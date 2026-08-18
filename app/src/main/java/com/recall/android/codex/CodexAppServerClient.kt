package com.recall.android.codex

import android.content.Context
import com.recall.android.BuildConfig
import com.recall.android.R
import com.recall.android.data.HistoryMemory
import com.recall.android.data.HistoryEvidenceFormatter
import java.io.Closeable
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

class CodexAppServerClient(
    private val context: Context,
    private val endpoint: String = "ws://127.0.0.1:4500",
) : Closeable {
    private val json = Json { ignoreUnknownKeys = true }
    private val http = OkHttpClient.Builder().retryOnConnectionFailure(true).build()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val nextId = AtomicLong(1)
    private val pending = ConcurrentHashMap<Long, CompletableDeferred<JsonObject>>()
    private val notificationFlow = MutableSharedFlow<JsonObject>(extraBufferCapacity = 256)
    private val _state = MutableStateFlow(
        CodexRuntimeState(hasStoredLogin = File(context.filesDir, "codex-home/auth.json").isFile),
    )
    val state: StateFlow<CodexRuntimeState> = _state.asStateFlow()
    val notifications = notificationFlow.asSharedFlow()

    @Volatile private var socket: WebSocket? = null

    suspend fun connect() {
        disconnect()
        _state.value = _state.value.copy(status = CodexRuntimeStatus.Connecting)
        val opened = CompletableDeferred<Unit>()
        val request = Request.Builder().url(endpoint).build()
        socket = http.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                opened.complete(Unit)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleMessage(text)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (!opened.isCompleted) opened.completeExceptionally(t)
                failPending(t)
                _state.value = _state.value.copy(status = CodexRuntimeStatus.Error, message = t.message)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                failPending(IllegalStateException("Codex App Server disconnected: $reason"))
                if (_state.value.status != CodexRuntimeStatus.Stopped) {
                    _state.value = _state.value.copy(status = CodexRuntimeStatus.Stopped)
                }
            }
        })
        withTimeout(15_000) { opened.await() }
        request(
            method = "initialize",
            params = buildJsonObject {
                put("clientInfo", buildJsonObject {
                    put("name", "plot_android")
                    put("title", "PLOT for Android")
                    put("version", BuildConfig.VERSION_NAME)
                })
            },
        )
        notify("initialized", buildJsonObject {})
        scope.launch { observeAccountUpdates() }
        refreshAccount()
    }

    suspend fun refreshAccount() {
        val response = request(
            "account/read",
            buildJsonObject { put("refreshToken", false) },
        )
        applyAccount(response["result"]?.jsonObject)
        if (_state.value.isReady) refreshPreferredModel()
    }

    private suspend fun refreshPreferredModel() {
        val response = runCatching {
            request(
                "model/list",
                buildJsonObject {
                    put("limit", 50)
                    put("includeHidden", false)
                },
            )
        }.getOrNull() ?: return
        val models = response["result"]?.jsonObject?.get("data") as? JsonArray ?: return
        val entries = models.mapNotNull { it as? JsonObject }
        val selected = entries.firstOrNull { it.string("model") == PREFERRED_MODEL }
            ?: entries.firstOrNull { (it["isDefault"] as? JsonPrimitive)?.contentOrNull == "true" }
            ?: entries.firstOrNull()
            ?: return
        val model = selected.string("model") ?: selected.string("id") ?: return
        val supportedEfforts = (selected["supportedReasoningEfforts"] as? JsonArray)
            .orEmpty()
            .mapNotNull { (it as? JsonObject)?.string("reasoningEffort") }
        val effort = when {
            "low" in supportedEfforts -> "low"
            else -> selected.string("defaultReasoningEffort")
        }
        _state.value = _state.value.copy(
            model = model,
            modelDisplayName = selected.string("displayName") ?: model,
            reasoningEffort = effort,
            message = context.getString(R.string.runtime_fast_model),
        )
    }

    suspend fun logout() {
        request("account/logout")
        _state.value = CodexRuntimeState(
            status = CodexRuntimeStatus.LoginRequired,
            message = context.getString(R.string.runtime_disconnected),
            hasStoredLogin = false,
        )
    }

    suspend fun beginDeviceLogin(): CodexDeviceLogin {
        val response = request(
            "account/login/start",
            buildJsonObject { put("type", "chatgptDeviceCode") },
        )
        val result = response["result"]?.jsonObject ?: error("Codex returned no login details")
        val login = CodexDeviceLogin(
            verificationUrl = result.string("verificationUrl") ?: error("Codex returned no verification URL"),
            userCode = result.string("userCode") ?: error("Codex returned no user code"),
        )
        _state.value = _state.value.copy(
            status = CodexRuntimeStatus.LoginRequired,
            verificationUrl = login.verificationUrl,
            userCode = login.userCode,
            message = context.getString(R.string.runtime_check_code),
        )
        return login
    }

    suspend fun beginBrowserLogin(): CodexBrowserLogin {
        val response = request(
            "account/login/start",
            buildJsonObject {
                put("type", "chatgpt")
                put("useHostedLoginSuccessPage", true)
                put("appBrand", "chatgpt")
            },
        )
        val result = response["result"]?.jsonObject ?: error("Codex returned no login details")
        val login = CodexBrowserLogin(
            authUrl = result.string("authUrl") ?: error("Codex returned no authentication URL"),
        )
        _state.value = _state.value.copy(
            status = CodexRuntimeStatus.LoginRequired,
            verificationUrl = login.authUrl,
            userCode = null,
            message = context.getString(R.string.runtime_finish_auth),
        )
        return login
    }

    suspend fun askHistory(
        question: String,
        memories: List<HistoryMemory>,
        workspace: String,
        reasoningEffort: String? = null,
    ): String {
        check(_state.value.isReady) { context.getString(R.string.runtime_login_first) }
        val threadResponse = request(
            "thread/start",
            buildJsonObject {
                put("cwd", workspace)
                put("approvalPolicy", "never")
                put("sandbox", "read-only")
                put("ephemeral", true)
                put("baseInstructions", context.getString(R.string.history_analyst_instructions))
            },
        )
        val threadId = threadResponse["result"]?.jsonObject
            ?.get("thread")?.jsonObject
            ?.string("id")
            ?: error("Codex did not create a thread")

        return coroutineScope {
            // Subscribe before turn/start: fast responses can emit their first
            // deltas before the JSON-RPC response to turn/start is delivered.
            val events = Channel<JsonObject>(Channel.UNLIMITED)
            val subscription = launch(start = CoroutineStart.UNDISPATCHED) {
                notifications.collect(events::send)
            }
            try {
                val prompt = buildHistoryPrompt(question, memories)
                val turnResponse = request(
                    "turn/start",
                    buildJsonObject {
                        put("threadId", threadId)
                        put("input", buildJsonArray {
                            add(buildJsonObject {
                                put("type", "text")
                                put("text", prompt)
                            })
                        })
                        _state.value.model?.let { put("model", it) }
                        (reasoningEffort ?: _state.value.reasoningEffort)?.let { put("effort", it) }
                    },
                )
                val turnId = turnResponse["result"]?.jsonObject
                    ?.get("turn")?.jsonObject
                    ?.string("id")
                    ?: error("Codex did not start a turn")
                val accumulator = CodexTurnAccumulator(turnId)
                withTimeout(180_000) {
                    while (!accumulator.accept(events.receive())) Unit
                }
                accumulator.answer().ifBlank { error(context.getString(R.string.runtime_empty_answer)) }
            } finally {
                subscription.cancel()
                events.close()
            }
        }
    }

    fun disconnect() {
        socket?.close(1000, "Recall disconnecting")
        socket = null
        failPending(IllegalStateException("Codex client disconnected"))
    }

    override fun close() {
        disconnect()
        scope.cancel()
        http.dispatcher.executorService.shutdown()
    }

    private suspend fun request(method: String, params: JsonObject? = null): JsonObject {
        val id = nextId.getAndIncrement()
        val deferred = CompletableDeferred<JsonObject>()
        pending[id] = deferred
        val sent = socket?.send(buildJsonObject {
            put("method", method)
            put("id", id)
            params?.let { put("params", it) }
        }.toString()) == true
        if (!sent) {
            pending.remove(id)
            error("Codex App Server is not connected")
        }
        val response = withTimeout(30_000) { deferred.await() }
        response["error"]?.takeUnless { it is JsonNull }?.let { error("Codex error: $it") }
        return response
    }

    private fun notify(method: String, params: JsonObject) {
        check(socket?.send(buildJsonObject {
            put("method", method)
            put("params", params)
        }.toString()) == true) { "Codex App Server is not connected" }
    }

    private fun handleMessage(text: String) {
        val message = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return
        val id = message["id"]?.jsonPrimitive?.longOrNull
        if (id != null) {
            pending.remove(id)?.complete(message)
        } else {
            notificationFlow.tryEmit(message)
        }
    }

    private suspend fun observeAccountUpdates() {
        notifications
            .mapNotNull { event -> event.method()?.takeIf { it == "account/login/completed" } }
            .collect { runCatching { refreshAccount() } }
    }

    private fun applyAccount(result: JsonObject?) {
        val account = result?.get("account") as? JsonObject
        if (account?.string("type") == "chatgpt") {
            _state.value = CodexRuntimeState(
                status = CodexRuntimeStatus.Authenticated,
                accountEmail = account.string("email"),
                planType = account.string("planType"),
                message = context.getString(R.string.runtime_connected),
                model = _state.value.model,
                modelDisplayName = _state.value.modelDisplayName,
                reasoningEffort = _state.value.reasoningEffort,
                hasStoredLogin = true,
            )
        } else {
            _state.value = CodexRuntimeState(
                status = CodexRuntimeStatus.LoginRequired,
                message = context.getString(R.string.runtime_login_required),
                hasStoredLogin = false,
            )
        }
    }

    private fun failPending(error: Throwable) {
        pending.values.forEach { it.completeExceptionally(error) }
        pending.clear()
    }

    private fun buildHistoryPrompt(question: String, memories: List<HistoryMemory>): String = buildString {
        appendLine(context.getString(R.string.history_question, question))
        appendLine()
        appendLine(context.getString(R.string.history_evidence_rules))
        appendLine("<history>")
        memories.take(12).forEachIndexed { index, memory ->
            appendLine(HistoryEvidenceFormatter.promptLine(index + 1, memory))
        }
        appendLine("</history>")
    }

    private companion object {
        const val PREFERRED_MODEL = "gpt-5.6-luna"
    }
}

private fun JsonObject.method(): String? = string("method")

private fun JsonObject.string(key: String): String? =
    (this[key] as? JsonPrimitive)?.contentOrNull
