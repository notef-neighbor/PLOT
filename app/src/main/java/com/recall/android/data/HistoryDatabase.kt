package com.recall.android.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [InteractionEventEntity::class, MemoryEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class HistoryDatabase : RoomDatabase() {
    abstract fun eventDao(): InteractionEventDao
    abstract fun memoryDao(): MemoryDao
}
