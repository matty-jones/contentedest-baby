package com.contentedest.baby.data.repo

import com.contentedest.baby.data.local.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

class EventRepository(private val eventsDao: EventsDao) {
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
}


