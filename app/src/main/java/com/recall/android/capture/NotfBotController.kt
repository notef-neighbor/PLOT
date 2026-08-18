package com.recall.android.capture

import android.accessibilityservice.AccessibilityService
import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.Color
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import android.view.inputmethod.InputMethodManager
import com.recall.android.R
import com.recall.android.codex.CodexRuntimeService
import com.recall.android.data.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class NotfBotController(
    private val service: AccessibilityService,
    private val container: AppContainer,
    private val scope: CoroutineScope,
) {
    private val windowManager = service.getSystemService(WindowManager::class.java)
    private var overlay: View? = null
    private var commandInputView: EditText? = null
    private var answerView: TextView? = null
    private var conversationView: LinearLayout? = null
    private var conversationScroll: ScrollView? = null
    private var sendButton: Button? = null
    private var statusView: TextView? = null
    private var progressView: ProgressBar? = null
    private var actionsView: LinearLayout? = null
    private var autoSendJob: Job? = null
    private var answer: String? = null
    private var baseDraft: String = ""
    private var visibleContext: String = ""
    private var sourcePackage: String = ""
    private val conversation = mutableListOf<ChatMessage>()
    private var armed = false
    private var handling = false

    fun handleTextChange(event: AccessibilityEvent, visibleContext: () -> String): Boolean {
        val eventSource = event.source
        val source = eventSource?.takeIf(AccessibilityNodeInfo::isEditable)
            ?: service.rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            ?: return false
        if (!source.isEditable || event.isPassword || source.isPassword) return false
        val fullText = source.text?.toString()
            ?: event.text.lastOrNull()?.toString()
            ?: return false
        if (handling) return true
        val trigger = NotfBotTrigger.parseDraft(fullText)
        if (trigger == null) {
            // Rich editors often emit follow-up changes from auxiliary editable nodes
            // (suggestions, hidden composers, etc.). Once armed, keep the launcher
            // stable until the user explicitly closes or submits it.
            return false
        }

        this.visibleContext = visibleContext()
        sourcePackage = source.packageName?.toString() ?: event.packageName?.toString().orEmpty()
        Log.d(TAG, "Trigger detected in $sourcePackage")
        if (!armed) {
            replaceFocusedText(source, trigger.draft)
            showCommandComposer(trigger.command, trigger.draft)
        }
        return true
    }

    fun dispose() {
        autoSendJob?.cancel()
        service.mainExecutor.execute { dismissOverlay() }
    }

    private fun executeCommand() {
        val command = commandInputView?.text?.toString()?.trim().orEmpty()
        if (command.length < 2) {
            Toast.makeText(service, service.getString(R.string.assistant_enter_request), Toast.LENGTH_LONG).show()
            return
        }

        handling = true
        val previousConversation = conversation.toList()
        conversation += ChatMessage(ChatRole.User, command)
        commandInputView?.setText("")
        sendButton?.isEnabled = false
        renderConversation(loading = true)
        scope.launch {
            val result = runCatching {
                CodexRuntimeService.start(service)
                val related = container.historyRepository.search(command).take(20)
                container.codexRuntime.askHistory(
                    question = buildPrompt(
                        command = command,
                        draft = baseDraft,
                        visibleContext = visibleContext,
                        packageName = sourcePackage,
                        previousConversation = previousConversation,
                    ),
                    memories = related,
                )
            }.getOrElse { error ->
                service.getString(R.string.assistant_failed, error.message.orEmpty())
            }

            withContext(Dispatchers.Main) {
                answer = result
                conversation += ChatMessage(ChatRole.Assistant, result)
                sendButton?.isEnabled = true
                renderConversation(loading = false)
                if (container.settings.state.value.notfBotAutoSendEnabled) {
                    beginAutoSend(result)
                } else {
                    actionsView?.visibility = View.VISIBLE
                    handling = false
                    commandInputView?.requestFocus()
                }
            }
        }
    }

    private fun showCommandComposer(command: String, draft: String) {
        dismissOverlay()
        armed = true
        baseDraft = draft
        conversation.clear()
        val outer = FrameLayout(service).apply { setPadding(dp(10), dp(8), dp(10), dp(8)) }
        val panel = createPanel()
        outer.addView(panel, FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT)

        panel.addView(LinearLayout(service).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(10))
            addView(View(service).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.rgb(200, 255, 56))
                }
            }, LinearLayout.LayoutParams(dp(22), dp(22)).apply { rightMargin = dp(10) })
            addView(TextView(service).apply {
                text = service.getString(R.string.app_name)
                textSize = 23f
                setTextColor(Color.rgb(17, 17, 17))
                setTypeface(typeface, Typeface.BOLD)
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(Button(service).apply {
                text = service.getString(R.string.assistant_new)
                minWidth = 0
                setTextColor(Color.rgb(17, 17, 17))
                background = buttonBackground(Color.rgb(200, 255, 56))
                setOnClickListener { resetConversation() }
            })
            addView(Button(service).apply {
                text = "×"
                contentDescription = service.getString(R.string.assistant_close)
                minWidth = 0
                setTextColor(Color.WHITE)
                background = buttonBackground(Color.rgb(17, 17, 17))
                setOnClickListener { dismissOverlay() }
            })
        })

        conversationView = LinearLayout(service).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(4), 0, dp(4))
        }
        conversationScroll = ScrollView(service).apply {
            isFillViewport = true
            addView(
                conversationView,
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
            )
        }.also { scroll ->
            panel.addView(scroll, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(96),
            ))
        }

        statusView = TextView(service).apply {
            text = service.getString(R.string.assistant_context_status)
            textSize = 11f
            setTextColor(Color.rgb(90, 90, 90))
            setPadding(0, dp(6), 0, dp(6))
        }.also(panel::addView)

        actionsView = LinearLayout(service).apply {
            orientation = LinearLayout.HORIZONTAL
            visibility = View.GONE
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(2), 0, dp(4))
            addView(Button(service).apply {
                text = service.getString(R.string.assistant_copy)
                minWidth = 0
                setTextColor(Color.rgb(17, 17, 17))
                background = buttonBackground(Color.rgb(200, 255, 56))
                setOnClickListener { copyAnswer() }
            })
            addView(Button(service).apply {
                text = service.getString(R.string.assistant_insert)
                minWidth = 0
                setTextColor(Color.WHITE)
                background = buttonBackground(Color.rgb(36, 71, 255))
                setOnClickListener { insertAnswer() }
            })
        }.also(panel::addView)

        panel.addView(LinearLayout(service).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.BOTTOM
            commandInputView = EditText(service).apply {
                hint = service.getString(R.string.assistant_reply_hint)
                textSize = 16f
                minLines = 1
                maxLines = 2
                setText(command)
                setSelection(text.length)
                setPadding(dp(12), dp(8), dp(12), dp(8))
                background = GradientDrawable().apply {
                    setColor(Color.rgb(246, 243, 235))
                    cornerRadius = dp(6).toFloat()
                    setStroke(dp(1), Color.rgb(17, 17, 17))
                }
            }.also {
                addView(it, LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f,
                ).apply { rightMargin = dp(8) })
            }
            sendButton = Button(service).apply {
                text = service.getString(R.string.assistant_send)
                minWidth = 0
                setTextColor(Color.WHITE)
                background = GradientDrawable().apply {
                    setColor(Color.rgb(36, 71, 255))
                    cornerRadius = dp(3).toFloat()
                }
                setOnClickListener { executeCommand() }
            }.also {
                addView(it, LinearLayout.LayoutParams(dp(76), LinearLayout.LayoutParams.WRAP_CONTENT))
            }
        })
        renderConversation(loading = false)
        installKeyboardAwareLayout(outer)
        attachOverlay(outer, focusable = true)
        commandInputView?.post {
            commandInputView?.requestFocus()
            service.getSystemService(InputMethodManager::class.java)
                .showSoftInput(commandInputView, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun renderConversation(loading: Boolean) {
        val container = conversationView ?: return
        container.removeAllViews()
        answerView = null
        if (conversation.isEmpty()) {
            container.addView(TextView(service).apply {
                text = service.getString(R.string.assistant_empty)
                textSize = 15f
                setTextColor(Color.rgb(70, 70, 70))
                setPadding(dp(12), dp(18), dp(12), dp(18))
            })
        } else {
            conversation.forEach { message -> addMessageBubble(container, message) }
        }
        if (loading) {
            container.addView(LinearLayout(service).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(12), dp(10), dp(12), dp(10))
                background = bubbleBackground(Color.rgb(255, 252, 244), Color.rgb(17, 17, 17))
                addView(ProgressBar(service), LinearLayout.LayoutParams(dp(22), dp(22)))
                addView(TextView(service).apply {
                    text = service.getString(R.string.assistant_loading)
                    textSize = 13f
                    setTextColor(Color.rgb(50, 50, 50))
                    setPadding(dp(10), 0, 0, 0)
                })
            }, bubbleLayout(Gravity.START))
            statusView?.text = service.getString(R.string.assistant_thinking)
        } else {
            statusView?.text = service.getString(R.string.assistant_context_status)
        }
        conversationScroll?.post { conversationScroll?.fullScroll(View.FOCUS_DOWN) }
    }

    private fun addMessageBubble(parent: LinearLayout, message: ChatMessage) {
        val isUser = message.role == ChatRole.User
        val bubble = LinearLayout(service).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(9), dp(12), dp(10))
            background = bubbleBackground(
                fill = if (isUser) Color.rgb(228, 233, 255) else Color.rgb(255, 252, 244),
                stroke = if (isUser) Color.rgb(36, 71, 255) else Color.rgb(17, 17, 17),
            )
            addView(TextView(service).apply {
                text = if (isUser) service.getString(R.string.assistant_you) else service.getString(R.string.app_name)
                textSize = 10f
                letterSpacing = 0.12f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(if (isUser) Color.rgb(36, 71, 255) else Color.rgb(17, 17, 17))
            })
            addView(TextView(service).apply {
                text = message.text
                textSize = 15f
                setTextColor(Color.rgb(17, 17, 17))
                setTextIsSelectable(!isUser)
                setPadding(0, dp(4), 0, 0)
                if (!isUser) answerView = this
            })
        }
        parent.addView(bubble, bubbleLayout(if (isUser) Gravity.END else Gravity.START))
    }

    private fun bubbleLayout(alignment: Int): LinearLayout.LayoutParams = LinearLayout.LayoutParams(
        (service.resources.displayMetrics.widthPixels * 0.78f).toInt(),
        LinearLayout.LayoutParams.WRAP_CONTENT,
    ).apply {
        gravity = alignment
        topMargin = dp(5)
        bottomMargin = dp(5)
    }

    private fun bubbleBackground(fill: Int, stroke: Int): GradientDrawable = GradientDrawable().apply {
        setColor(fill)
        cornerRadius = dp(8).toFloat()
        setStroke(dp(1), stroke)
    }

    private fun buttonBackground(fill: Int): GradientDrawable = GradientDrawable().apply {
        setColor(fill)
        cornerRadius = dp(3).toFloat()
    }

    private fun resetConversation() {
        conversation.clear()
        answer = null
        actionsView?.visibility = View.GONE
        commandInputView?.setText("")
        sendButton?.isEnabled = true
        handling = false
        renderConversation(loading = false)
        commandInputView?.requestFocus()
        service.getSystemService(InputMethodManager::class.java)
            .showSoftInput(commandInputView, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun createPanel(): LinearLayout = LinearLayout(service).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(14), dp(12), dp(14), dp(10))
        elevation = dp(16).toFloat()
        background = GradientDrawable().apply {
            setColor(Color.rgb(255, 252, 244))
            cornerRadius = dp(10).toFloat()
            setStroke(dp(1), Color.rgb(17, 17, 17))
        }
    }

    private fun installKeyboardAwareLayout(root: View) {
        root.viewTreeObserver.addOnGlobalLayoutListener {
            val scroll = conversationScroll ?: return@addOnGlobalLayoutListener
            if (root.height == 0 || scroll.height == 0) return@addOnGlobalLayoutListener

            val visibleFrame = Rect()
            root.getWindowVisibleDisplayFrame(visibleFrame)
            val imeBottom = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                root.rootWindowInsets?.getInsets(WindowInsets.Type.ime())?.bottom ?: 0
            } else {
                0
            }
            val visibleBottom = if (imeBottom > 0) {
                minOf(
                    visibleFrame.bottom,
                    service.resources.displayMetrics.heightPixels - imeBottom,
                )
            } else {
                visibleFrame.bottom
            }
            val rootLocation = IntArray(2)
            root.getLocationOnScreen(rootLocation)
            val fixedContentHeight = root.height - scroll.height
            val availableForConversation = visibleBottom - rootLocation[1] -
                fixedContentHeight - dp(8)
            val targetHeight = availableForConversation.coerceIn(dp(72), dp(220))
            val params = scroll.layoutParams
            if (params.height != targetHeight) {
                params.height = targetHeight
                scroll.layoutParams = params
                scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
            }
        }
    }

    private fun attachOverlay(outer: View, focusable: Boolean = false) {
        overlay = outer
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            (if (focusable) 0 else WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE) or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            android.graphics.PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP
            y = dp(8)
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        }
        runCatching { windowManager.addView(outer, params) }
            .onFailure {
                overlay = null
                Log.e(TAG, "Unable to attach accessibility overlay", it)
                Toast.makeText(service, service.getString(R.string.assistant_open_failed), Toast.LENGTH_SHORT).show()
            }
    }

    private fun insertAnswer() {
        val value = answer ?: return
        dismissOverlay()
        Handler(Looper.getMainLooper()).postDelayed({
            val focused = findEditableNode()
            val inserted = focused != null && !focused.isPassword && replaceFocusedText(focused, value)
            if (inserted) {
                Toast.makeText(service, service.getString(R.string.assistant_inserted), Toast.LENGTH_SHORT).show()
            } else {
                copyValue(value)
                Toast.makeText(service, service.getString(R.string.assistant_insert_failed), Toast.LENGTH_LONG).show()
            }
        }, RETURN_TO_APP_DELAY_MS)
    }

    private fun beginAutoSend(rawAnswer: String) {
        val botReply = "[${service.getString(R.string.app_name)}] ${rawAnswer.trim()}"
        answer = botReply
        answerView?.text = botReply
        val focused = findEditableNode()
        if (focused == null || focused.isPassword || !replaceFocusedText(focused, botReply)) {
            Toast.makeText(service, service.getString(R.string.assistant_auto_send_insert_failed), Toast.LENGTH_LONG).show()
            showManualActions()
            handling = false
            return
        }

        val row = actionsView ?: return
        row.removeAllViews()
        val countdown = TextView(service).apply {
            textSize = 14f
            setTextColor(Color.rgb(20, 76, 67))
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, dp(8), 0)
        }
        row.addView(countdown, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(Button(service).apply {
            text = service.getString(R.string.cancel)
            setOnClickListener { cancelAutoSend() }
        })
        row.addView(Button(service).apply {
            text = service.getString(R.string.assistant_send_now)
            setOnClickListener {
                autoSendJob?.cancel()
                autoSendJob = null
                completeAutoSend()
            }
        })
        row.visibility = View.VISIBLE

        autoSendJob?.cancel()
        autoSendJob = scope.launch {
            for (seconds in AUTO_SEND_SECONDS downTo 1) {
                withContext(Dispatchers.Main) { countdown.text = service.getString(R.string.assistant_send_countdown, seconds) }
                delay(1_000)
            }
            withContext(Dispatchers.Main) {
                autoSendJob = null
                completeAutoSend()
            }
        }
    }

    private fun cancelAutoSend() {
        autoSendJob?.cancel()
        autoSendJob = null
        handling = false
        showManualActions()
        Toast.makeText(service, service.getString(R.string.assistant_auto_send_cancelled), Toast.LENGTH_LONG).show()
    }

    private fun completeAutoSend() {
        val root = service.rootInActiveWindow
        val sameApplication = root?.packageName?.toString() == sourcePackage
        val sendButton = if (sameApplication) findSendButton(root) else null
        if (sendButton?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true) {
            handling = false
            Toast.makeText(service, service.getString(R.string.assistant_sent), Toast.LENGTH_SHORT).show()
            dismissOverlay()
        } else {
            handling = false
            showManualActions()
            Toast.makeText(service, service.getString(R.string.assistant_send_control_failed), Toast.LENGTH_LONG).show()
        }
    }

    private fun showManualActions() {
        actionsView?.apply {
            visibility = View.VISIBLE
        }
        sendButton?.isEnabled = true
    }

    private fun findEditableNode(): AccessibilityNodeInfo? {
        val root = service.rootInActiveWindow ?: return null
        root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)?.let { focused ->
            if (focused.isEditable && focused.packageName?.toString() == sourcePackage) return focused
        }
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var visited = 0
        while (queue.isNotEmpty() && visited++ < MAX_SEARCH_NODES) {
            val node = queue.removeFirst()
            if (node.isEditable && !node.isPassword && node.packageName?.toString() == sourcePackage) return node
            for (index in 0 until node.childCount) node.getChild(index)?.let(queue::addLast)
        }
        return null
    }

    private fun findSendButton(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var visited = 0
        while (queue.isNotEmpty() && visited++ < MAX_SEARCH_NODES) {
            val node = queue.removeFirst()
            val identity = listOfNotNull(
                node.text?.toString(),
                node.contentDescription?.toString(),
                node.viewIdResourceName,
            ).joinToString(" ").lowercase()
            if (
                node.isClickable && node.isEnabled &&
                node.packageName?.toString() == sourcePackage &&
                SEND_MARKERS.any(identity::contains)
            ) return node
            for (index in 0 until node.childCount) node.getChild(index)?.let(queue::addLast)
        }
        return null
    }

    private fun replaceFocusedText(node: AccessibilityNodeInfo, value: String): Boolean {
        val arguments = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value)
        }
        return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
    }

    private fun copyAnswer() {
        val value = answer ?: return
        copyValue(value)
        Toast.makeText(service, service.getString(R.string.assistant_answer_copied), Toast.LENGTH_SHORT).show()
    }

    private fun copyValue(value: String) {
        service.getSystemService(ClipboardManager::class.java)
            .setPrimaryClip(ClipData.newPlainText(service.getString(R.string.assistant_clipboard_label), value))
    }

    private fun dismissOverlay() {
        autoSendJob?.cancel()
        autoSendJob = null
        overlay?.let { view ->
            service.getSystemService(InputMethodManager::class.java)
                .hideSoftInputFromWindow(view.windowToken, 0)
            runCatching { windowManager.removeView(view) }
        }
        overlay = null
        commandInputView = null
        answerView = null
        conversationView = null
        conversationScroll = null
        sendButton = null
        statusView = null
        progressView = null
        actionsView = null
        answer = null
        conversation.clear()
        baseDraft = ""
        armed = false
    }

    private fun buildPrompt(
        command: String,
        draft: String,
        visibleContext: String,
        packageName: String,
        previousConversation: List<ChatMessage>,
    ): String = buildString {
        appendLine(service.getString(R.string.assistant_prompt_rules))
        appendLine()
        if (previousConversation.isNotEmpty()) {
            appendLine(service.getString(R.string.assistant_prompt_history))
            previousConversation.takeLast(MAX_CONVERSATION_MESSAGES).forEach { message ->
                appendLine("${if (message.role == ChatRole.User) service.getString(R.string.assistant_you) else service.getString(R.string.app_name)}: ${message.text.take(MAX_MESSAGE_CHARS)}")
            }
            appendLine()
        }
        appendLine(service.getString(R.string.assistant_prompt_latest, command))
        if (draft.isNotBlank()) appendLine(service.getString(R.string.assistant_prompt_draft, draft))
        appendLine(service.getString(R.string.assistant_prompt_app, packageName))
        appendLine(service.getString(R.string.assistant_prompt_screen))
        appendLine(visibleContext.take(MAX_CONTEXT_CHARS))
    }

    private fun dp(value: Int): Int = (value * service.resources.displayMetrics.density).toInt()

    private companion object {
        const val TAG = "NotfBot"
        const val MAX_CONTEXT_CHARS = 6_000
        const val MAX_SEARCH_NODES = 300
        const val MAX_CONVERSATION_MESSAGES = 12
        const val MAX_MESSAGE_CHARS = 1_500
        const val RETURN_TO_APP_DELAY_MS = 250L
        const val AUTO_SEND_SECONDS = 3
        val SEND_MARKERS = listOf("送信", "送る", "send", "compose_send", "send_button", "button_send")
    }
}

private enum class ChatRole { User, Assistant }

private data class ChatMessage(
    val role: ChatRole,
    val text: String,
)

internal data class NotfBotTriggerResult(
    val command: String,
    val draft: String,
)

internal object NotfBotTrigger {
    private val draftPattern = Regex("(?:^|\\s)[@＠]\\s*(?:chapichapi|ちゃぴちゃぴ|notfbot)(?:\\s+(.*))?$", RegexOption.IGNORE_CASE)

    fun parseDraft(text: String): NotfBotTriggerResult? {
        val match = draftPattern.find(text) ?: return null
        return NotfBotTriggerResult(
            command = match.groupValues.getOrElse(1) { "" }.trim(),
            draft = text.substring(0, match.range.first).trimEnd(),
        )
    }

    fun parseReady(text: String): NotfBotTriggerResult? = parseDraft(text)?.takeIf { it.command.length >= 2 }
}
