package com.contentedest.baby

import com.contentedest.baby.data.local.EventEntity
import com.contentedest.baby.data.local.EventType
import com.contentedest.baby.domain.SleepAnalytics
import com.contentedest.baby.domain.TimeRules
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class SleepAnalyticsTest {

    @Test
    fun babyLocalDateForInstant_beforeSevenBelongsToPreviousCalendarDay() {
        val zone = ZoneId.of("UTC")
        val t = LocalDateTime.of(2024, 6, 10, 5, 0).atZone(zone).toEpochSecond()
        assertEquals(LocalDate.of(2024, 6, 9), TimeRules.babyLocalDateForInstant(t, zone))
    }

    @Test
    fun babyLocalDateForInstant_atSevenBelongsToSameCalendarDay() {
        val zone = ZoneId.of("UTC")
        val t = LocalDateTime.of(2024, 6, 10, 7, 0).atZone(zone).toEpochSecond()
        assertEquals(LocalDate.of(2024, 6, 10), TimeRules.babyLocalDateForInstant(t, zone))
    }

    @Test
    fun intervalOverlapSeconds_halfOpen() {
        assertEquals(1800L, TimeRules.intervalOverlapSeconds(0L, 3600L, 1800L, 7200L))
        assertEquals(0L, TimeRules.intervalOverlapSeconds(0L, 100L, 200L, 300L))
    }

    @Test
    fun sleepSplitAcrossTwoBabyDays() {
        val zone = ZoneId.of("UTC")
        val now = LocalDateTime.of(2024, 6, 11, 12, 0).atZone(zone).toEpochSecond()
        val start = LocalDateTime.of(2024, 6, 10, 6, 0).atZone(zone).toEpochSecond()
        val end = LocalDateTime.of(2024, 6, 10, 8, 0).atZone(zone).toEpochSecond()
        val ev = EventEntity(
            event_id = "a",
            device_id = "d",
            created_ts = 0,
            updated_ts = 0,
            version = 1,
            deleted = false,
            type = EventType.sleep,
            start_ts = start,
            end_ts = end
        )
        val day9 = LocalDate.of(2024, 6, 9)
        val day10 = LocalDate.of(2024, 6, 10)
        val s9 = SleepAnalytics.sleepSecondsOverlappingBabyDay(listOf(ev), day9, now, zone)
        val s10 = SleepAnalytics.sleepSecondsOverlappingBabyDay(listOf(ev), day10, now, zone)
        assertEquals(3600L, s9)
        assertEquals(3600L, s10)
    }

    @Test
    fun mergeSleepIntervals_adjacentWithinGap() {
        val zone = ZoneId.of("UTC")
        val now = LocalDateTime.of(2024, 1, 1, 12, 0).atZone(zone).toEpochSecond()
        val e1 = EventEntity(
            event_id = "a", device_id = "d", created_ts = 0, updated_ts = 0, version = 1,
            deleted = false, type = EventType.sleep, start_ts = 1000L, end_ts = 2000L
        )
        val e2 = EventEntity(
            event_id = "b", device_id = "d", created_ts = 0, updated_ts = 0, version = 1,
            deleted = false, type = EventType.sleep, start_ts = 2400L, end_ts = 3000L
        )
        val merged = SleepAnalytics.mergeSleepIntervals(listOf(e1, e2), nowEpoch = now)
        assertEquals(1, merged.size)
        assertEquals(1000L, merged[0].first)
        assertEquals(3000L, merged[0].second)
    }
}
