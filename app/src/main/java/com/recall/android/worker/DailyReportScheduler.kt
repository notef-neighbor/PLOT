package com.recall.android.worker

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.time.Duration
import java.time.LocalDate
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

object DailyReportScheduler {
    private const val SCHEDULED_WORK = "recall-daily-report-scheduled"
    private const val MANUAL_WORK = "recall-daily-report-now"

    fun schedule(context: Context, enabled: Boolean, hour: Int, minute: Int) {
        val manager = WorkManager.getInstance(context)
        if (!enabled) {
            manager.cancelUniqueWork(SCHEDULED_WORK)
            return
        }
        val now = ZonedDateTime.now()
        var next = now.toLocalDate().atTime(hour, minute).atZone(now.zone)
        if (!next.isAfter(now)) next = next.plusDays(1)
        val delayMillis = Duration.between(now, next).toMillis().coerceAtLeast(0)
        val request = OneTimeWorkRequestBuilder<DailyReportWorker>()
            .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            .setInputData(
                Data.Builder()
                    .putString(DailyReportWorker.KEY_REPORT_DATE, next.toLocalDate().toString())
                    .build(),
            )
            .build()
        manager.enqueueUniqueWork(SCHEDULED_WORK, ExistingWorkPolicy.REPLACE, request)
    }

    fun scheduleNext(context: Context, enabled: Boolean, hour: Int, minute: Int) {
        if (!enabled) return
        val now = ZonedDateTime.now()
        var next = now.toLocalDate().atTime(hour, minute).atZone(now.zone)
        if (!next.isAfter(now)) next = next.plusDays(1)
        val request = OneTimeWorkRequestBuilder<DailyReportWorker>()
            .setInitialDelay(Duration.between(now, next).toMillis().coerceAtLeast(0), TimeUnit.MILLISECONDS)
            .setInputData(
                Data.Builder()
                    .putString(DailyReportWorker.KEY_REPORT_DATE, next.toLocalDate().toString())
                    .build(),
            )
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            SCHEDULED_WORK,
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            request,
        )
    }

    fun generateNow(context: Context) {
        val request = OneTimeWorkRequestBuilder<DailyReportWorker>()
            .setInputData(
                Data.Builder()
                    .putBoolean(DailyReportWorker.KEY_MANUAL, true)
                    .putString(DailyReportWorker.KEY_REPORT_DATE, LocalDate.now().toString())
                    .build(),
            )
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(MANUAL_WORK, ExistingWorkPolicy.REPLACE, request)
    }
}
