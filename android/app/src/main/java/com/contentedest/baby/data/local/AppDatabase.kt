package com.contentedest.baby.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [EventEntity::class, FeedSegmentEntity::class, SyncStateEntity::class, SettingsEntity::class],
    version = 2,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun eventsDao(): EventsDao
    abstract fun syncStateDao(): SyncStateDao
    abstract fun settingsDao(): SettingsDao
}


