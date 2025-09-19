package com.contentedest.baby

import com.contentedest.baby.data.local.EventEntity
import com.contentedest.baby.data.local.EventType
import com.contentedest.baby.domain.SleepClass
import com.contentedest.baby.domain.StatsUseCases
import com.contentedest.baby.domain.TimeRules
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId

class TimeRulesAndStatsTest {
    @Test
    fun classifySleepNightVsNap() {
        val zone = ZoneId.of("UTC")
        // Night: start 20:00, duration 3h
        val night = TimeRules.classifySleep(20 * 3600L, 23 * 3600L, zone)
        assertEquals(SleepClass.Night, night)
        // Nap: start 10:00, duration 1h
        val nap = TimeRules.classifySleep(10 * 3600L, 11 * 3600L, zone)
        assertEquals(SleepClass.Nap, nap)
    }

    @Test
    fun computeSleepStatsTotals() {
        val evs = listOf(
            EventEntity(
                event_id = "s1", device_id = "d", created_ts = 0, updated_ts = 0, version = 1,
                deleted = false, type = EventType.sleep, start_ts = 0, end_ts = 3600
            ),
            EventEntity(
                event_id = "s2", device_id = "d", created_ts = 0, updated_ts = 0, version = 1,
                deleted = false, type = EventType.sleep, start_ts = 7200, end_ts = 10800
            )
        )
        val stats = StatsUseCases.computeSleepStats(evs)
        assertEquals(7200L, stats.totalSeconds)
        assertEquals(3600L, stats.longestStretchSeconds)
    }
}


