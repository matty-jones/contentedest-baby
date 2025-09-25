package com.contentedest.baby.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "events",
    indices = [
        Index(value = ["start_ts"]),
        Index(value = ["end_ts"]),
        Index(value = ["ts"])
    ]
)
data class EventEntity(
    @PrimaryKey val event_id: String,
    val device_id: String,
    val created_ts: Long,
    val updated_ts: Long,
    val version: Int,
    val deleted: Boolean = false,
    val type: EventType,

    // Sleep
    val start_ts: Long? = null,
    val end_ts: Long? = null,

    // Single-point events
    val ts: Long? = null,

    // Feeding specifics
    val feed_mode: FeedMode? = null,
    val bottle_amount_ml: Int? = null,
    val solids_amount: Int? = null,
    val duration_s: Int? = null,

    // Nappy specifics
    val nappy_type: String? = null,

    // Optional note (FTS could be added later)
    val note: String? = null,

    // Raw payload for extensibility
    val payload: String? = null
)

@Entity(
    tableName = "feed_segments",
    foreignKeys = [
        ForeignKey(
            entity = EventEntity::class,
            parentColumns = ["event_id"],
            childColumns = ["event_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("event_id"), Index("start_ts"), Index("end_ts")]
)
data class FeedSegmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val event_id: String,
    val side: BreastSide,
    val start_ts: Long,
    val end_ts: Long
)

@Entity(tableName = "sync_state")
data class SyncStateEntity(
    @PrimaryKey val id: Int = 1,
    val last_server_clock: Long = 0,
    val device_id: String? = null,
    val paired: Boolean = false
)

@Entity(tableName = "settings")
data class SettingsEntity(
    @PrimaryKey val id: Int = 1,
    val infant_name: String? = null,
    val dob_epoch_days: Int? = null,
    val trusted_ssids_csv: String? = null,
    val server_host: String? = null
)


