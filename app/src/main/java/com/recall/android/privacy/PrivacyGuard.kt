package com.recall.android.privacy

import com.recall.android.data.CapturedInteraction

data class GuardDecision(
    val allowed: Boolean,
    val sanitizedText: String? = null,
    val contentRedacted: Boolean = false,
    val reason: String? = null,
)

class PrivacyGuard(private val ownPackage: String) {
    fun evaluate(
        interaction: CapturedInteraction,
        allowedPackages: Set<String>,
        passwordField: Boolean,
    ): GuardDecision {
        val packageName = interaction.packageName
        if (packageName == ownPackage) return GuardDecision(false, reason = "own_app")
        if (packageName !in allowedPackages) return GuardDecision(false, reason = "not_allowed")
        if (passwordField) return GuardDecision(false, reason = "password_field")
        if (isProtectedPackage(packageName)) return GuardDecision(false, reason = "protected_application")

        return GuardDecision(
            allowed = true,
            sanitizedText = sanitize(interaction.text),
            contentRedacted = false,
        )
    }

    private fun sanitize(value: String?): String? {
        return value
            ?.replace(Regex("\\s+"), " ")
            ?.trim()
            ?.take(MAX_CAPTURED_TEXT)
            ?.takeIf(String::isNotBlank)
    }

    fun isProtectedPackage(packageName: String): Boolean = protectedPackagePrefixes.any { prefix ->
        packageName == prefix || packageName.startsWith("$prefix.")
    }

    companion object {
        private val protectedPackagePrefixes = setOf(
            "com.android.systemui",
            "com.google.android.permissioncontroller",
            "com.android.permissioncontroller",
            "com.google.android.apps.authenticator2",
            "jp.go.digital.auth_and_sign",
            "com.azure.authenticator",
            "com.authy.authy",
            "com.twilio.authy",
            "com.okta.android.auth",
            "com.duosecurity.duomobile",
            "com.lastpass.authenticator",
            "org.fedorahosted.freeotp",
            "org.fedorahosted.freeotp.plus",
            "com.beemdevelopment.aegis",
            "com.twofasapp",
            "com.yubico.authenticator",
            "com.yubico.yubioath",
            "com.x8bit.bitwarden",
            "com.bitwarden.android",
            "com.agilebits.onepassword",
            "com.onepassword.android",
            "com.lastpass.lpandroid",
            "com.dashlane",
            "com.enpass.app",
            "com.keepersecurity",
            "com.nordpass.android.app.password.manager",
        )
        private const val MAX_CAPTURED_TEXT = 8_000
    }
}
