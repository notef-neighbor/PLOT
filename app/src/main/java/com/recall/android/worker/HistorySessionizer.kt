package com.recall.android.worker

import com.recall.android.data.InteractionEventEntity

internal object HistorySessionizer {
    fun group(
        events: List<InteractionEventEntity>,
        sessionGapMs: Long,
    ): List<List<InteractionEventEntity>> {
        if (events.isEmpty()) return emptyList()
        val groups = mutableListOf<MutableList<InteractionEventEntity>>()
        events.forEach { event ->
            val current = groups.lastOrNull()
            val previous = current?.lastOrNull()
            val startsNewSession = previous == null ||
                event.sourceHash != previous.sourceHash ||
                event.occurredAt - previous.occurredAt > sessionGapMs
            if (startsNewSession) {
                groups += mutableListOf(event)
            } else {
                checkNotNull(current).add(event)
            }
        }
        return groups
    }
}
