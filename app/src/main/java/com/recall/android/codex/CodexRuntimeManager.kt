package com.recall.android.codex

import android.content.Context
import android.content.Intent
import com.recall.android.R
import com.recall.android.data.HistoryMemory
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class CodexRuntimeManager(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val client = CodexAppServerClient(context)
    private val _state = MutableStateFlow(CodexRuntimeState())
    val state: StateFlow<CodexRuntimeState> = _state.asStateFlow()

    @Volatile private var process: Process? = null
    @Volatile private var networkProxy: LocalConnectProxy? = null
    @Volatile private var idleStopJob: Job? = null
    private val startMutex = Mutex()

    init {
        scope.launch { client.state.collectLatest { _state.value = it } }
        if (!binaryFile().exists()) {
            _state.value = CodexRuntimeState(
                CodexRuntimeStatus.Unavailable,
                message = context.getString(R.string.runtime_not_bundled),
            )
        }
    }

    suspend fun start() = withContext(Dispatchers.IO) {
        idleStopJob?.cancel()
        idleStopJob = null
        startMutex.withLock {
            if (client.state.value.status == CodexRuntimeStatus.Authenticated ||
                client.state.value.status == CodexRuntimeStatus.LoginRequired
            ) return@withLock
            val binary = binaryFile()
            check(binary.exists()) { context.getString(R.string.runtime_binary_missing) }
            _state.value = _state.value.copy(
                status = CodexRuntimeStatus.Starting,
                message = context.getString(R.string.runtime_starting),
            )
            if (process?.isAlive != true) {
                val proxy = networkProxy ?: LocalConnectProxy().also { networkProxy = it }
                val codexHome = File(context.filesDir, "codex-home").apply { mkdirs() }
                val workspace = File(context.filesDir, "codex-workspace").apply { mkdirs() }
                val temp = File(context.cacheDir, "codex-temp").apply { mkdirs() }
                val caBundle = createAndroidCaBundle(codexHome)
                process = ProcessBuilder(
                    "/system/bin/nice",
                    "-n",
                    "10",
                    binary.absolutePath,
                    "--listen",
                    "ws://127.0.0.1:4500",
                ).apply {
                    directory(workspace)
                    redirectErrorStream(true)
                    environment()["CODEX_HOME"] = codexHome.absolutePath
                    environment()["TMPDIR"] = temp.absolutePath
                    environment()["SSL_CERT_FILE"] = caBundle.absolutePath
                    environment()["HTTPS_PROXY"] = proxy.url
                    environment()["https_proxy"] = proxy.url
                    environment()["NO_PROXY"] = "127.0.0.1,localhost"
                    environment()["no_proxy"] = "127.0.0.1,localhost"
                    environment()["RUST_BACKTRACE"] = "1"
                }.start()
                scope.launch {
                    process?.inputStream?.bufferedReader()?.useLines { lines ->
                        lines.forEach { android.util.Log.i("RecallCodex", it.take(4_000)) }
                    }
                }
            }
            var lastError: Throwable? = null
            repeat(40) {
                if (process?.isAlive != true) error("Codex App Server exited during startup")
                runCatching { client.connect() }
                    .onSuccess { return@withLock }
                    .onFailure { lastError = it }
                delay(250)
            }
            throw lastError ?: IllegalStateException("Codex App Server did not start")
        }
    }

    suspend fun beginDeviceLogin(): CodexDeviceLogin {
        start()
        return client.beginDeviceLogin()
    }

    suspend fun beginBrowserLogin(): CodexBrowserLogin {
        start()
        return client.beginBrowserLogin()
    }

    suspend fun refreshAccount() {
        start()
        client.refreshAccount()
    }

    suspend fun logout() {
        start()
        client.logout()
    }

    suspend fun askHistory(
        question: String,
        memories: List<HistoryMemory>,
        reasoningEffort: String? = null,
    ): String {
        start()
        return try {
            client.askHistory(
                question = question,
                memories = memories,
                workspace = File(context.filesDir, "codex-workspace").apply { mkdirs() }.absolutePath,
                reasoningEffort = reasoningEffort,
            )
        } finally {
            scheduleIdleStop()
        }
    }

    fun stop() {
        stopRuntime(cancelIdleTimer = true)
    }

    private fun stopRuntime(cancelIdleTimer: Boolean) {
        if (cancelIdleTimer) idleStopJob?.cancel()
        idleStopJob = null
        client.disconnect()
        process?.destroy()
        process = null
        networkProxy?.close()
        networkProxy = null
        _state.value = _state.value.copy(status = CodexRuntimeStatus.Stopped)
    }

    private fun scheduleIdleStop() {
        idleStopJob?.cancel()
        idleStopJob = scope.launch {
            delay(IDLE_STOP_DELAY_MS)
            stopRuntime(cancelIdleTimer = false)
            context.stopService(Intent(context, CodexRuntimeService::class.java))
        }
    }

    private fun binaryFile(): File = File(context.applicationInfo.nativeLibraryDir, "libcodex_android.so")

    private fun createAndroidCaBundle(codexHome: File): File {
        val certificateDirectory = listOf(
            File("/apex/com.android.conscrypt/cacerts"),
            File("/system/etc/security/cacerts"),
        ).firstOrNull { directory -> directory.listFiles()?.isNotEmpty() == true }
            ?: error(context.getString(R.string.runtime_certificates_failed))
        val bundle = File(codexHome, "android-ca-bundle.pem")
        FileOutputStream(bundle, false).buffered().use { output ->
            certificateDirectory.listFiles()
                .orEmpty()
                .filter(File::isFile)
                .sortedBy(File::getName)
                .forEach { certificate ->
                    certificate.inputStream().buffered().use { it.copyTo(output) }
                    output.write('\n'.code)
                }
        }
        check(bundle.length() > 0) { context.getString(R.string.runtime_certificates_empty) }
        return bundle
    }

    private companion object {
        const val IDLE_STOP_DELAY_MS = 5 * 60_000L
    }
}
