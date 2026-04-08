package com.contentedest.baby

import com.contentedest.baby.data.local.EventEntity
import com.contentedest.baby.data.local.EventType
import com.contentedest.baby.ui.timeline.calculateEventStats
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class EventListStatsTest {

    @Test
    fun sleepMeanDailyTotals_overlapWindow() {
        val zone = ZoneId.of("UTC")
        val today = LocalDate.of(2024, 6, 12)
        val now = LocalDateTime.of(2024, 6, 12, 12, 0).atZone(zone).toEpochSecond()
        val start = LocalDateTime.of(2024, 6, 10, 10, 0).atZone(zone).toEpochSecond()
        val end = LocalDateTime.of(2024, 6, 10, 11, 0).atZone(zone).toEpochSecond()
        val ev = EventEntity(
            event_id = "s1",
            device_id = "d",
            created_ts = 0,
            updated_ts = 0,
            version = 1,
            deleted = false,
            type = EventType.sleep,
            start_ts = start,
            end_ts = end
        )
        val stats = calculateEventStats(
            events = listOf(ev),
            eventType = EventType.sleep,
            rangeDays = 7,
            today = today,
            zone = zone,
            nowEpoch = now
        )
        val meanH = 1.0 / 7.0
        assertTrue(stats.mean.startsWith(String.format("%.1f", meanH)))
        assertTrue(stats.median.contains("h"))
        assertTrue(stats.frequency.contains("/day"))
    }
}
