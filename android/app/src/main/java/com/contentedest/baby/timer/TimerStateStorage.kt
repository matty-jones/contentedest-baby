package com.contentedest.baby.timer

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.contentedest.baby.data.local.BreastSide
import com.contentedest.baby.data.local.EventType

data class ActiveTimerState(
    val type: EventType,
    val startEpoch: Long,
    val endEpoch: Long,
    val running: Boolean,
    val details: String? = null, // for sleep
    val segments: List<Triple<BreastSide, Long, Long>> = emptyList(), // for feed
    val activeSide: BreastSide? = null, // for feed
    val currentStart: Long? = null // for feed
)

class TimerStateStorage(context: Context) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "tcb-timer-prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun saveActiveTimer(
        type: EventType,
        startEpoch: Long,
        endEpoch: Long,
        running: Boolean,
        details: String? = null,
        segments: List<Triple<BreastSide, Long, Long>> = emptyList(),
        activeSide: BreastSide? = null,
        currentStart: Long? = null
    ) {
        prefs.edit().apply {
            putString(KEY_TYPE, type.name)
            putLong(KEY_START_EPOCH, startEpoch)
            putLong(KEY_END_EPOCH, endEpoch)
            putBoolean(KEY_RUNNING, running)
            if (details != null) {
                putString(KEY_DETAILS, details)
            } else {
                remove(KEY_DETAILS)
            }
            
            // Store segments as comma-separated values: "side:start:end,side:start:end,..."
            if (segments.isNotEmpty()) {
                val segmentsString = segments.joinToString(",") { "${it.first.name}:${it.second}:${it.third}" }
                putString(KEY_SEGMENTS, segmentsString)
            } else {
                remove(KEY_SEGMENTS)
            }
            
            if (activeSide != null) {
                putString(KEY_ACTIVE_SIDE, activeSide.name)
            } else {
                remove(KEY_ACTIVE_SIDE)
            }
            
            if (currentStart != null) {
                putLong(KEY_CURRENT_START, currentStart)
            } else {
                remove(KEY_CURRENT_START)
            }
        }.apply()
    }

    fun getActiveTimer(): ActiveTimerState? {
        val typeString = prefs.getString(KEY_TYPE, null) ?: return null
        val type = try {
            EventType.valueOf(typeString)
        } catch (e: Exception) {
            return null
        }
        
        val startEpoch = prefs.getLong(KEY_START_EPOCH, -1)
        if (startEpoch == -1L) return null
        
        val endEpoch = prefs.getLong(KEY_END_EPOCH, -1)
        if (endEpoch == -1L) return null
        
        val running = prefs.getBoolean(KEY_RUNNING, false)
        val details = prefs.getString(KEY_DETAILS, null)
        
        // Parse segments
        val segmentsString = prefs.getString(KEY_SEGMENTS, null)
        val segments = if (segmentsString != null && segmentsString.isNotEmpty()) {
            segmentsString.split(",").mapNotNull { segmentStr ->
                val parts = segmentStr.split(":")
                if (parts.size == 3) {
                    try {
                        val side = BreastSide.valueOf(parts[0])
                        val start = parts[1].toLong()
                        val end = parts[2].toLong()
                        Triple(side, start, end)
                    } catch (e: Exception) {
                        null
                    }
                } else {
                    null
                }
            }
        } else {
            emptyList()
        }
        
        val activeSideString = prefs.getString(KEY_ACTIVE_SIDE, null)
        val activeSide = activeSideString?.let { 
            try { BreastSide.valueOf(it) } catch (e: Exception) { null }
        }
        
        val currentStart = if (prefs.contains(KEY_CURRENT_START)) {
            prefs.getLong(KEY_CURRENT_START, -1).takeIf { it != -1L }
        } else {
            null
        }
        
        return ActiveTimerState(
            type = type,
            startEpoch = startEpoch,
            endEpoch = endEpoch,
            running = running,
            details = details,
            segments = segments,
            activeSide = activeSide,
            currentStart = currentStart
        )
    }

    fun clearActiveTimer() {
        prefs.edit().apply {
            remove(KEY_TYPE)
            remove(KEY_START_EPOCH)
            remove(KEY_END_EPOCH)
            remove(KEY_RUNNING)
            remove(KEY_DETAILS)
            remove(KEY_SEGMENTS)
            remove(KEY_ACTIVE_SIDE)
            remove(KEY_CURRENT_START)
        }.apply()
    }

    fun updateEndTime(endEpoch: Long) {
        prefs.edit().putLong(KEY_END_EPOCH, endEpoch).apply()
    }

    fun hasActiveTimer(): Boolean {
        return prefs.contains(KEY_TYPE) && prefs.contains(KEY_START_EPOCH)
    }

    companion object {
        private const val KEY_TYPE = "timer_type"
        private const val KEY_START_EPOCH = "timer_start_epoch"
        private const val KEY_END_EPOCH = "timer_end_epoch"
        private const val KEY_RUNNING = "timer_running"
        private const val KEY_DETAILS = "timer_details"
        private const val KEY_SEGMENTS = "timer_segments"
        private const val KEY_ACTIVE_SIDE = "timer_active_side"
        private const val KEY_CURRENT_START = "timer_current_start"
    }
}

