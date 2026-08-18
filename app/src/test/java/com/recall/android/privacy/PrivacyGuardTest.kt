package com.recall.android.privacy

import com.recall.android.data.CapturedInteraction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivacyGuardTest {
    private val guard = PrivacyGuard("com.recall.android")
    private val base = CapturedInteraction(
        occurredAt = 1,
        kind = "view_clicked",
        packageName = "com.example.notes",
        applicationLabel = "Notes",
        className = "Button",
        text = "Project plan",
        viewId = "save",
        contentRedacted = false,
    )

    @Test
    fun `default deny rejects an application not explicitly allowed`() {
        val result = guard.evaluate(base, emptySet(), passwordField = false)
        assertFalse(result.allowed)
        assertEquals("not_allowed", result.reason)
    }

    @Test
    fun `password fields are rejected before persistence`() {
        val result = guard.evaluate(base, setOf(base.packageName), passwordField = true)
        assertFalse(result.allowed)
        assertEquals("password_field", result.reason)
    }

    @Test
    fun `authenticator applications are always rejected`() {
        val authenticator = base.copy(packageName = "com.google.android.apps.authenticator2")
        val result = guard.evaluate(authenticator, setOf(authenticator.packageName), passwordField = false)
        assertFalse(result.allowed)
        assertEquals("protected_application", result.reason)
    }

    @Test
    fun `digital agency authenticator is always rejected`() {
        val authenticator = base.copy(packageName = "jp.go.digital.auth_and_sign")
        val result = guard.evaluate(authenticator, setOf(authenticator.packageName), passwordField = false)
        assertFalse(result.allowed)
        assertEquals("protected_application", result.reason)
    }

    @Test
    fun `browser content is captured when explicitly allowed`() {
        val browser = base.copy(packageName = "com.android.chrome", text = "Secret tab text")
        val result = guard.evaluate(browser, setOf(browser.packageName), passwordField = false)
        assertTrue(result.allowed)
        assertFalse(result.contentRedacted)
        assertEquals("Secret tab text", result.sanitizedText)
    }

    @Test
    fun `non-password text is retained without content redaction`() {
        val event = base.copy(text = "api_key=very-secret-value")
        val result = guard.evaluate(event, setOf(event.packageName), passwordField = false)
        assertTrue(result.allowed)
        assertEquals("api_key=very-secret-value", result.sanitizedText)
    }
}
