package com.contentedest.baby.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update

@Dao
interface EventsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEvent(event: EventEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSegments(segments: List<FeedSegmentEntity>)

    @Query("SELECT * FROM events WHERE deleted = 0 AND ((start_ts NOT NULL AND end_ts NOT NULL) OR ts NOT NULL) AND ((start_ts BETWEEN :start AND :end) OR (ts BETWEEN :start AND :end)) ORDER BY COALESCE(start_ts, ts) ASC")
    suspend fun eventsInRange(start: Long, end: Long): List<EventEntity>

    @Query("SELECT * FROM feed_segments WHERE event_id = :eventId ORDER BY start_ts ASC")
    suspend fun feedSegments(eventId: String): List<FeedSegmentEntity>

    @Query("UPDATE events SET deleted = 1, updated_ts = :updatedTs, version = version + 1 WHERE event_id = :eventId")
    suspend fun softDelete(eventId: String, updatedTs: Long)

    @Query("UPDATE events SET end_ts = :endTs, updated_ts = :endTs, version = version + 1 WHERE event_id = :eventId")
    suspend fun completeSleep(eventId: String, endTs: Long)

    @Query("SELECT * FROM events WHERE deleted = 0 AND type = 'sleep' AND (start_ts NOT NULL OR end_ts NOT NULL) ORDER BY COALESCE(end_ts, start_ts) DESC LIMIT 1")
    suspend fun lastSleepEvent(): EventEntity?

    @Query("SELECT * FROM events WHERE deleted = 0 AND type = 'feed' AND (ts NOT NULL OR start_ts NOT NULL) ORDER BY COALESCE(ts, start_ts) DESC LIMIT 1")
    suspend fun lastFeedEvent(): EventEntity?

    @Query("SELECT * FROM events WHERE deleted = 0 AND type = 'nappy' AND ts NOT NULL ORDER BY ts DESC LIMIT 1")
    suspend fun lastNappyEvent(): EventEntity?

    @Query("SELECT * FROM events WHERE deleted = 0 ORDER BY created_ts ASC")
    suspend fun getAllEvents(): List<EventEntity>

    @Query("SELECT * FROM events WHERE event_id = :eventId")
    suspend fun getEventById(eventId: String): EventEntity?
}

@Dao
interface SyncStateDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(state: SyncStateEntity)

    @Query("SELECT * FROM sync_state WHERE id = 1")
    suspend fun get(): SyncStateEntity?

    @Query("UPDATE sync_state SET last_server_clock = :clock WHERE id = 1")
    suspend fun updateClock(clock: Long)
}

@Dao
interface SettingsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(settings: SettingsEntity)

    @Query("SELECT * FROM settings WHERE id = 1")
    suspend fun get(): SettingsEntity?
}

@Dao
interface GrowthDataDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(data: GrowthDataEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(data: List<GrowthDataEntity>)

    @Query("SELECT * FROM growth_data WHERE deleted = 0 AND category = :category ORDER BY ts ASC")
    suspend fun getByCategory(category: GrowthCategory): List<GrowthDataEntity>

    @Query("SELECT * FROM growth_data WHERE deleted = 0 ORDER BY ts ASC")
    suspend fun getAll(): List<GrowthDataEntity>

    @Query("SELECT * FROM growth_data WHERE id = :id")
    suspend fun getById(id: String): GrowthDataEntity?

    @Query("UPDATE growth_data SET deleted = 1, updated_ts = :updatedTs, version = version + 1 WHERE id = :id")
    suspend fun softDelete(id: String, updatedTs: Long)
}


