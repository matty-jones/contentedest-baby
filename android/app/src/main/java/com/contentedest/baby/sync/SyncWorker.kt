package com.contentedest.baby.sync

import android.content.Context
import androidx.work.*
import androidx.work.ListenableWorker.Result as WorkerResult
import com.contentedest.baby.data.repo.EventRepository
import com.contentedest.baby.data.repo.GrowthRepository
import com.contentedest.baby.data.repo.SyncRepository
import com.contentedest.baby.net.EventDto
import com.contentedest.baby.net.GrowthDataDto
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

class SyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WorkerDependenciesEntryPoint {
        fun eventRepository(): EventRepository
        fun growthRepository(): GrowthRepository
        fun syncRepository(): SyncRepository
    }

    private val eventRepository: EventRepository
    private val growthRepository: GrowthRepository
    private val syncRepository: SyncRepository

    init {
        val entryPoint = EntryPointAccessors.fromApplication(
            appContext,
            WorkerDependenciesEntryPoint::class.java
        )
        eventRepository = entryPoint.eventRepository()
        growthRepository = entryPoint.growthRepository()
        syncRepository = entryPoint.syncRepository()
    }

    override suspend fun doWork(): ListenableWorker.Result = coroutineScope {
        try {
            val deviceId = inputData.getString("device_id") ?: "device-${System.currentTimeMillis()}"
            eventRepository.ensureSyncState(deviceId)

            val lastClock = eventRepository.getLastServerClock()

            // Get all local events and convert them to DTOs for pushing
            val eventsToPush = eventRepository.getAllEventsAsDtos()
            android.util.Log.d("SyncWorker", "Pushing ${eventsToPush.size} local events to server")

            val pushDeferred = async { syncRepository.syncPush(eventsToPush) }
            val pullResult = syncRepository.syncPull(lastClock)
            pushDeferred.await()

            // Sync growth data
            val growthPullResult = growthRepository.syncPull(since = 0)

            return@coroutineScope when (pullResult) {
                is com.contentedest.baby.data.repo.Result.Success -> {
                    val successData = pullResult.data
                    if (successData is Pair<*, *>) {
                        val newClock = successData.first as Long
                        val events = successData.second as List<EventDto>
                        if (events.isNotEmpty()) {
                            eventRepository.saveServerEvents(events)
                            android.util.Log.d("SyncWorker", "Saved ${events.size} events to local database")
                            eventRepository.updateServerClock(newClock)
                            android.util.Log.d("SyncWorker", "Updated server clock to: $newClock")
                        } else {
                            android.util.Log.d("SyncWorker", "No new events to sync")
                        }
                    }
                    
                    // Handle growth data sync result
                    when (growthPullResult) {
                        is com.contentedest.baby.data.repo.Result.Success -> {
                            val growthData = growthPullResult.data.second
                            if (growthData.isNotEmpty()) {
                                android.util.Log.d("SyncWorker", "Synced ${growthData.size} growth data entries")
                            } else {
                                android.util.Log.d("SyncWorker", "No new growth data to sync")
                            }
                        }
                        is com.contentedest.baby.data.repo.Result.Failure -> {
                            android.util.Log.e("SyncWorker", "Growth data sync failed", growthPullResult.exception)
                        }
                    }
                    
                    WorkerResult.success()
                }
                is com.contentedest.baby.data.repo.Result.Failure -> {
                    android.util.Log.e("SyncWorker", "Sync pull failed", pullResult.exception)
                    WorkerResult.retry()
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("SyncWorker", "Sync failed with exception", e)
            WorkerResult.retry()
        }
    }

    companion object {
        const val WORK_NAME = "sync_work"
        const val IMMEDIATE_SYNC_WORK_NAME = "immediate_sync_work"

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

        fun triggerImmediateSync(context: Context, deviceId: String) {
            val workRequest = OneTimeWorkRequestBuilder<SyncWorker>()
                .setInputData(workDataOf("device_id" to deviceId))
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                IMMEDIATE_SYNC_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                workRequest
            )
        }
    }
}

