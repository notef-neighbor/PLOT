package com.recall.android.data

import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Test

class MacHistoryPairingTest {
    @Test
    fun parsesBridgePairingCode() {
        val json = """{"version":1,"url":"https://10.0.0.4:47631","token":"abcdefghijklmnopqrstuvwxyz1234567890","tlsPin":"sha256/abc","deviceId":"mac-1","deviceName":"Studio Mac"}"""
        val code = "PLOT-MAC-1:${Base64.getUrlEncoder().withoutPadding().encodeToString(json.toByteArray())}"

        val pairing = MacHistoryPairing.parse(code)

        assertEquals("https://10.0.0.4:47631", pairing.url)
        assertEquals("Studio Mac", pairing.deviceName)
        assertEquals("sha256/abc", pairing.tlsPin)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNonHttpsPairingCode() {
        val json = """{"version":1,"url":"http://10.0.0.4:47631","token":"abcdefghijklmnopqrstuvwxyz1234567890","tlsPin":"sha256/abc","deviceId":"mac-1","deviceName":"Studio Mac"}"""
        val code = "PLOT-MAC-1:${Base64.getUrlEncoder().withoutPadding().encodeToString(json.toByteArray())}"

        MacHistoryPairing.parse(code)
    }
}
