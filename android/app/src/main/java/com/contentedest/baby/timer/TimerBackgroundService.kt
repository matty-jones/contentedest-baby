package com.contentedest.baby.timer

import android.app.Service
import android.content.Intent
import android.os.IBinder
import kotlinx.coroutines.*

class TimerBackgroundService : Service() {
    private var updateJob: Job? = null
    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var timerStateStorage: TimerStateStorage? = null

    override fun onCreate() {
        super.onCreate()
        timerStateStorage = TimerStateStorage(applicationContext)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startUpdating()
        return START_STICKY // Restart if killed
    }

    private fun startUpdating() {
        // Cancel any existing job
        updateJob?.cancel()
        
        updateJob = serviceScope.launch {
            while (isActive) {
                val state = timerStateStorage?.getActiveTimer()
                if (state != null && state.running) {
                    val now = java.time.Instant.now().epochSecond
                    timerStateStorage?.updateEndTime(now)
                } else {
                    // Timer stopped or not running, stop the service
                    stopSelf()
                    break
                }
                delay(1000) // Update every second
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null // Not a bound service
    }

    override fun onDestroy() {
        super.onDestroy()
        updateJob?.cancel()
        serviceScope.cancel()
    }

    companion object {
        const val ACTION_START = "com.contentedest.baby.timer.START"
        const val ACTION_STOP = "com.contentedest.baby.timer.STOP"
    }
}



