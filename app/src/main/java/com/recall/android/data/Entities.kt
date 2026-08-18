package com.recall.android.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "interaction_events",
    indices = [Index("occurredAt"), Index("processed"), Index("sourceHash")],
)
data class InteractionEventEntity(
    @PrimaryKey val id: String,
    val occurredAt: Long,
    val kind: String,
    val sourceHash: String,
    val encryptedPayload: String,
    val processed: Boolean = false,
)

@Entity(
    tableName = "memories",
    indices = [Index("startedAt"), Index("endedAt")],
)
data class MemoryEntity(
    @PrimaryKey val id: String,
    val startedAt: Long,
    val endedAt: Long,
    val encryptedPayload: String,
    val eventCount: Int,
    val source: String,
)
