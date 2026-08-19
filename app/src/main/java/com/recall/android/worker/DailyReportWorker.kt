package com.recall.android.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.recall.android.MainActivity
import com.recall.android.R
import com.recall.android.RecallApplication
import com.recall.android.data.HistoryMemory
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.max

class DailyReportWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as RecallApplication).container
        val settings = container.settings.state.value
        val manual = inputData.getBoolean(KEY_MANUAL, false)
        if (!manual && !settings.dailyReportEnabled) return Result.success()

        val date = inputData.getString(KEY_REPORT_DATE)
            ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?: LocalDate.now()
        val zone = ZoneId.systemDefault()
        val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        if (settings.macHistoryEnabled) {
            runCatching { MacHistorySyncWorker.sync(container) }
                .onFailure { Log.w(TAG, "Mac sync before daily report failed", it) }
        }
        val memories = container.historyRepository.memoriesBetween(start, end)
            .filter { !it.source.startsWith("daily_") && !it.source.startsWith("rollup_") }

        return runCatching {
            val androidMemories = memories.filterNot { it.source.startsWith("mac_") }
            val macSixHour = memories.filter { it.source == "mac_6h" }
            val macTenMinute = memories.filter { it.source == "mac_10min" }
            val local = buildLocalReport(date, androidMemories, macSixHour.ifEmpty { macTenMinute })
            val rollups = HistoryRollupWorker.buildDay(applicationContext, date, androidMemories, zone)
            container.historyRepository.replaceDerivedMemories(
                sources = SixHourRollup.DERIVED_SOURCES,
                start = start,
                end = end,
                memories = rollups,
            )
            val reportEvidence = (rollups + macSixHour).ifEmpty { androidMemories + macTenMinute }
            val generatedText = if (memories.isNotEmpty() && hasCodexAuth()) {
                runCatching {
                    container.codexRuntime.askHistory(
                        question = buildPrompt(date, local),
                        memories = reportEvidence,
                        reasoningEffort = "medium",
                    )
                }.onFailure { Log.w(TAG, "Codex daily report failed; using local report", it) }
                    .getOrDefault(local)
            } else {
                local
            }
            val usedCodex = generatedText != local
            val reportText = normalizeReport(generatedText)
            val applications = memories
                .flatMap { memory -> memory.applications.map { it to max(memory.eventCount, 1) } }
                .groupBy({ it.first }, { it.second })
                .mapValues { (_, values) -> values.sum() }
                .entries.sortedByDescending { it.value }.take(6).map { it.key }
            val generatedAt = System.currentTimeMillis()
            container.historyRepository.saveMemory(
                HistoryMemory(
                    id = "daily-report-$date",
                    startedAt = generatedAt,
                    endedAt = generatedAt,
                    title = applicationContext.getString(R.string.report_title, formatDate(date)),
                    summary = reportText,
                    applications = applications,
                    keywords = listOf(applicationContext.getString(R.string.report_keyword), date.toString()) + applications,
                    eventCount = memories.sumOf { it.eventCount },
                    source = if (usedCodex) "daily_codex" else "daily_local",
                ),
            )
            showNotification(date, reportText)
        }.fold(
            onSuccess = {
                if (manual) {
                    DailyReportScheduler.schedule(
                        applicationContext,
                        settings.dailyReportEnabled,
                        settings.dailyReportHour,
                        settings.dailyReportMinute,
                    )
                } else {
                    DailyReportScheduler.scheduleNext(
                        applicationContext,
                        settings.dailyReportEnabled,
                        settings.dailyReportHour,
                        settings.dailyReportMinute,
                    )
                }
                Result.success()
            },
            onFailure = { Result.retry() },
        )
    }

    private fun hasCodexAuth(): Boolean =
        File(applicationContext.filesDir, "codex-home/auth.json").isFile

    private fun buildLocalReport(
        date: LocalDate,
        androidMemories: List<HistoryMemory>,
        macMemories: List<HistoryMemory>,
    ): String {
        val memories = androidMemories + macMemories
        if (memories.isEmpty()) return applicationContext.getString(R.string.report_empty)
        val eventCount = androidMemories.sumOf { it.eventCount }
        val activeMinutes = memories.sumOf { ((it.endedAt - it.startedAt).coerceAtLeast(0) / 60_000) }
        val appCounts = memories
            .flatMap { memory -> memory.applications.map { it to max(memory.eventCount, 1) } }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, values) -> values.sum() }
            .entries.sortedByDescending { it.value }.take(5)
        return buildString {
            appendLine(applicationContext.getString(R.string.report_local_summary, formatDate(date), androidMemories.size, eventCount))
            if (macMemories.isNotEmpty()) appendLine(applicationContext.getString(R.string.report_mac_summary, macMemories.size))
            if (activeMinutes > 0) appendLine(applicationContext.getString(R.string.report_active_minutes, activeMinutes))
            if (appCounts.isNotEmpty()) appendLine(applicationContext.getString(R.string.report_top_apps, appCounts.joinToString(", ") { it.key }))
            append(applicationContext.getString(R.string.report_main_activity, memories.take(5).joinToString(" / ") { it.title }))
        }.trim()
    }

    private fun buildPrompt(date: LocalDate, local: String): String =
        applicationContext.getString(R.string.report_prompt, formatDate(date), local)

    private fun formatDate(date: LocalDate): String = date.format(
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
            .withLocale(applicationContext.resources.configuration.locales[0]),
    )

    private fun normalizeReport(report: String): String = report
        .replace(Regex("(?m)^#{1,6}\\s*"), "")
        .replace("**", "")
        .trim()

    private fun showNotification(date: LocalDate, summary: String) {
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, applicationContext.getString(R.string.report_channel), NotificationManager.IMPORTANCE_DEFAULT),
        )
        val openIntent = PendingIntent.getActivity(
            applicationContext,
            0,
            Intent(applicationContext, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_recall)
            .setContentTitle(applicationContext.getString(R.string.report_title, formatDate(date)))
            .setContentText(summary.lineSequence().firstOrNull().orEmpty().take(120))
            .setStyle(NotificationCompat.BigTextStyle().bigText(summary.take(1_000)))
            .setContentIntent(openIntent)
            .setAutoCancel(true)
            .build()
        manager.notify(NOTIFICATION_ID, notification)
    }

    companion object {
        const val KEY_MANUAL = "manual"
        const val KEY_REPORT_DATE = "report_date"
        private const val CHANNEL_ID = "recall_daily_report"
        private const val NOTIFICATION_ID = 4201
        private const val TAG = "DailyReportWorker"
    }
}
