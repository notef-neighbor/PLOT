package com.recall.android.worker

import com.recall.android.data.HistoryMemory
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class SixHourRollupTest {
    private val zone = ZoneId.of("UTC")
    private val date = LocalDate.of(2026, 8, 18)

    @Test
    fun `groups short summaries into local six hour windows`() {
        val buckets = SixHourRollup.buckets(
            date = date,
            zone = zone,
            memories = listOf(
                memory("a", "2026-08-18T00:10:00Z"),
                memory("b", "2026-08-18T05:59:59Z"),
                memory("c", "2026-08-18T06:00:00Z"),
                memory("d", "2026-08-18T18:30:00Z"),
            ),
        )

        assertEquals(listOf(0, 1, 3), buckets.map(SixHourBucket::index))
        assertEquals(listOf(listOf("a", "b"), listOf("c"), listOf("d")), buckets.map { it.memories.map(HistoryMemory::id) })
    }

    @Test
    fun `does not recursively include reports or earlier rollups`() {
        val buckets = SixHourRollup.buckets(
            date = date,
            zone = zone,
            memories = listOf(
                memory("base", "2026-08-18T01:00:00Z"),
                memory("daily", "2026-08-18T02:00:00Z", "daily_codex"),
                memory("rollup", "2026-08-18T03:00:00Z", SixHourRollup.SOURCE),
            ),
        )

        assertEquals(listOf("base"), buckets.single().memories.map(HistoryMemory::id))
    }

    private fun memory(id: String, at: String, source: String = "local") = HistoryMemory(
        id = id,
        startedAt = Instant.parse(at).toEpochMilli(),
        endedAt = Instant.parse(at).toEpochMilli(),
        title = id,
        summary = "summary $id",
        applications = listOf("App"),
        keywords = listOf(id),
        eventCount = 1,
        source = source,
    )
}
