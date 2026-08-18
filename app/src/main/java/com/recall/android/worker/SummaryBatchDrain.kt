package com.recall.android.worker

internal suspend fun <T> drainSummaryBatches(
    maxBatches: Int,
    loadBatch: suspend () -> List<T>,
    processBatch: suspend (List<T>) -> Unit,
): Int {
    var processed = 0
    repeat(maxBatches) {
        val batch = loadBatch()
        if (batch.isEmpty()) return processed
        processBatch(batch)
        processed += batch.size
    }
    return processed
}
