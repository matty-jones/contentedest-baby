package com.contentedest.baby.sync

import android.content.Context
import androidx.work.*
import com.contentedest.baby.data.local.EventEntity
import com.contentedest.baby.data.repo.EventRepository
import com.contentedest.baby.data.repo.SyncRepository
import com.contentedest.baby.net.EventDto
import com.contentedest.baby.net.TokenStorage
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

class SyncWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted private val params: WorkerParameters,
    private val eventRepository: EventRepository,
    private val syncRepository: SyncRepository,
    private val tokenStorage: TokenStorage
) : CoroutineWorker(appContext, params) {

    @AssistedFactory
    interface Factory {
        fun create(appContext: Context, params: WorkerParameters): SyncWorker
    }

    override suspend fun doWork(): Result = coroutineScope {
        try {
            val token = tokenStorage.getToken()
            if (token == null) {
                return@coroutineScope Result.failure()
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
            when (pullResult) {
                is com.contentedest.baby.data.repo.Result.Success -> {
                    val (newClock, events) = pullResult.data
                    eventRepository.updateServerClock(newClock)

                    // Apply server events to local DB
                    events.forEach { eventDto ->
                        val entity = eventDto.toEntity()
                        // TODO: Handle conflicts based on version/updated_ts/device_id
                        // For now, just upsert
                    }

                    Result.success()
                }
                is com.contentedest.baby.data.repo.Result.Failure -> {
                    Result.retry()
                }
            }
        } catch (e: Exception) {
            Result.retry()
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
    val feedMode = payload?.get("mode")?.toString()?.let { com.contentedest.baby.data.local.FeedMode.valueOf(it) }
    val bottleAmount = payload?.get("bottle_amount_ml") as? Int
    val solidsAmount = payload?.get("solids_amount") as? Int
    val nappyType = payload?.get("nappy_type")?.toString()

    return EventEntity(
        event_id = event_id,
        type = com.contentedest.baby.data.local.EventType.valueOf(type),
        payload = payload,
        start_ts = start_ts,
        end_ts = end_ts,
        ts = ts,
        created_ts = created_ts,
        updated_ts = updated_ts,
        version = version,
        deleted = deleted,
        device_id = device_id,
        feed_mode = feedMode,
        bottle_amount_ml = bottleAmount,
        solids_amount = solidsAmount,
        nappy_type = nappyType
    )
}
