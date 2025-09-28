package com.contentedest.baby.data.repo

import com.contentedest.baby.data.local.*
import com.contentedest.baby.net.EventDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.squareup.moshi.Moshi
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Types
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

    suspend fun createSleep(nowUtc: Long, deviceId: String, note: String? = null): String = withContext(Dispatchers.IO) {
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

    suspend fun startFeed(nowUtc: Long, deviceId: String, mode: com.contentedest.baby.data.local.FeedMode, note: String? = null): String = withContext(Dispatchers.IO) {
        val id = UUID.randomUUID().toString()
        val event = EventEntity(
            event_id = id,
            device_id = deviceId,
            created_ts = nowUtc,
            updated_ts = nowUtc,
            version = 1,
            deleted = false,
            type = EventType.feed,
            feed_mode = mode,
            ts = null,
            note = note
        )
        eventsDao.upsertEvent(event)
        eventsDao.upsertSegments(listOf(com.contentedest.baby.data.local.FeedSegmentEntity(event_id = id, side = com.contentedest.baby.data.local.BreastSide.left, start_ts = nowUtc, end_ts = nowUtc)))
        id
    }

    suspend fun createNappy(nowUtc: Long, deviceId: String, type: String, note: String? = null): String = withContext(Dispatchers.IO) {
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

    suspend fun saveServerEvents(events: List<EventDto>) = withContext(Dispatchers.IO) {
        android.util.Log.d("EventRepository", "Saving ${events.size} server events to local database")
        events.forEach { eventDto ->
            val entity = eventDto.toEntity()
            eventsDao.upsertEvent(entity)
        }
        android.util.Log.d("EventRepository", "Successfully saved ${events.size} events to local database")
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
        val events = eventsDao.eventsInRange(dayStartUtc, dayEndUtc)
        android.util.Log.d("EventRepository", "Loaded ${events.size} events for day range $dayStartUtc to $dayEndUtc")
        events
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
        val payloadMap: Map<String, Any>? = when (type) {
            EventType.feed -> {
                val map = mutableMapOf<String, Any>()
                feed_mode?.name?.let { map["mode"] = it }
                bottle_amount_ml?.let { map["bottle_amount_ml"] = it }
                solids_amount?.let { map["solids_amount"] = it }
                map.takeIf { it.isNotEmpty() }
            }
            EventType.nappy -> {
                val map = mutableMapOf<String, Any>()
                nappy_type?.let { map["nappy_type"] = it }
                map.takeIf { it.isNotEmpty() }
            }
            else -> null
        }

        return EventDto(
            eventId = event_id,
            type = type.name,
            payload = payloadMap,
            startTs = start_ts,
            endTs = end_ts,
            ts = ts,
            createdTs = created_ts,
            updatedTs = updated_ts,
            version = version,
            deleted = deleted,
            deviceId = device_id
        )
    }

    // Convert EventDto to EventEntity for local storage
    private fun EventDto.toEntity(): EventEntity {
        // Parse payload Map<String, Any> to extract fields
        val payloadMap = payload ?: emptyMap()

        // Extract values from payload map - handle both new format and legacy server format
        val feedMode = try {
            // Try new format first
            (payloadMap["mode"] as? String)?.let { FeedMode.valueOf(it) }
                ?: // Try legacy server format - extract from details
                (payloadMap["details"] as? String)?.let { details ->
                    when {
                        details.contains("L&R") -> FeedMode.breast
                        details.contains("Bottle") -> FeedMode.bottle
                        details.contains("Solids") -> FeedMode.solids
                        else -> null
                    }
                }
        } catch (e: Exception) { null }

        val bottleAmount = try {
            when (val amount = payloadMap["bottle_amount_ml"]) {
                is Number -> amount.toInt()
                is String -> amount.toIntOrNull()
                else -> null
            }
        } catch (e: Exception) { null }

        val solidsAmount = try {
            when (val amount = payloadMap["solids_amount"]) {
                is Number -> amount.toInt()
                is String -> amount.toIntOrNull()
                else -> null
            }
        } catch (e: Exception) { null }

        val nappyType = try {
            // Try new format first
            (payloadMap["nappy_type"] as? String)
                ?: // Try legacy server format
                (payloadMap["details"] as? String)
        } catch (e: Exception) { null }

        // For feed events, use start_ts as the primary timestamp if ts is null
        val primaryTs = if (type == "feed" && ts == null) startTs else ts

        return EventEntity(
            event_id = eventId,
            type = EventType.valueOf(type),
            payload = payloadMap.takeIf { it.isNotEmpty() }?.toString(), // Convert Map to String
            start_ts = startTs,
            end_ts = endTs,
            ts = primaryTs,
            created_ts = createdTs,
            updated_ts = updatedTs,
            version = version,
            deleted = deleted,
            device_id = deviceId,
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

        val moshi = Moshi.Builder().build()
        val mapType = Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)
        val mapAdapter: JsonAdapter<Map<String, Any>> = moshi.adapter(mapType)
        val listType = Types.newParameterizedType(List::class.java, mapType)
        val listAdapter: JsonAdapter<List<Map<String, Any>>> = moshi.adapter(listType)

        val exportData = mapOf("events" to eventsForJson)
        mapAdapter.toJson(exportData)
    }
}


