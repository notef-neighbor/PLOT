package com.recall.android.data

import android.content.Context
import android.os.SystemClock
import androidx.room.withTransaction
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.recall.android.crypto.VaultCipher
import com.recall.android.worker.SummarizeWorker
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class HistoryRepository(
    private val context: Context,
    private val database: HistoryDatabase,
    private val cipher: VaultCipher,
) {
    fun observeMemories(): Flow<List<HistoryMemory>> = database.memoryDao().observeRecent().map { entities ->
        entities.mapNotNull(::decryptMemorySafely)
    }.flowOn(Dispatchers.Default)

    fun observeTodayEventCount(): Flow<Int> {
        val startOfDay = java.time.LocalDate.now()
            .atStartOfDay(java.time.ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
        return database.eventDao().observeCountSince(startOfDay)
    }

    fun observeRecentActivities(): Flow<List<RecentActivity>> = database.eventDao().observeRecent().map { entities ->
        entities.mapNotNull { entity ->
            runCatching {
                val payload = PayloadCodec.decodeEvent(cipher.decrypt(entity.encryptedPayload))
                RecentActivity(
                    id = entity.id,
                    occurredAt = entity.occurredAt,
                    kind = entity.kind,
                    applicationLabel = payload.applicationLabel,
                    preview = payload.text?.take(240),
                )
            }.getOrNull()
        }
    }.flowOn(Dispatchers.Default)

    suspend fun record(interaction: CapturedInteraction, stableKey: String? = null) {
        val payload = EventPayload(
            packageName = interaction.packageName,
            applicationLabel = interaction.applicationLabel,
            className = interaction.className,
            text = interaction.text,
            viewId = interaction.viewId,
            contentRedacted = interaction.contentRedacted,
        )
        database.eventDao().insert(
            InteractionEventEntity(
                id = stableKey?.let { UUID.nameUUIDFromBytes(it.toByteArray()).toString() }
                    ?: UUID.randomUUID().toString(),
                occurredAt = interaction.occurredAt,
                kind = interaction.kind,
                sourceHash = cipher.sourceHash(interaction.packageName),
                encryptedPayload = cipher.encrypt(PayloadCodec.encodeEvent(payload)),
            ),
        )
        scheduleSummary()
    }

    suspend fun search(query: String): List<HistoryMemory> = withContext(Dispatchers.Default) {
        val terms = HistorySearchPolicy.terms(query)
        val allSummaries = database.memoryDao().latest().mapNotNull(::decryptMemorySafely)
        val scoredSummaries = allSummaries.mapNotNull { memory ->
            val searchable = buildString {
                append(memory.title).append(' ')
                append(memory.summary).append(' ')
                append(memory.applications.joinToString(" ")).append(' ')
                append(memory.keywords.joinToString(" "))
            }
            HistorySearchPolicy.score(searchable, terms)
                .takeIf { it > 0 }
                ?.let { score -> ScoredMemory(memory, score) }
        }

        val allRawEvidence = database.eventDao().latest().mapNotNull { entity ->
            val payload = runCatching {
                PayloadCodec.decodeEvent(cipher.decrypt(entity.encryptedPayload))
            }.getOrNull() ?: return@mapNotNull null
            val evidence = payload.text?.let(HistoryEvidenceFormatter::clean)?.takeIf(String::isNotBlank)
                ?: return@mapNotNull null
            HistoryMemory(
                id = "event:${entity.id}",
                startedAt = entity.occurredAt,
                endedAt = entity.occurredAt,
                title = context.getString(com.recall.android.R.string.history_raw_title, payload.applicationLabel),
                summary = evidence,
                applications = listOf(payload.applicationLabel),
                keywords = terms,
                eventCount = 1,
                source = "raw_event",
            )
        }

        val scoredRawEvidence = allRawEvidence.mapNotNull { memory ->
            val searchable = "${memory.title} ${memory.summary} ${memory.applications.joinToString(" ")}"
            HistorySearchPolicy.score(searchable, terms)
                .takeIf { it > 0 }
                ?.let { score -> ScoredMemory(memory, score + 1) }
        }

        val relevant = (scoredRawEvidence + scoredSummaries)
            .sortedWith(compareByDescending<ScoredMemory> { it.score }.thenByDescending { it.memory.startedAt })
            .map(ScoredMemory::memory)
        val candidates = if (relevant.isNotEmpty()) {
            relevant
        } else if (HistorySearchPolicy.isOverviewQuery(query)) {
            (allRawEvidence.take(8) + allSummaries.take(8)).sortedByDescending(HistoryMemory::startedAt)
        } else {
            emptyList()
        }

        candidates
            .distinctBy { it.id }
            .distinctBy { HistoryEvidenceFormatter.clean(it.summary).lowercase() }
            .take(MAX_SEARCH_RESULTS)
    }

    suspend fun deleteMemory(id: String) = database.memoryDao().delete(id)

    suspend fun memoriesBetween(start: Long, end: Long): List<HistoryMemory> =
        database.memoryDao().between(start, end).mapNotNull(::decryptMemorySafely)

    suspend fun saveMemory(memory: HistoryMemory) {
        database.memoryDao().insert(
            memory.toEntity(),
        )
    }

    suspend fun replaceDerivedMemories(
        sources: List<String>,
        start: Long,
        end: Long,
        memories: List<HistoryMemory>,
    ) {
        val entities = withContext(Dispatchers.Default) { memories.map { it.toEntity() } }
        database.withTransaction {
            database.memoryDao().deleteBySourcesBetween(sources, start, end)
            entities.forEach { database.memoryDao().insert(it) }
        }
    }

    suspend fun clearAll() {
        database.eventDao().deleteAll()
        database.memoryDao().deleteAll()
    }

    suspend fun repairLegacyTimestamps() {
        val bootEpochOffset = System.currentTimeMillis() - SystemClock.uptimeMillis()
        database.withTransaction {
            database.eventDao().repairLegacyTimestamps(bootEpochOffset, LEGACY_TIMESTAMP_CUTOFF)
            database.memoryDao().repairLegacyTimestamps(bootEpochOffset, LEGACY_TIMESTAMP_CUTOFF)
        }
    }

    suspend fun prepareDerivedHistory() {
        val preferences = context.getSharedPreferences(MAINTENANCE_PREFERENCES, Context.MODE_PRIVATE)
        if (preferences.getInt(KEY_SUMMARY_FORMAT_VERSION, 0) >= SUMMARY_FORMAT_VERSION) return
        database.withTransaction {
            database.memoryDao().deleteBySources(listOf("local", "gateway"))
            database.eventDao().markAllUnprocessed()
        }
        preferences.edit().putInt(KEY_SUMMARY_FORMAT_VERSION, SUMMARY_FORMAT_VERSION).commit()
    }

    private fun decryptMemorySafely(entity: MemoryEntity): HistoryMemory? = runCatching {
        PayloadCodec.decodeMemory(entity, cipher.decrypt(entity.encryptedPayload))
    }.getOrNull()

    private fun HistoryMemory.toEntity(): MemoryEntity = MemoryEntity(
        id = id,
        startedAt = startedAt,
        endedAt = endedAt,
        encryptedPayload = cipher.encrypt(PayloadCodec.encodeMemory(this)),
        eventCount = eventCount,
        source = source,
    )

    private fun scheduleSummary() {
        val work = OneTimeWorkRequestBuilder<SummarizeWorker>()
            .setInitialDelay(10, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            SummarizeWorker.DEBOUNCED_WORK,
            ExistingWorkPolicy.KEEP,
            work,
        )
    }

    companion object {
        private const val LEGACY_TIMESTAMP_CUTOFF = 1_577_836_800_000L // 2020-01-01 UTC
        private const val MAX_SEARCH_RESULTS = 8
        private const val MAINTENANCE_PREFERENCES = "plot-history-maintenance"
        private const val KEY_SUMMARY_FORMAT_VERSION = "summary-format-version"
        private const val SUMMARY_FORMAT_VERSION = 2
    }

    private data class ScoredMemory(val memory: HistoryMemory, val score: Int)
}
