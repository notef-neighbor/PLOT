package com.recall.android.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.recall.android.RecallApplication
import java.time.LocalDate
import java.time.ZoneId

class HistoryRollupWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as RecallApplication).container
        return runCatching {
            val today = LocalDate.now()
            rebuildDay(applicationContext, container, today.minusDays(1))
            rebuildDay(applicationContext, container, today)
        }.fold(
            onSuccess = { Result.success() },
            onFailure = { error ->
                Log.e(TAG, "Could not build six-hour history rollups", error)
                Result.retry()
            },
        )
    }

    companion object {
        const val PERIODIC_WORK = "plot-six-hour-rollups"
        const val STARTUP_WORK = "plot-six-hour-rollups-startup"
        private const val TAG = "PLOT-Rollup"

        suspend fun rebuildDay(
            context: Context,
            container: com.recall.android.data.AppContainer,
            date: LocalDate,
        ): List<com.recall.android.data.HistoryMemory> {
            val zone = ZoneId.systemDefault()
            val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
            val end = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            val allMemories = container.historyRepository.memoriesBetween(start, end)
            val rollups = buildDay(context, date, allMemories, zone)
            container.historyRepository.replaceDerivedMemories(
                sources = SixHourRollup.DERIVED_SOURCES,
                start = start,
                end = end,
                memories = rollups,
            )
            return rollups
        }

        fun buildDay(
            context: Context,
            date: LocalDate,
            memories: List<com.recall.android.data.HistoryMemory>,
            zone: ZoneId = ZoneId.systemDefault(),
        ): List<com.recall.android.data.HistoryMemory> =
            SixHourRollup.buckets(date, memories, zone)
                .map { SixHourRollup.toMemory(context, date, it) }
    }
}
