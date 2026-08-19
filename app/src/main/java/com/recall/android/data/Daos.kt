package com.recall.android.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface InteractionEventDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(event: InteractionEventEntity)

    @Query("SELECT * FROM interaction_events WHERE processed = 0 AND occurredAt <= :before ORDER BY occurredAt ASC LIMIT :limit")
    suspend fun unprocessed(before: Long, limit: Int = 1_000): List<InteractionEventEntity>

    @Query("UPDATE interaction_events SET processed = 1 WHERE id IN (:ids)")
    suspend fun markProcessed(ids: List<String>)

    @Query("UPDATE interaction_events SET processed = 0")
    suspend fun markAllUnprocessed()

    @Query("DELETE FROM interaction_events WHERE occurredAt < :before")
    suspend fun deleteOlderThan(before: Long): Int

    @Query("DELETE FROM interaction_events")
    suspend fun deleteAll()

    @Query("UPDATE interaction_events SET occurredAt = occurredAt + :bootEpochOffset WHERE occurredAt < :legacyCutoff")
    suspend fun repairLegacyTimestamps(bootEpochOffset: Long, legacyCutoff: Long): Int

    @Query("SELECT COUNT(*) FROM interaction_events WHERE occurredAt >= :since AND kind != 'calendar_event'")
    fun observeCountSince(since: Long): Flow<Int>

    @Query("SELECT * FROM interaction_events WHERE kind != 'calendar_event' ORDER BY occurredAt DESC LIMIT :limit")
    fun observeRecent(limit: Int = 12): Flow<List<InteractionEventEntity>>

    @Query("SELECT * FROM interaction_events ORDER BY rowid DESC LIMIT :limit")
    suspend fun latest(limit: Int = 2_000): List<InteractionEventEntity>
}

@Dao
interface MemoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(memory: MemoryEntity)

    @Query("SELECT * FROM memories WHERE source NOT LIKE 'rollup_%' AND source != 'mac_10min' ORDER BY startedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int = 200): Flow<List<MemoryEntity>>

    @Query("SELECT * FROM memories ORDER BY startedAt DESC LIMIT :limit")
    suspend fun latest(limit: Int = 2_000): List<MemoryEntity>

    @Query("SELECT * FROM memories WHERE startedAt >= :start AND startedAt < :end ORDER BY startedAt ASC")
    suspend fun between(start: Long, end: Long): List<MemoryEntity>

    @Query("DELETE FROM memories WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM memories")
    suspend fun deleteAll()

    @Query("DELETE FROM memories WHERE source IN (:sources)")
    suspend fun deleteBySources(sources: List<String>)

    @Query("DELETE FROM memories WHERE source IN (:sources) AND startedAt >= :start AND startedAt < :end")
    suspend fun deleteBySourcesBetween(sources: List<String>, start: Long, end: Long)

    @Query("UPDATE memories SET startedAt = startedAt + :bootEpochOffset, endedAt = endedAt + :bootEpochOffset WHERE startedAt < :legacyCutoff")
    suspend fun repairLegacyTimestamps(bootEpochOffset: Long, legacyCutoff: Long): Int
}
