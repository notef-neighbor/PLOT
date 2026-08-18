package com.recall.android.capture

internal class ContentCaptureThrottle(private val minimumIntervalMs: Long) {
    private val lastCaptureAt = mutableMapOf<String, Long>()
    private val lastFingerprint = mutableMapOf<String, Int>()

    @Synchronized
    fun shouldCapture(packageName: String, fingerprint: Int, now: Long): Boolean {
        if (fingerprint == lastFingerprint[packageName]) return false
        val capturedAt = lastCaptureAt[packageName]
        if (capturedAt != null && now - capturedAt < minimumIntervalMs) return false
        lastCaptureAt[packageName] = now
        lastFingerprint[packageName] = fingerprint
        return true
    }
}
