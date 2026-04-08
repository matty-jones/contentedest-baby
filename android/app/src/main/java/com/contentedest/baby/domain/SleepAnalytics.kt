package com.contentedest.baby.domain

import com.contentedest.baby.data.local.EventEntity
import com.contentedest.baby.data.local.EventType
import java.time.LocalDate
import java.time.ZoneId

/** Max gap between sleep rows to count as one session (classifier fragmentation). */
const val SLEEP_SESSION_MERGE_GAP_SECONDS: Long = 600L

object SleepAnalytics {

    /**
     * Merge overlapping or nearly-adjacent sleep intervals (same semantics as session length).
     * Returns half-open merged intervals [start, endExclusive).
     */
    fun mergeSleepIntervals(
        events: List<EventEntity>,
        maxGapSeconds: Long = SLEEP_SESSION_MERGE_GAP_SECONDS,
        nowEpoch: Long
    ): List<Pair<Long, Long>> {
        val intervals = events
            .asSequence()
            .filter { it.type == EventType.sleep && it.start_ts != null }
            .map { e ->
                val s = e.start_ts!!
                val eEx = e.end_ts ?: nowEpoch
                s to maxOf(eEx, s)
            }
            .sortedBy { it.first }
            .toList()
        if (intervals.isEmpty()) return emptyList()
        val merged = mutableListOf<Pair<Long, Long>>()
        var curS = intervals[0].first
        var curE = intervals[0].second
        for (i in 1 until intervals.size) {
            val (s, e) = intervals[i]
            if (s - curE <= maxGapSeconds) {
                curE = maxOf(curE, e)
            } else {
                merged.add(curS to curE)
                curS = s
                curE = e
            }
        }
        merged.add(curS to curE)
        return merged
    }

    fun intervalOverlapsWindow(
        intervalStart: Long,
        intervalEndExclusive: Long,
        windowStart: Long,
        windowEndExclusive: Long
    ): Boolean =
        intervalStart < windowEndExclusive && intervalEndExclusive > windowStart

    /**
     * Total sleep seconds overlapping the baby-day for [babyDayLabel] (window starting that date 07:00).
     */
    fun sleepSecondsOverlappingBabyDay(
        events: List<EventEntity>,
        babyDayLabel: LocalDate,
        nowEpoch: Long,
        zone: ZoneId = ZoneId.systemDefault()
    ): Long {
        val ws = TimeRules.babyDayStartEpochSeconds(babyDayLabel, zone)
        val we = TimeRules.babyDayEndExclusiveEpochSeconds(babyDayLabel, zone)
        var total = 0L
        for (e in events) {
            if (e.type != EventType.sleep || e.start_ts == null) continue
            val endEx = e.end_ts ?: nowEpoch
            total += TimeRules.intervalOverlapSeconds(e.start_ts, endEx, ws, we)
        }
        return total
    }

    /**
     * [rangeDays] consecutive baby days ending on [today] (inclusive): earliest = today minus (rangeDays - 1).
     */
    fun dailySleepTotalsHours(
        events: List<EventEntity>,
        rangeDays: Int,
        today: LocalDate,
        nowEpoch: Long,
        zone: ZoneId = ZoneId.systemDefault()
    ): List<Double> {
        return (0 until rangeDays).map { offset ->
            val babyDayLabel = today.minusDays((rangeDays - 1 - offset).toLong())
            sleepSecondsOverlappingBabyDay(events, babyDayLabel, nowEpoch, zone) / 3600.0
        }
    }

    fun mergedSleepSessionsOverlappingWindow(
        mergedIntervals: List<Pair<Long, Long>>,
        windowStart: Long,
        windowEndExclusive: Long
    ): Int =
        mergedIntervals.count { (s, e) ->
            intervalOverlapsWindow(s, e, windowStart, windowEndExclusive)
        }

    fun eventOverlapsBabyWindow(
        e: EventEntity,
        windowStart: Long,
        windowEndExclusive: Long,
        nowEpoch: Long
    ): Boolean {
        return when (e.type) {
            EventType.sleep -> {
                val s = e.start_ts ?: return false
                val endEx = e.end_ts ?: nowEpoch
                intervalOverlapsWindow(s, endEx, windowStart, windowEndExclusive)
            }
            EventType.feed -> {
                val s = e.start_ts ?: e.ts ?: return false
                val endEx = e.end_ts ?: e.ts ?: s
                intervalOverlapsWindow(s, endEx, windowStart, windowEndExclusive)
            }
            EventType.nappy -> {
                val t = e.ts ?: e.start_ts ?: return false
                t >= windowStart && t < windowEndExclusive
            }
        }
    }
}
