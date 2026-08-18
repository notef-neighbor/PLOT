package com.recall.android.worker

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.recall.android.RecallApplication
import com.recall.android.data.CapturedInteraction
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

class CalendarSyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        if (
            ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.READ_CALENDAR) !=
            PackageManager.PERMISSION_GRANTED
        ) return Result.success()

        val container = (applicationContext as RecallApplication).container
        if (!container.settings.state.value.disclosureAccepted) return Result.success()
        val now = System.currentTimeMillis()
        val from = now - TimeUnit.DAYS.toMillis(30)
        val until = now + TimeUnit.DAYS.toMillis(365)
        val projection = arrayOf(
            CalendarContract.Events._ID,
            CalendarContract.Events.TITLE,
            CalendarContract.Events.DESCRIPTION,
            CalendarContract.Events.EVENT_LOCATION,
            CalendarContract.Events.DTSTART,
            CalendarContract.Events.DTEND,
            CalendarContract.Events.CALENDAR_DISPLAY_NAME,
        )

        return runCatching {
            applicationContext.contentResolver.query(
                CalendarContract.Events.CONTENT_URI,
                projection,
                "${CalendarContract.Events.DTSTART} >= ? AND ${CalendarContract.Events.DTSTART} <= ?",
                arrayOf(from.toString(), until.toString()),
                "${CalendarContract.Events.DTSTART} ASC",
            )?.use { cursor ->
                val idIndex = cursor.getColumnIndexOrThrow(CalendarContract.Events._ID)
                val titleIndex = cursor.getColumnIndexOrThrow(CalendarContract.Events.TITLE)
                val descriptionIndex = cursor.getColumnIndexOrThrow(CalendarContract.Events.DESCRIPTION)
                val locationIndex = cursor.getColumnIndexOrThrow(CalendarContract.Events.EVENT_LOCATION)
                val startIndex = cursor.getColumnIndexOrThrow(CalendarContract.Events.DTSTART)
                val endIndex = cursor.getColumnIndexOrThrow(CalendarContract.Events.DTEND)
                val calendarIndex = cursor.getColumnIndexOrThrow(CalendarContract.Events.CALENDAR_DISPLAY_NAME)
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idIndex)
                    val start = cursor.getLong(startIndex)
                    val end = cursor.getLong(endIndex).takeIf { it > 0 } ?: start
                    val title = cursor.getString(titleIndex).orEmpty().ifBlank { applicationContext.getString(com.recall.android.R.string.calendar_untitled) }
                    val description = cursor.getString(descriptionIndex).orEmpty()
                    val location = cursor.getString(locationIndex).orEmpty()
                    val calendar = cursor.getString(calendarIndex).orEmpty()
                    val text = buildString {
                        append(title)
                        append(" | ").append(formatTime(start)).append(" - ").append(formatTime(end))
                        if (calendar.isNotBlank()) append(" | calendar=").append(calendar)
                        if (location.isNotBlank()) append(" | location=").append(location)
                        if (description.isNotBlank()) append(" | ").append(description)
                    }.take(MAX_EVENT_TEXT)
                    container.historyRepository.record(
                        interaction = CapturedInteraction(
                            occurredAt = start,
                            kind = "calendar_event",
                            packageName = CALENDAR_SOURCE_PACKAGE,
                            applicationLabel = "Calendar",
                            className = CalendarContract.Events::class.java.name,
                            text = text,
                            viewId = id.toString(),
                            contentRedacted = false,
                        ),
                        stableKey = "calendar:$id:$start:$end:${text.hashCode()}",
                    )
                }
            }
        }.fold(
            onSuccess = { Result.success() },
            onFailure = { Result.retry() },
        )
    }

    private fun formatTime(value: Long): String = FORMATTER.format(Instant.ofEpochMilli(value))

    private companion object {
        const val CALENDAR_SOURCE_PACKAGE = "android.calendar.provider"
        const val MAX_EVENT_TEXT = 8_000
        val FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            .withZone(ZoneId.systemDefault())
    }
}

object CalendarSyncScheduler {
    private const val PERIODIC_WORK = "recall-calendar-sync"

    fun schedule(context: Context) {
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_WORK,
            ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<CalendarSyncWorker>(6, TimeUnit.HOURS).build(),
        )
    }

    fun syncNow(context: Context) {
        WorkManager.getInstance(context).enqueue(OneTimeWorkRequestBuilder<CalendarSyncWorker>().build())
    }
}
