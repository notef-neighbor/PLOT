package com.recall.android.data

import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryEvidenceFormatterTest {
    @Test
    fun `removes duplicate accessibility labels and normalizes whitespace`() {
        assertEquals(
            "Route A · Compare plans",
            HistoryEvidenceFormatter.clean(" Route A | Route A | Compare   plans\nCompare plans "),
        )
    }

    @Test
    fun `prompt evidence uses readable time instead of epoch and omits raw title`() {
        val line = HistoryEvidenceFormatter.promptLine(
            1,
            HistoryMemory("id", 0, 0, "raw", "Route A | Route A", listOf("X"), emptyList(), 1, "raw_event"),
            ZoneId.of("UTC"),
        )
        assertTrue(line.contains("1970-01-01 00:00:00"))
        assertTrue(line.contains("content=Route A"))
        assertFalse(line.contains("| Route A"))
    }
}
