package com.contentedest.baby

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.contentedest.baby.data.local.*
import com.contentedest.baby.data.repo.EventRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EventRepositoryTest {
    @Test
    fun sleepStartStopAndQuery() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        val repo = EventRepository(db.eventsDao())
        val now = 1_700_000_000L
        val eventId = repo.startSleep(now, "dev1")
        repo.stopSleep(eventId, now + 3_600)

        val items = db.eventsDao().eventsInRange(now - 10_000, now + 10_000)
        assertEquals(1, items.size)
        assertTrue(items.first().start_ts != null)
        assertTrue(items.first().end_ts != null)

        db.close()
    }

    @Test
    fun breastFeedSegmentsSwap() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        val repo = EventRepository(db.eventsDao())
        val now = 1_700_000_000L
        val feedId = repo.startBreastFeed(now, "dev1", BreastSide.left)
        repo.swapBreastSide(feedId, now + 120, BreastSide.right)
        repo.swapBreastSide(feedId, now + 240, BreastSide.left)

        val segments = db.eventsDao().feedSegments(feedId)
        assertEquals(3, segments.size)
        // First segment should be left ending at first swap
        assertEquals(BreastSide.left, segments[0].side)
        assertEquals(now + 120, segments[0].end_ts)

        db.close()
    }
}


