package com.recall.android.worker

import com.recall.android.data.InteractionEventEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class HistorySessionizerTest {
    @Test
    fun `keeps consecutive events from one app together`() {
        val groups = HistorySessionizer.group(
            listOf(event("a", 1_000), event("a", 2_000)),
            sessionGapMs = 10_000,
        )

        assertEquals(listOf(2), groups.map { it.size })
    }

    @Test
    fun `starts a new history card when the app changes`() {
        val groups = HistorySessionizer.group(
            listOf(event("discord", 1_000), event("chrome", 2_000), event("discord", 3_000)),
            sessionGapMs = 10_000,
        )

        assertEquals(listOf(1, 1, 1), groups.map { it.size })
    }

    @Test
    fun `starts a new history card after an inactivity gap`() {
        val groups = HistorySessionizer.group(
            listOf(event("chrome", 1_000), event("chrome", 20_000)),
            sessionGapMs = 10_000,
        )

        assertEquals(listOf(1, 1), groups.map { it.size })
    }

    private fun event(source: String, at: Long) = InteractionEventEntity(
        id = "$source:$at",
        occurredAt = at,
        kind = "content_changed",
        sourceHash = source,
        encryptedPayload = "payload",
    )
}
