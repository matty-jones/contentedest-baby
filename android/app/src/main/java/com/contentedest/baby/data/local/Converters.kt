package com.contentedest.baby.data.local

import androidx.room.TypeConverter

enum class EventType { sleep, feed, nappy }
enum class FeedMode { breast, bottle, solids }
enum class BreastSide { left, right }
enum class GrowthCategory { weight, height, head }

class Converters {
    @TypeConverter
    fun fromString(value: String?): String? {
        // Simple pass-through for String
        return value
    }

    @TypeConverter
    fun fromEventType(value: EventType?): String? = value?.name

    @TypeConverter
    fun toEventType(value: String?): EventType? = value?.let { EventType.valueOf(it) }

    @TypeConverter
    fun fromFeedMode(value: FeedMode?): String? = value?.name

    @TypeConverter
    fun toFeedMode(value: String?): FeedMode? = value?.let { FeedMode.valueOf(it) }

    @TypeConverter
    fun fromBreastSide(value: BreastSide?): String? = value?.name

    @TypeConverter
    fun toBreastSide(value: String?): BreastSide? = value?.let { BreastSide.valueOf(it) }

    @TypeConverter
    fun fromGrowthCategory(value: GrowthCategory?): String? = value?.name

    @TypeConverter
    fun toGrowthCategory(value: String?): GrowthCategory? = value?.let { GrowthCategory.valueOf(it) }
}


