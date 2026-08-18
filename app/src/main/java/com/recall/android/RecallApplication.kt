package com.recall.android

import android.app.Application
import androidx.work.Configuration
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.recall.android.data.AppContainer
import com.recall.android.worker.CleanupWorker
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
        applicationScope.launch { container.historyRepository.repairLegacyTimestamps() }
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
        scheduleMaintenance()
        CalendarSyncScheduler.schedule(this)
    }

    private fun scheduleMaintenance() {
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            SummarizeWorker.PERIODIC_WORK,
            ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<SummarizeWorker>(15, TimeUnit.MINUTES).build(),
        )
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            CleanupWorker.PERIODIC_WORK,
            ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<CleanupWorker>(12, TimeUnit.HOURS).build(),
        )
    }
}
