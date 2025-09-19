package com.contentedest.baby.domain

import com.contentedest.baby.data.local.EventEntity
import com.contentedest.baby.data.local.EventType

data class SleepStats(
    val totalSeconds: Long,
    val longestStretchSeconds: Long,
    val napSeconds: Long,
    val nightSeconds: Long
)

data class FeedStats(
    val feedCount: Int,
    val bottleMlTotal: Int,
    val solidsAmountTotal: Int
)

data class NappyStats(
    val count: Int
)

object StatsUseCases {
    fun computeSleepStats(events: List<EventEntity>): SleepStats {
        var total = 0L
        var longest = 0L
        var nap = 0L
        var night = 0L
        for (e in events) if (e.type == EventType.sleep && e.start_ts != null && e.end_ts != null) {
            val dur = e.end_ts - e.start_ts
            total += dur
            if (dur > longest) longest = dur
            when (TimeRules.classifySleep(e.start_ts, e.end_ts)) {
                SleepClass.Nap -> nap += dur
                SleepClass.Night -> night += dur
            }
        }
        return SleepStats(total, longest, nap, night)
    }

    fun computeFeedStats(events: List<EventEntity>): FeedStats {
        var count = 0
        var bottle = 0
        var solids = 0
        for (e in events) if (e.type == EventType.feed) {
            count += 1
            bottle += e.bottle_amount_ml ?: 0
            solids += e.solids_amount ?: 0
        }
        return FeedStats(count, bottle, solids)
    }

    fun computeNappyStats(events: List<EventEntity>): NappyStats {
        val count = events.count { it.type == EventType.nappy }
        return NappyStats(count)
    }
}


