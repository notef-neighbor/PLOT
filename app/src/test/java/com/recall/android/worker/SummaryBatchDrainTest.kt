package com.recall.android.worker

import java.util.ArrayDeque
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class SummaryBatchDrainTest {
    @Test
    fun `drains a backlog larger than one database batch`() = runTest {
        val queue = ArrayDeque((1..6_076).toList())
        var loads = 0

        val processed = drainSummaryBatches(
            maxBatches = 10,
            loadBatch = {
                loads += 1
                buildList {
                    repeat(minOf(1_000, queue.size)) { add(queue.removeFirst()) }
                }
            },
            processBatch = { },
        )

        assertEquals(6_076, processed)
        assertEquals(8, loads)
        assertEquals(0, queue.size)
    }

    @Test
    fun `respects the per-run safety limit`() = runTest {
        val queue = ArrayDeque((1..25_000).toList())

        val processed = drainSummaryBatches(
            maxBatches = 10,
            loadBatch = {
                buildList {
                    repeat(minOf(1_000, queue.size)) { add(queue.removeFirst()) }
                }
            },
            processBatch = { },
        )

        assertEquals(10_000, processed)
        assertEquals(15_000, queue.size)
    }
}
