package com.recall.android.worker

import android.content.Context
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

class SummarizeWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as RecallApplication).container
        val cutoff = System.currentTimeMillis()
        val events = container.database.eventDao().unprocessed(cutoff)
        if (events.isEmpty()) return Result.success()

        return runCatching {
            val groups = sessionize(events)
            val settings = container.settings.state.value
            val prepared = groups.map { group ->
                val decoded = group.map { event ->
                    event to PayloadCodec.decodeEvent(container.cipher.decrypt(event.encryptedPayload))
                }
                val localMemory = localSummary(decoded)
                val memory = if (settings.gatewayUrl.isNotBlank() && settings.deviceToken.isNotBlank()) {
                    runCatching {
                        val remote = container.gatewayClient.summarize(
                            baseUrl = settings.gatewayUrl,
                            token = settings.deviceToken,
                            sessionId = localMemory.id,
                            locale = applicationContext.resources.configuration.locales[0].toLanguageTag(),
                            events = decoded,
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
                group to memory
            }

            container.database.withTransaction {
                prepared.forEach { (group, memory) ->
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
                    container.database.eventDao().markProcessed(group.map { it.id })
                }
            }
        }.fold(
            onSuccess = { Result.success() },
            onFailure = { Result.retry() },
        )
    }

    private fun sessionize(events: List<InteractionEventEntity>): List<List<InteractionEventEntity>> {
        if (events.isEmpty()) return emptyList()
        val groups = mutableListOf<MutableList<InteractionEventEntity>>()
        events.forEach { event ->
            val current = groups.lastOrNull()
            if (current == null || event.occurredAt - current.last().occurredAt > SESSION_GAP_MS) {
                groups += mutableListOf(event)
            } else {
                current += event
            }
        }
        return groups
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
        const val PERIODIC_WORK = "recall-periodic-summary"
        const val DEBOUNCED_WORK = "recall-debounced-summary"
        private const val SESSION_GAP_MS = 10 * 60_000L
    }
}
