package com.recall.android.capture

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContentCaptureThrottleTest {
    @Test
    fun `captures distinct settled content but rejects duplicate and burst events`() {
        val throttle = ContentCaptureThrottle(minimumIntervalMs = 2_000)
        assertTrue(throttle.shouldCapture("com.twitter.android", fingerprint = 1, now = 10_000))
        assertFalse(throttle.shouldCapture("com.twitter.android", fingerprint = 1, now = 13_000))
        assertFalse(throttle.shouldCapture("com.twitter.android", fingerprint = 2, now = 11_000))
        assertTrue(throttle.shouldCapture("com.twitter.android", fingerprint = 2, now = 12_000))
    }

    @Test
    fun `throttles each application independently`() {
        val throttle = ContentCaptureThrottle(minimumIntervalMs = 2_000)
        assertTrue(throttle.shouldCapture("com.twitter.android", fingerprint = 1, now = 10_000))
        assertTrue(throttle.shouldCapture("com.android.chrome", fingerprint = 1, now = 10_100))
    }
}
