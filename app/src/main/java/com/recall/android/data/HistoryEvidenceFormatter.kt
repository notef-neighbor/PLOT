package com.recall.android.data

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

internal object HistoryEvidenceFormatter {
    private val whitespace = Regex("\\s+")
    private val timestampFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    fun clean(raw: String, maxCharacters: Int = 1_800): String {
        val unique = LinkedHashSet<String>()
        raw.split('|', '\n')
            .asSequence()
            .map { it.replace(whitespace, " ").trim() }
            .filter { it.isNotBlank() }
            .forEach { segment ->
                if (unique.none { existing -> existing.equals(segment, ignoreCase = true) }) {
                    unique += segment
                }
            }
        return unique.joinToString(" · ").take(maxCharacters).trimEnd(' ', '·')
    }

    fun promptLine(index: Int, memory: HistoryMemory, zoneId: ZoneId = ZoneId.systemDefault()): String {
        val time = Instant.ofEpochMilli(memory.startedAt).atZone(zoneId).format(timestampFormatter)
        val content = clean(memory.summary)
        return "[$index] time=$time; apps=${memory.applications.joinToString(", ")}; content=$content"
    }
}
