package com.recall.android.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.recall.android.RecallApplication
import com.recall.android.data.AppContainer
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class MacHistorySyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as RecallApplication).container
        return runCatching { sync(container) }.fold(
            onSuccess = { Result.success() },
            onFailure = { error ->
                val current = container.settings.state.value
                container.settings.setMacHistorySyncState(
                    cursor = current.macHistoryCursor,
                    lastSyncAt = current.macHistoryLastSyncAt,
                    error = error.message ?: "Mac sync failed",
                )
                Result.retry()
            },
        )
    }

    companion object {
        const val STARTUP_WORK = "mac-history-startup"
        const val PERIODIC_WORK = "mac-history-periodic"
        const val MANUAL_WORK = "mac-history-manual"

        fun enqueueNow(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                MANUAL_WORK,
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<MacHistorySyncWorker>().build(),
            )
        }

        suspend fun sync(container: AppContainer): Int = syncMutex.withLock {
            var settings = container.settings.state.first()
            if (!settings.macHistoryEnabled || settings.macHistoryToken.isBlank()) return@withLock 0
            var cursor = settings.macHistoryCursor
            var imported = 0
            repeat(MAX_PAGES_PER_RUN) {
                val page = container.macHistoryClient.fetch(settings, cursor)
                container.historyRepository.saveMemories(page.memories)
                imported += page.memories.size
                cursor = page.nextCursor
                container.settings.setMacHistorySyncState(cursor, System.currentTimeMillis(), null)
                if (!page.hasMore || page.nextCursor == settings.macHistoryCursor) return@withLock imported
                settings = settings.copy(macHistoryCursor = cursor)
            }
            imported
        }

        private const val MAX_PAGES_PER_RUN = 8
        private val syncMutex = Mutex()
    }
}
