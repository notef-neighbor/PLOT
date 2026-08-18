package com.recall.android.capture

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.recall.android.RecallApplication
import com.recall.android.data.CapturedInteraction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class RecallNotificationListenerService : NotificationListenerService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        val container = (application as RecallApplication).container
        val settings = container.settings.state.value
        val packageName = sbn.packageName
        if (
            !settings.disclosureAccepted || settings.collectionPaused ||
            packageName !in settings.allowedPackages ||
            container.privacyGuard.isProtectedPackage(packageName)
        ) return

        val text = notificationText(sbn.notification).takeIf(String::isNotBlank) ?: return
        val label = runCatching {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(packageName, 0)).toString()
        }.getOrDefault(packageName.substringAfterLast('.'))
        scope.launch {
            container.historyRepository.record(
                interaction = CapturedInteraction(
                    occurredAt = sbn.postTime,
                    kind = "notification",
                    packageName = packageName,
                    applicationLabel = label,
                    className = Notification::class.java.name,
                    text = text,
                    viewId = null,
                    contentRedacted = false,
                ),
                stableKey = "notification:${sbn.key}:${sbn.postTime}:${text.hashCode()}",
            )
        }
    }

    override fun onDestroy() {
        scope.coroutineContext[Job]?.cancel()
        super.onDestroy()
    }

    private fun notificationText(notification: Notification): String {
        val extras = notification.extras
        val direct = listOfNotNull(
            extras.getCharSequence(Notification.EXTRA_TITLE),
            extras.getCharSequence(Notification.EXTRA_TEXT),
            extras.getCharSequence(Notification.EXTRA_BIG_TEXT),
            extras.getCharSequence(Notification.EXTRA_SUB_TEXT),
            extras.getCharSequence(Notification.EXTRA_SUMMARY_TEXT),
        ).map(CharSequence::toString)
        val lines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
            ?.map(CharSequence::toString)
            .orEmpty()
        return (direct + lines)
            .map { it.replace(Regex("\\s+"), " ").trim() }
            .filter(String::isNotBlank)
            .distinct()
            .joinToString(" | ")
            .take(MAX_NOTIFICATION_TEXT)
    }

    private companion object {
        const val MAX_NOTIFICATION_TEXT = 8_000
    }
}
