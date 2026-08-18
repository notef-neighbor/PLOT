package com.recall.android.worker

import android.content.Context
import com.recall.android.R
import com.recall.android.data.HistoryEvidenceFormatter
import com.recall.android.data.HistoryMemory
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

internal data class SixHourBucket(
    val index: Int,
    val startedAt: Long,
    val endedAt: Long,
    val memories: List<HistoryMemory>,
)

internal object SixHourRollup {
    const val SOURCE = "rollup_6h_local"
    val DERIVED_SOURCES = listOf(SOURCE, "rollup_6h_codex")

    fun buckets(
        date: LocalDate,
        memories: List<HistoryMemory>,
        zone: ZoneId,
    ): List<SixHourBucket> {
        val dayStart = date.atStartOfDay(zone)
        return (0 until 4).mapNotNull { index ->
            val bucketStart = dayStart.plusHours(index * HOURS_PER_BUCKET.toLong())
            val bucketEnd = if (index == 3) date.plusDays(1).atStartOfDay(zone) else bucketStart.plusHours(HOURS_PER_BUCKET.toLong())
            val start = bucketStart.toInstant().toEpochMilli()
            val end = bucketEnd.toInstant().toEpochMilli()
            val matches = memories
                .asSequence()
                .filter(::isBaseMemory)
                .filter { it.startedAt in start until end }
                .sortedBy(HistoryMemory::startedAt)
                .toList()
            matches.takeIf(List<HistoryMemory>::isNotEmpty)?.let {
                SixHourBucket(index, start, end, it)
            }
        }
    }

    fun toMemory(context: Context, date: LocalDate, bucket: SixHourBucket): HistoryMemory {
        val locale = context.resources.configuration.locales[0]
        val timeFormatter = DateTimeFormatter.ofPattern("HH:mm", locale).withZone(ZoneId.systemDefault())
        val allApplications = bucket.memories.flatMap(HistoryMemory::applications)
        val applications = allApplications
            .groupingBy { it.lowercase() }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .mapNotNull { counted -> allApplications.firstOrNull { it.lowercase() == counted.key } }
            .take(MAX_APPS)
        val evidence = selectEvidence(bucket.memories)
        val startLabel = timeFormatter.format(java.time.Instant.ofEpochMilli(bucket.startedAt))
        val endLabel = timeFormatter.format(java.time.Instant.ofEpochMilli(bucket.endedAt))
        val title = context.getString(R.string.rollup_title, startLabel, endLabel)
        val summary = buildString {
            appendLine(context.getString(R.string.rollup_summary, bucket.memories.size, bucket.memories.sumOf(HistoryMemory::eventCount)))
            if (applications.isNotEmpty()) appendLine(context.getString(R.string.rollup_apps, applications.joinToString(", ")))
            evidence.forEach { memory ->
                val time = timeFormatter.format(java.time.Instant.ofEpochMilli(memory.startedAt))
                appendLine(context.getString(R.string.rollup_activity, time, HistoryEvidenceFormatter.clean(memory.summary).take(MAX_EVIDENCE_CHARS)))
            }
        }.trim()
        val keywords = (applications + bucket.memories.flatMap(HistoryMemory::keywords))
            .map(String::trim)
            .filter(String::isNotBlank)
            .distinctBy { it.lowercase() }
            .take(MAX_KEYWORDS)

        return HistoryMemory(
            id = "rollup-6h-$date-${bucket.index}",
            startedAt = bucket.startedAt,
            endedAt = bucket.endedAt,
            title = title,
            summary = summary,
            applications = applications,
            keywords = keywords,
            eventCount = bucket.memories.sumOf(HistoryMemory::eventCount),
            source = SOURCE,
        )
    }

    private fun selectEvidence(memories: List<HistoryMemory>): List<HistoryMemory> {
        if (memories.size <= MAX_EVIDENCE) return memories
        val important = memories.sortedByDescending(HistoryMemory::eventCount).take(MAX_EVIDENCE / 2)
        val remainingSlots = MAX_EVIDENCE - important.size
        val sampled = (0 until remainingSlots).map { index ->
            memories[index * (memories.lastIndex) / (remainingSlots - 1).coerceAtLeast(1)]
        }
        return (important + sampled).distinctBy(HistoryMemory::id).sortedBy(HistoryMemory::startedAt).take(MAX_EVIDENCE)
    }

    private fun isBaseMemory(memory: HistoryMemory): Boolean =
        !memory.source.startsWith("daily_") && !memory.source.startsWith("rollup_")

    private const val HOURS_PER_BUCKET = 6
    private const val MAX_APPS = 8
    private const val MAX_EVIDENCE = 24
    private const val MAX_EVIDENCE_CHARS = 320
    private const val MAX_KEYWORDS = 48
}
