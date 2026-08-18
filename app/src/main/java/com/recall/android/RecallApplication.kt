package com.recall.android

import android.app.Application
import androidx.work.Configuration
import androidx.work.ExistingWorkPolicy
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.recall.android.data.AppContainer
import com.recall.android.worker.CleanupWorker
import com.recall.android.worker.HistoryRollupWorker
import com.recall.android.worker.SummarizeWorker
import com.recall.android.worker.DailyReportScheduler
import com.recall.android.worker.CalendarSyncScheduler
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collectLatest

class RecallApplication : Application(), Configuration.Provider {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    lateinit var container: AppContainer
        private set

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setMinimumLoggingLevel(android.util.Log.INFO).build()

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        applicationScope.launch {
            container.historyRepository.repairLegacyTimestamps()
            container.historyRepository.prepareDerivedHistory()
            scheduleMaintenance()
        }
        applicationScope.launch {
            container.settings.state.collectLatest { settings ->
                DailyReportScheduler.schedule(
                    context = this@RecallApplication,
                    enabled = settings.dailyReportEnabled,
                    hour = settings.dailyReportHour,
                    minute = settings.dailyReportMinute,
                )
            }
        }
        CalendarSyncScheduler.schedule(this)
    }

    private fun scheduleMaintenance() {
        val manager = WorkManager.getInstance(this)
        manager.enqueueUniqueWork(
            SummarizeWorker.DEBOUNCED_WORK,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<SummarizeWorker>().build(),
        )
        manager.enqueueUniquePeriodicWork(
            SummarizeWorker.PERIODIC_WORK,
            ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<SummarizeWorker>(15, TimeUnit.MINUTES).build(),
        )
        manager.enqueueUniqueWork(
            HistoryRollupWorker.STARTUP_WORK,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<HistoryRollupWorker>().build(),
        )
        manager.enqueueUniquePeriodicWork(
            HistoryRollupWorker.PERIODIC_WORK,
            ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<HistoryRollupWorker>(6, TimeUnit.HOURS).build(),
        )
        manager.enqueueUniquePeriodicWork(
            CleanupWorker.PERIODIC_WORK,
            ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<CleanupWorker>(12, TimeUnit.HOURS).build(),
        )
    }
}
