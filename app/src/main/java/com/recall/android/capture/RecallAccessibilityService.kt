package com.recall.android.capture

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.recall.android.RecallApplication
import com.recall.android.data.CapturedInteraction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

class RecallAccessibilityService : AccessibilityService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var notfBotController: NotfBotController? = null
    private val contentCaptureThrottle = ContentCaptureThrottle(CONTENT_CAPTURE_INTERVAL_MS)
    private val captureQueue = Channel<AccessibilityEvent>(
        capacity = CAPTURE_QUEUE_CAPACITY,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
        onUndeliveredElement = ::recycleEvent,
    )

    init {
        serviceScope.launch {
            for (event in captureQueue) {
                try {
                    captureEventInBackground(event)
                } catch (error: Throwable) {
                    android.util.Log.w("RecallCapture", "Skipped malformed accessibility event", error)
                } finally {
                    recycleEvent(event)
                }
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        runCatching { enqueueCapture(event) }
            .onFailure { error ->
                android.util.Log.w("RecallCapture", "Skipped malformed accessibility event", error)
            }
    }

    private fun enqueueCapture(event: AccessibilityEvent) {
        val container = (application as RecallApplication).container
        val settings = container.settings.state.value
        if (!settings.disclosureAccepted || settings.collectionPaused) return

        val packageName = event.packageName?.toString() ?: return
        if (packageName !in settings.allowedPackages) return
        val label = runCatching {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(packageName, 0)).toString()
        }.getOrDefault(packageName.substringAfterLast('.'))

        val isNotfBotInputEvent =
            event.eventType == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED ||
                event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        if (
            settings.notfBotEnabled &&
            isNotfBotInputEvent &&
            !event.isPassword &&
            packageName in settings.allowedPackages &&
            !container.privacyGuard.isProtectedPackage(packageName)
        ) {
            val controller = notfBotController ?: NotfBotController(this, container, serviceScope)
                .also { notfBotController = it }
            if (controller.handleTextChange(event) {
                    collectVisibleContent(contentRoot(event.source, rootInActiveWindow, packageName))
                        .distinct()
                        .joinToString(" | ")
                }
            ) return
        }

        @Suppress("DEPRECATION")
        val copy = AccessibilityEvent.obtain(event)
        captureQueue.trySend(copy)
    }

    private suspend fun captureEventInBackground(event: AccessibilityEvent) {
        val container = (application as RecallApplication).container
        val settings = container.settings.state.value
        if (!settings.disclosureAccepted || settings.collectionPaused) return

        val packageName = event.packageName?.toString() ?: return
        if (packageName !in settings.allowedPackages) return
        val label = runCatching {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(packageName, 0)).toString()
        }.getOrDefault(packageName.substringAfterLast('.'))

        val source = event.source
        val eventText = (event.text.mapNotNull { value -> value?.toString() } + listOfNotNull(
            event.beforeText?.toString(),
            event.contentDescription?.toString(),
        )).filter(String::isNotBlank)
        val treeText = collectVisibleContent(contentRoot(source, rootInActiveWindow, packageName))
        val rawText = (eventText + treeText).distinct().joinToString(" | ").takeIf(String::isNotBlank)

        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            val now = System.currentTimeMillis()
            val fingerprint = rawText?.hashCode() ?: return
            if (!contentCaptureThrottle.shouldCapture(packageName, fingerprint, now)) return
        }

        val raw = CapturedInteraction(
            // AccessibilityEvent.eventTime is uptime, not Unix epoch time.
            occurredAt = System.currentTimeMillis(),
            kind = eventTypeName(event.eventType),
            packageName = packageName,
            applicationLabel = label,
            className = event.className?.toString(),
            text = rawText,
            viewId = source?.viewIdResourceName,
            contentRedacted = false,
        )
        val decision = container.privacyGuard.evaluate(
            interaction = raw,
            allowedPackages = settings.allowedPackages,
            passwordField = event.isPassword || source?.isPassword == true,
        )
        if (!decision.allowed) return
        val stableKey = if (event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            "$packageName:content:${rawText.hashCode()}:${System.currentTimeMillis() / CONTENT_DEDUP_WINDOW_MS}"
        } else {
            null
        }

        container.historyRepository.record(
            raw.copy(
                text = decision.sanitizedText,
                contentRedacted = decision.contentRedacted,
            ),
            stableKey = stableKey,
        )
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        notfBotController?.dispose()
        captureQueue.close()
        serviceScope.coroutineContext[Job]?.cancel()
        super.onDestroy()
    }

    @Suppress("DEPRECATION")
    private fun recycleEvent(event: AccessibilityEvent) {
        event.recycle()
    }

    private fun contentRoot(
        source: AccessibilityNodeInfo?,
        activeRoot: AccessibilityNodeInfo?,
        eventPackage: String,
    ): AccessibilityNodeInfo? = when {
        AccessibilityCapturePolicy.belongsToEventPackage(eventPackage, source?.packageName) -> source
        AccessibilityCapturePolicy.belongsToEventPackage(eventPackage, activeRoot?.packageName) -> activeRoot
        else -> null
    }

    private fun eventTypeName(type: Int): String = when (type) {
        AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> "window_changed"
        AccessibilityEvent.TYPE_WINDOWS_CHANGED -> "windows_changed"
        AccessibilityEvent.TYPE_VIEW_CLICKED -> "view_clicked"
        AccessibilityEvent.TYPE_VIEW_FOCUSED -> "view_focused"
        AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> "text_changed"
        AccessibilityEvent.TYPE_VIEW_SELECTED -> "view_selected"
        AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> "content_changed"
        else -> "other"
    }

    private fun collectVisibleContent(root: AccessibilityNodeInfo?): List<String> {
        root ?: return emptyList()
        val result = ArrayList<String>()
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var visited = 0
        var characters = 0
        while (queue.isNotEmpty() && visited < MAX_NODES && characters < MAX_CHARACTERS) {
            val node = queue.removeFirst()
            visited += 1
            if (!node.isPassword) {
                val values = listOfNotNull(
                    node.text?.toString(),
                    node.contentDescription?.toString(),
                    node.hintText?.toString(),
                ).map { it.replace(Regex("\\s+"), " ").trim() }.filter(String::isNotBlank)
                values.forEach { value ->
                    if (characters + value.length <= MAX_CHARACTERS) {
                        result += value
                        characters += value.length
                    }
                }
            }
            for (index in 0 until node.childCount) {
                node.getChild(index)?.let(queue::addLast)
            }
        }
        return result
    }

    companion object {
        private const val MAX_NODES = 250
        private const val MAX_CHARACTERS = 8_000
        private const val CONTENT_CAPTURE_INTERVAL_MS = 2_000L
        private const val CONTENT_DEDUP_WINDOW_MS = 60_000L
        private const val CAPTURE_QUEUE_CAPACITY = 128
    }
}
