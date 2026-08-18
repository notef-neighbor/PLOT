package com.recall.android.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.recall.android.RecallApplication
import java.util.concurrent.TimeUnit

class CleanupWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = runCatching {
        val container = (applicationContext as RecallApplication).container
        val cutoff = System.currentTimeMillis() - TimeUnit.HOURS.toMillis(RAW_RETENTION_HOURS)
        container.database.eventDao().deleteOlderThan(cutoff)
    }.fold(
        onSuccess = { Result.success() },
        onFailure = { Result.retry() },
    )

    companion object {
        const val PERIODIC_WORK = "recall-raw-event-cleanup"
        private const val RAW_RETENTION_HOURS = 48L
    }
}
