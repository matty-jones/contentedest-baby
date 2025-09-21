package com.contentedest.baby.data.repo

import com.contentedest.baby.data.local.*
import com.contentedest.baby.net.EventDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.StringWriter
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

class EventRepository(
    private val eventsDao: EventsDao,
    private val syncStateDao: SyncStateDao
) {
    suspend fun startSleep(nowUtc: Long, deviceId: String, note: String? = null): String = withContext(Dispatchers.IO) {
        val id = UUID.randomUUID().toString()
        val event = EventEntity(
            event_id = id,
            device_id = deviceId,
            created_ts = nowUtc,
            updated_ts = nowUtc,
            version = 1,
            deleted = false,
            type = EventType.sleep,
            start_ts = nowUtc,
            end_ts = null,
            ts = null,
            note = note
        )
        eventsDao.upsertEvent(event)
        id
    }

    suspend fun stopSleep(eventId: String, endUtc: Long) = withContext(Dispatchers.IO) {
        eventsDao.completeSleep(eventId, endUtc)
    }

    suspend fun logNappy(nowUtc: Long, deviceId: String, type: String, note: String?): String = withContext(Dispatchers.IO) {
        val id = UUID.randomUUID().toString()
        val event = EventEntity(
            event_id = id,
            device_id = deviceId,
            created_ts = nowUtc,
            updated_ts = nowUtc,
            version = 1,
            deleted = false,
            type = EventType.nappy,
            ts = nowUtc,
            nappy_type = type,
            note = note
        )
        eventsDao.upsertEvent(event)
        id
    }

    suspend fun startBreastFeed(nowUtc: Long, deviceId: String, side: BreastSide): String = withContext(Dispatchers.IO) {
        val id = UUID.randomUUID().toString()
        val event = EventEntity(
            event_id = id,
            device_id = deviceId,
            created_ts = nowUtc,
            updated_ts = nowUtc,
            version = 1,
            deleted = false,
            type = EventType.feed,
            feed_mode = FeedMode.breast,
            ts = null
        )
        eventsDao.upsertEvent(event)
        eventsDao.upsertSegments(listOf(FeedSegmentEntity(event_id = id, side = side, start_ts = nowUtc, end_ts = nowUtc)))
        id
    }

    suspend fun swapBreastSide(eventId: String, swapUtc: Long, newSide: BreastSide) = withContext(Dispatchers.IO) {
        val segments = eventsDao.feedSegments(eventId)
        val last = segments.lastOrNull()
        if (last != null && last.end_ts == last.start_ts) {
            // close last and open new
            val closed = last.copy(end_ts = swapUtc)
            val opened = FeedSegmentEntity(event_id = eventId, side = newSide, start_ts = swapUtc, end_ts = swapUtc)
            eventsDao.upsertSegments(listOf(closed, opened))
        } else {
            eventsDao.upsertSegments(listOf(FeedSegmentEntity(event_id = eventId, side = newSide, start_ts = swapUtc, end_ts = swapUtc)))
        }
    }

    suspend fun eventsForDay(dayStartUtc: Long, dayEndUtc: Long): List<EventEntity> = withContext(Dispatchers.IO) {
        eventsDao.eventsInRange(dayStartUtc, dayEndUtc)
    }

    // Sync functionality
    suspend fun getLastServerClock(): Long = withContext(Dispatchers.IO) {
        syncStateDao.get()?.last_server_clock ?: 0L
    }

    suspend fun updateServerClock(clock: Long) = withContext(Dispatchers.IO) {
        syncStateDao.updateClock(clock)
    }

    suspend fun ensureSyncState(deviceId: String) = withContext(Dispatchers.IO) {
        val state = syncStateDao.get()
        if (state == null) {
            syncStateDao.upsert(SyncStateEntity(device_id = deviceId, paired = true))
        } else if (state.device_id != deviceId) {
            syncStateDao.upsert(state.copy(device_id = deviceId, paired = true))
        }
    }

    // Convert EventEntity to EventDto for API
    private fun EventEntity.toDto(): EventDto {
        return EventDto(
            event_id = event_id,
            type = type.name,
            payload = when (type) {
                EventType.feed -> mapOf("mode" to feed_mode?.name, "bottle_amount_ml" to bottle_amount_ml, "solids_amount" to solids_amount)
                EventType.nappy -> mapOf("nappy_type" to nappy_type)
                else -> null
            },
            start_ts = start_ts,
            end_ts = end_ts,
            ts = ts,
            created_ts = created_ts,
            updated_ts = updated_ts,
            version = version,
            deleted = deleted,
            device_id = device_id
        )
    }

    // Convert EventDto to EventEntity for local storage
    private fun EventDto.toEntity(): EventEntity {
        val feedMode = payload?.get("mode")?.toString()?.let { FeedMode.valueOf(it) }
        val bottleAmount = payload?.get("bottle_amount_ml") as? Int
        val solidsAmount = payload?.get("solids_amount") as? Int
        val nappyType = payload?.get("nappy_type")?.toString()

        return EventEntity(
            event_id = event_id,
            type = EventType.valueOf(type),
            payload = payload,
            start_ts = start_ts,
            end_ts = end_ts,
            ts = ts,
            created_ts = created_ts,
            updated_ts = updated_ts,
            version = version,
            deleted = deleted,
            device_id = device_id,
            feed_mode = feedMode,
            bottle_amount_ml = bottleAmount,
            solids_amount = solidsAmount,
            nappy_type = nappyType
        )
    }

    // Export functionality
    suspend fun exportToCsv(): String = withContext(Dispatchers.IO) {
        val allEvents = eventsDao.eventsInRange(0L, Long.MAX_VALUE)
        val writer = StringWriter()

        // CSV Header
        writer.append("event_id,type,start_ts,end_ts,ts,created_ts,updated_ts,version,deleted,device_id,note,feed_mode,bottle_amount_ml,solids_amount,nappy_type\n")

        // CSV Rows
        for (event in allEvents) {
            writer.append("${event.event_id},${event.type.name},${event.start_ts ?: ""},${event.end_ts ?: ""},${event.ts ?: ""},${event.created_ts},${event.updated_ts},${event.version},${event.deleted},${event.device_id},${event.note ?: ""},${event.feed_mode?.name ?: ""},${event.bottle_amount_ml ?: ""},${event.solids_amount ?: ""},${event.nappy_type ?: ""}\n")
        }

        writer.toString()
    }

    suspend fun exportToJson(): String = withContext(Dispatchers.IO) {
        val allEvents = eventsDao.eventsInRange(0L, Long.MAX_VALUE)
        val eventsForJson = allEvents.map { event ->
            mapOf(
                "event_id" to event.event_id,
                "type" to event.type.name,
                "start_ts" to event.start_ts,
                "end_ts" to event.end_ts,
                "ts" to event.ts,
                "created_ts" to event.created_ts,
                "updated_ts" to event.updated_ts,
                "version" to event.version,
                "deleted" to event.deleted,
                "device_id" to event.device_id,
                "note" to event.note,
                "feed_mode" to event.feed_mode?.name,
                "bottle_amount_ml" to event.bottle_amount_ml,
                "solids_amount" to event.solids_amount,
                "nappy_type" to event.nappy_type
            )
        }

        kotlinx.serialization.json.Json.encodeToString(mapOf("events" to eventsForJson))
    }
}


