package com.contentedest.baby

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.contentedest.baby.data.local.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RoomBasicsTest {
    @Test
    fun insertAndQuerySleepEvent() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        val dao = db.eventsDao()

        val event = EventEntity(
            event_id = "e1",
            device_id = "dev1",
            created_ts = 1000,
            updated_ts = 1000,
            version = 1,
            deleted = false,
            type = EventType.sleep,
            start_ts = 1000,
            end_ts = 2000,
            ts = null,
            note = "nap"
        )

        kotlinx.coroutines.runBlocking {
            dao.upsertEvent(event)
            val result = dao.eventsInRange(0, 3000)
            assertEquals(1, result.size)
            assertEquals("e1", result[0].event_id)
            assertTrue(result[0].start_ts != null)
        }

        db.close()
    }
}


