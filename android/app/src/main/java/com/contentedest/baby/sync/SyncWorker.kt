package com.contentedest.baby.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import androidx.work.ListenableWorker.Result as WorkerResult
import com.contentedest.baby.data.local.EventEntity
import com.contentedest.baby.data.repo.EventRepository
import com.contentedest.baby.data.repo.SyncRepository
import com.contentedest.baby.net.EventDto
import com.contentedest.baby.net.TokenStorage
import dagger.assisted.Assisted
// Removed import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

@HiltWorker // Added annotation
class SyncWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted private val params: WorkerParameters,
    private val eventRepository: EventRepository,
    private val syncRepository: SyncRepository,
    private val tokenStorage: TokenStorage
) : CoroutineWorker(appContext, params) {

    // Removed the @AssistedFactory interface Factory

    override suspend fun doWork(): ListenableWorker.Result = coroutineScope {
        try {
            val token = tokenStorage.getToken()
            if (token == null) {
                return@coroutineScope WorkerResult.failure()
            }

            // TODO: Add LAN reachability check here
            // For now, we'll assume we're connected if we have a token

            val deviceId = params.inputData.getString("device_id") ?: "unknown"

            // Ensure sync state is initialized
            eventRepository.ensureSyncState(deviceId)

            val lastClock = eventRepository.getLastServerClock()

            // Run push and pull concurrently
            val pushDeferred = async { syncRepository.syncPush(emptyList()) } // TODO: get pending events
            val pullResult = syncRepository.syncPull(lastClock)

            // Wait for push to complete
            pushDeferred.await()

            // Process pull results
            return@coroutineScope when (pullResult) {
                is com.contentedest.baby.data.repo.Result.Success -> {
                    val successData = pullResult.data
                    if (successData is Pair<*, *>) {
                        val newClock = successData.first as Long
                        val events = successData.second as List<EventDto>
                        eventRepository.updateServerClock(newClock)

                        // Apply server events to local DB
                        events.forEach { eventDto: EventDto ->
                            val entity = eventDto.toEntity()
                            // TODO: Handle conflicts based on version/updated_ts/device_id
                            // For now, just upsert
                        }
                    }
                    WorkerResult.success()
                }
                is com.contentedest.baby.data.repo.Result.Failure -> {
                    WorkerResult.retry()
                }
            }
        } catch (e: Exception) {
            WorkerResult.retry()
        }
    }

    companion object {
        const val WORK_NAME = "sync_work"

        fun schedulePeriodicSync(context: Context, deviceId: String) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.UNMETERED)
                .setRequiresBatteryNotLow(true)
                .build()

            val workRequest = PeriodicWorkRequestBuilder<SyncWorker>(15, java.util.concurrent.TimeUnit.MINUTES)
                .setConstraints(constraints)
                .setInputData(workDataOf("device_id" to deviceId))
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.REPLACE,
                workRequest
            )
        }
    }
}

// Extension function to convert EventDto to EventEntity
private fun EventDto.toEntity(): EventEntity {
    // For now, we'll store the payload as a simple string and parse basic fields
    // In a production app, you would properly parse the Map<String, Any>
    val payloadStr = payload?.toString() ?: "{}"

    // Simple parsing of common fields - this is a simplified approach
    val feedMode = try {
        if (payloadStr.contains("\"mode\"")) {
            val modeMatch = "\"mode\"\\s*:\\s*\"([^\"]+)\"".toRegex().find(payloadStr)
            modeMatch?.groupValues?.get(1)?.let { com.contentedest.baby.data.local.FeedMode.valueOf(it) }
        } else null
    } catch (e: Exception) { null }

    val bottleAmount = try {
        if (payloadStr.contains("bottle_amount_ml")) {
            "bottle_amount_ml\"?\\s*:\\s*(\\d+)".toRegex().find(payloadStr)?.groupValues?.get(1)?.toIntOrNull()
        } else null
    } catch (e: Exception) { null }

    val solidsAmount = try {
        if (payloadStr.contains("solids_amount")) {
            "solids_amount\"?\\s*:\\s*(\\d+)".toRegex().find(payloadStr)?.groupValues?.get(1)?.toIntOrNull()
        } else null
    } catch (e: Exception) { null }

    val nappyType = try {
        if (payloadStr.contains("nappy_type")) {
            "\"nappy_type\"\\s*:\\s*\"([^\"]+)\"".toRegex().find(payloadStr)?.groupValues?.get(1)
        } else null
    } catch (e: Exception) { null }

    return EventEntity(
        event_id = eventId,
        type = com.contentedest.baby.data.local.EventType.valueOf(type),
        payload = payload?.toString(), // Convert Map to String
        start_ts = startTs,
        end_ts = endTs,
        ts = ts,
        created_ts = createdTs,
        updated_ts = updatedTs,
        version = version,
        deleted = deleted,
        device_id = deviceId,
        feed_mode = feedMode,
        bottle_amount_ml = bottleAmount,
        solids_amount = solidsAmount,
        nappy_type = nappyType
    )
}
