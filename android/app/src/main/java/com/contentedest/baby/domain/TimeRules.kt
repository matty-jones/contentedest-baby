package com.contentedest.baby.domain

import java.time.*

enum class SleepClass { Nap, Night }

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
}


