package com.contentedest.baby.domain

import java.time.*

enum class SleepClass { Nap, Night }

/**
 * Baby day: 07:00 local time on [date] through 07:00 on [date]+1 (exclusive end).
 * Matches [TimelineViewModel.load] and [QuickStatsBar].
 */
object TimeRules {
    // Default: start between 19:00–07:00 and duration ≥ 2h => Night; else Nap
    fun classifySleep(startUtc: Long, endUtc: Long, zone: ZoneId = ZoneId.systemDefault()): SleepClass {
        val start = Instant.ofEpochSecond(startUtc).atZone(zone)
        val end = Instant.ofEpochSecond(endUtc).atZone(zone)
        val duration = Duration.between(start, end)
        val hour = start.hour
        val inNightWindow = (hour >= 19 || hour < 7)
        val isLong = duration.toHours() >= 2
        return if (inNightWindow && isLong) SleepClass.Night else SleepClass.Nap
    }

    fun dayRangeEpochSeconds(date: LocalDate, zone: ZoneId = ZoneId.systemDefault()): LongRange {
        val start = date.atStartOfDay(zone).toEpochSecond()
        val end = date.plusDays(1).atStartOfDay(zone).minusSeconds(1).toEpochSecond()
        return start..end
    }

    /** Calendar date of the baby-day that contains [epochSeconds] (local clock). */
    fun babyLocalDateForInstant(epochSeconds: Long, zone: ZoneId = ZoneId.systemDefault()): LocalDate {
        val zdt = Instant.ofEpochSecond(epochSeconds).atZone(zone)
        return if (zdt.hour >= 7) zdt.toLocalDate() else zdt.toLocalDate().minusDays(1)
    }

    fun babyDayStartEpochSeconds(date: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Long =
        date.atTime(7, 0).atZone(zone).toEpochSecond()

    /** First instant not in the baby-day for [date] (next day 07:00 local). */
    fun babyDayEndExclusiveEpochSeconds(date: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Long =
        date.plusDays(1).atTime(7, 0).atZone(zone).toEpochSecond()

    /**
     * Half-open intervals [intervalStart, intervalEndExclusive) and [windowStart, windowEndExclusive).
     */
    fun intervalOverlapSeconds(
        intervalStart: Long,
        intervalEndExclusive: Long,
        windowStart: Long,
        windowEndExclusive: Long
    ): Long {
        if (intervalEndExclusive <= windowStart || intervalStart >= windowEndExclusive) return 0L
        val s = maxOf(intervalStart, windowStart)
        val e = minOf(intervalEndExclusive, windowEndExclusive)
        return maxOf(0L, e - s)
    }
}


