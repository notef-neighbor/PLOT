package com.recall.android.codex

enum class CodexRuntimeStatus {
    Unavailable,
    Stopped,
    Starting,
    Connecting,
    LoginRequired,
    Authenticated,
    Error,
}

data class CodexRuntimeState(
    val status: CodexRuntimeStatus = CodexRuntimeStatus.Stopped,
    val accountEmail: String? = null,
    val planType: String? = null,
    val verificationUrl: String? = null,
    val userCode: String? = null,
    val message: String? = null,
    val model: String? = null,
    val modelDisplayName: String? = null,
    val reasoningEffort: String? = null,
    val hasStoredLogin: Boolean = false,
) {
    val isReady: Boolean get() = status == CodexRuntimeStatus.Authenticated
}

data class CodexDeviceLogin(
    val verificationUrl: String,
    val userCode: String,
)

data class CodexBrowserLogin(
    val authUrl: String,
)
