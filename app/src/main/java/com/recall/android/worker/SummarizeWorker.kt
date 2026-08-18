package com.recall.android.worker

import android.content.Context
import android.util.Log
import androidx.room.withTransaction
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.recall.android.R
import com.recall.android.RecallApplication
import com.recall.android.data.EventPayload
import com.recall.android.data.HistoryMemory
import com.recall.android.data.HistoryEvidenceFormatter
import com.recall.android.data.InteractionEventEntity
import com.recall.android.data.MemoryEntity
import com.recall.android.data.PayloadCodec
import java.security.MessageDigest
import java.time.LocalDate

class SummarizeWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as RecallApplication).container

        return runCatching {
            drainSummaryBatches(
                maxBatches = MAX_BATCHES_PER_RUN,
                loadBatch = { container.database.eventDao().unprocessed(System.currentTimeMillis()) },
                processBatch = { events -> processBatch(container, events) },
            )
            HistoryRollupWorker.rebuildDay(applicationContext, container, LocalDate.now())
        }.fold(
            onSuccess = { Result.success() },
            onFailure = { error ->
                Log.e(TAG, "Could not turn captured events into history", error)
                Result.retry()
            },
        )
    }

    private suspend fun processBatch(
        container: com.recall.android.data.AppContainer,
        events: List<InteractionEventEntity>,
    ) {
        val groups = HistorySessionizer.group(events, SESSION_GAP_MS)
        val settings = container.settings.state.value
        var unreadableCount = 0
        var firstUnreadableType: String? = null
        val prepared = groups.map { group ->
            val decoded = group.mapNotNull { event ->
                runCatching {
                    event to PayloadCodec.decodeEvent(container.cipher.decrypt(event.encryptedPayload))
                }.getOrElse { error ->
                    unreadableCount += 1
                    if (firstUnreadableType == null) firstUnreadableType = error::class.java.simpleName
                    null
                }
            }
            val memory = decoded.takeIf { it.isNotEmpty() }?.let { validEvents ->
                val localMemory = localSummary(validEvents)
                if (settings.gatewayUrl.isNotBlank() && settings.deviceToken.isNotBlank()) {
                    runCatching {
                        val remote = container.gatewayClient.summarize(
                            baseUrl = settings.gatewayUrl,
                            token = settings.deviceToken,
                            sessionId = localMemory.id,
                            locale = applicationContext.resources.configuration.locales[0].toLanguageTag(),
                            events = validEvents,
                        )
                        localMemory.copy(
                            title = remote.title,
                            summary = remote.summary,
                            keywords = remote.keywords,
                            source = "gateway",
                        )
                    }.getOrDefault(localMemory)
                } else {
                    localMemory
                }
            }
            group to memory
        }
        if (unreadableCount > 0) {
            Log.w(TAG, "Skipped $unreadableCount unreadable events (${firstUnreadableType ?: "unknown error"})")
        }

        container.database.withTransaction {
            prepared.forEach { (group, memory) ->
                if (memory != null) {
                    container.database.memoryDao().insert(
                        MemoryEntity(
                            id = memory.id,
                            startedAt = memory.startedAt,
                            endedAt = memory.endedAt,
                            encryptedPayload = container.cipher.encrypt(PayloadCodec.encodeMemory(memory)),
                            eventCount = memory.eventCount,
                            source = memory.source,
                        ),
                    )
                }
                // One unreadable event must not block every later history entry forever.
                container.database.eventDao().markProcessed(group.map { it.id })
            }
        }
    }

    private fun localSummary(events: List<Pair<InteractionEventEntity, EventPayload>>): HistoryMemory {
        val first = events.first().first
        val last = events.last().first
        val applications = events.map { it.second.applicationLabel }.distinct()
        val clicks = events.count { it.first.kind == "view_clicked" }
        val edits = events.count { it.first.kind == "text_changed" }
        val visibleText = events.mapNotNull { it.second.text }
            .map(HistoryEvidenceFormatter::clean)
            .filter(String::isNotBlank)
            .filterNot { it == "Text edited" }
            .distinct()
            .take(4)

        val action = when {
            edits > 0 && clicks > 0 -> applicationContext.getString(R.string.summary_view_edit)
            edits > 0 -> applicationContext.getString(R.string.summary_edit)
            clicks > 0 -> applicationContext.getString(R.string.summary_interact)
            else -> applicationContext.getString(R.string.summary_view)
        }
        val appNames = applications.joinToString(", ")
        val title = applicationContext.getString(R.string.summary_title, applications.firstOrNull() ?: "Android", action)
        val summary = buildString {
            append(applicationContext.getString(R.string.summary_body, events.size, appNames))
            if (visibleText.isNotEmpty()) append(applicationContext.getString(R.string.summary_main_content, visibleText.joinToString(" / ")))
        }
        val keywords = visibleText.flatMap { text ->
            text.split(Regex("[\\s、。/・:：]+"))
        }.map(String::trim).filter { it.length in 2..40 }.distinct().take(12)

        return HistoryMemory(
            id = stableId(first.id, last.id),
            startedAt = first.occurredAt,
            endedAt = last.occurredAt,
            title = title,
            summary = summary,
            applications = applications,
            keywords = keywords,
            eventCount = events.size,
            source = "local",
        )
    }

    private fun stableId(first: String, last: String): String = MessageDigest.getInstance("SHA-256")
        .digest("$first:$last".toByteArray())
        .joinToString("") { "%02x".format(it) }
        .take(32)

    companion object {
        private const val TAG = "PLOT-Summary"
        const val PERIODIC_WORK = "recall-periodic-summary"
        const val DEBOUNCED_WORK = "recall-debounced-summary"
        private const val SESSION_GAP_MS = 10 * 60_000L
        private const val MAX_BATCHES_PER_RUN = 10
    }
}
