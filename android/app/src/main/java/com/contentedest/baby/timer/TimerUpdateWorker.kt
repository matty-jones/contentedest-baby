package com.contentedest.baby.timer

import android.content.Context
import androidx.work.*
import androidx.work.ListenableWorker.Result as WorkerResult
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

class TimerUpdateWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WorkerDependenciesEntryPoint {
        fun timerStateStorage(): TimerStateStorage
    }

    private val timerStateStorage: TimerStateStorage

    init {
        val entryPoint = EntryPointAccessors.fromApplication(
            appContext,
            WorkerDependenciesEntryPoint::class.java
        )
        timerStateStorage = entryPoint.timerStateStorage()
    }

    override suspend fun doWork(): ListenableWorker.Result {
        return try {
            val state = timerStateStorage.getActiveTimer()
            if (state != null && state.running) {
                // Update end time to current time
                val now = java.time.Instant.now().epochSecond
                timerStateStorage.updateEndTime(now)
                WorkerResult.success()
            } else {
                // No active timer or timer not running, cancel future work
                WorkerResult.success()
            }
        } catch (e: Exception) {
            android.util.Log.e("TimerUpdateWorker", "Failed to update timer", e)
            WorkerResult.retry()
        }
    }

    companion object {
        const val WORK_NAME = "timer_update_work"

        fun schedulePeriodicUpdate(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                .setRequiresBatteryNotLow(false)
                .build()

            val workRequest = PeriodicWorkRequestBuilder<TimerUpdateWorker>(
                30, // Every 30 seconds
                java.util.concurrent.TimeUnit.SECONDS
            )
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.REPLACE,
                workRequest
            )
        }

        fun cancelUpdate(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}

