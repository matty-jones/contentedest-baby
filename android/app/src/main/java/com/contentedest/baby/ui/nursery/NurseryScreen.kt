package com.contentedest.baby.ui.nursery

import android.app.Activity
import android.content.Context
import android.net.Uri
import android.util.Log
import android.view.WindowManager
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.rtsp.RtspMediaSource
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val TAG = "NurseryScreen"

private const val HEALTH_CHECK_INTERVAL_MS = 2_000L
private const val STALE_THRESHOLD_MS = 8_000L
private const val FIRST_FRAME_TIMEOUT_MS = 10_000L
private const val RECOVERY_COOLDOWN_MS = 15_000L
private const val HARD_ESCALATION_FAILURES = 2

private enum class RecoveryLevel {
    SOFT,
    HARD,
}

private class RecoveryCallbackHolder {
    var callback: (RecoveryLevel, String) -> Unit = { _, _ -> }
}

private class StreamHealthState {
    var lastRenderedFrameAtMs: Long = 0L
    var hasRenderedFirstFrame: Boolean = false
    var lastPosition: Long = 0L
    var lastPositionAdvanceMs: Long = System.currentTimeMillis()
    var lastRecoveryAttemptMs: Long = 0L
    var recoveryAttemptCount: Int = 0
    var consecutiveHealthFailures: Int = 0
    var loadStartedAtMs: Long = System.currentTimeMillis()
    var pendingRecoveryLevel: RecoveryLevel? = null
    var pendingRecoveryReason: String? = null
    var pendingRecoveryStartedAtMs: Long = 0L

    fun resetTracking() {
        hasRenderedFirstFrame = false
        lastRenderedFrameAtMs = 0L
        lastPosition = 0L
        lastPositionAdvanceMs = System.currentTimeMillis()
        loadStartedAtMs = System.currentTimeMillis()
    }

    fun markRecoveryAttempt(level: RecoveryLevel, reason: String, startedAtMs: Long) {
        pendingRecoveryLevel = level
        pendingRecoveryReason = reason
        pendingRecoveryStartedAtMs = startedAtMs
    }

    fun clearPendingRecovery() {
        pendingRecoveryLevel = null
        pendingRecoveryReason = null
    }
}

private fun onFrameRendered(streamHealth: StreamHealthState) {
    val now = System.currentTimeMillis()
    streamHealth.lastRenderedFrameAtMs = now
    streamHealth.hasRenderedFirstFrame = true
    streamHealth.consecutiveHealthFailures = 0

    val pendingLevel = streamHealth.pendingRecoveryLevel ?: return
    val reason = streamHealth.pendingRecoveryReason
    val afterMs = now - streamHealth.pendingRecoveryStartedAtMs
    streamHealth.clearPendingRecovery()
    Log.i(
        TAG,
        "RECOVER_OK level=${pendingLevel.name.lowercase()} reason=$reason afterMs=$afterMs",
    )
}

private fun createLoadControl(): DefaultLoadControl {
    return DefaultLoadControl.Builder()
        .setBufferDurationsMs(
            /* minBufferMs = */ 2_000,
            /* maxBufferMs = */ 8_000,
            /* bufferForPlaybackMs = */ 1_000,
            /* bufferForPlaybackAfterRebufferMs = */ 2_000,
        )
        .build()
}

private fun createNurseryPlayer(
    context: Context,
    streamHealth: StreamHealthState,
    recoveryHolder: RecoveryCallbackHolder,
): ExoPlayer {
    return ExoPlayer.Builder(context)
        .setLoadControl(createLoadControl())
        .build()
        .apply {
            videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT
            repeatMode = Player.REPEAT_MODE_ONE

            setVideoFrameMetadataListener { _, _, _, _ ->
                onFrameRendered(streamHealth)
            }

            addListener(object : Player.Listener {
                override fun onRenderedFirstFrame() {
                    onFrameRendered(streamHealth)
                    Log.d(TAG, "First frame rendered")
                }

                override fun onPlayerError(error: PlaybackException) {
                    Log.e(TAG, "ExoPlayer error: ${error.message}")
                    Log.e(TAG, "Error type: ${error.errorCode}, cause: ${error.cause?.message}")
                    recoveryHolder.callback(RecoveryLevel.SOFT, "player_error")
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    when (playbackState) {
                        Player.STATE_BUFFERING -> Log.d(TAG, "Player buffering")
                        Player.STATE_READY -> Log.d(TAG, "Player ready")
                        Player.STATE_ENDED -> {
                            Log.d(TAG, "Player ended")
                            recoveryHolder.callback(RecoveryLevel.SOFT, "state_ended")
                        }
                        Player.STATE_IDLE -> Log.d(TAG, "Player idle")
                    }
                }
            })
        }
}

private fun loadStream(player: ExoPlayer, streamUrl: String, streamHealth: StreamHealthState) {
    streamHealth.loadStartedAtMs = System.currentTimeMillis()
    Log.d(TAG, "Loading RTSP stream: $streamUrl")
    try {
        player.stop()
        player.clearMediaItems()

        val rtspFactory = RtspMediaSource.Factory()
            .setForceUseRtpTcp(true)

        val mediaSource = rtspFactory.createMediaSource(MediaItem.fromUri(Uri.parse(streamUrl)))
        player.setMediaSource(mediaSource)
        player.prepare()
        player.play()
        Log.d(TAG, "RTSP stream prepared and playing")
    } catch (e: Exception) {
        Log.e(TAG, "Failed to load RTSP stream", e)
    }
}

private fun evaluateStreamHealth(player: ExoPlayer, streamHealth: StreamHealthState): String? {
    val now = System.currentTimeMillis()

    if (player.playerError != null) {
        return "player_error"
    }

    when (player.playbackState) {
        Player.STATE_ENDED -> return "state_ended"
        Player.STATE_IDLE -> {
            if (streamHealth.hasRenderedFirstFrame) {
                return "state_idle"
            }
        }
    }

    if (!streamHealth.hasRenderedFirstFrame) {
        return if (now - streamHealth.loadStartedAtMs >= FIRST_FRAME_TIMEOUT_MS) {
            "first_frame_timeout"
        } else {
            null
        }
    }

    val playbackState = player.playbackState
    val isPlaying = player.isPlaying
    val expectsFrames = isPlaying ||
        playbackState == Player.STATE_READY ||
        playbackState == Player.STATE_BUFFERING

    if (!expectsFrames) {
        return null
    }

    val frameAgeMs = now - streamHealth.lastRenderedFrameAtMs
    if (frameAgeMs >= STALE_THRESHOLD_MS) {
        return "frame_stale"
    }

    if (isPlaying && playbackState == Player.STATE_READY) {
        val currentPosition = player.currentPosition
        when {
            currentPosition < streamHealth.lastPosition -> {
                streamHealth.lastPosition = currentPosition
                streamHealth.lastPositionAdvanceMs = now
            }
            currentPosition > streamHealth.lastPosition -> {
                streamHealth.lastPosition = currentPosition
                streamHealth.lastPositionAdvanceMs = now
            }
            now - streamHealth.lastPositionAdvanceMs >= STALE_THRESHOLD_MS -> {
                return "position_stall"
            }
        }
    }

    return null
}

@Composable
fun NurseryScreen(streamUrl: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()
    val streamUrlState = rememberUpdatedState(streamUrl)

    val streamHealth = remember { StreamHealthState() }
    val recoveryHolder = remember { RecoveryCallbackHolder() }

    var playerGeneration by remember { mutableIntStateOf(0) }
    var exoPlayer by remember {
        mutableStateOf(createNurseryPlayer(context, streamHealth, recoveryHolder))
    }

    recoveryHolder.callback = recovery@{ requestedLevel, reason ->
        val now = System.currentTimeMillis()
        if (now - streamHealth.lastRecoveryAttemptMs < RECOVERY_COOLDOWN_MS) {
            Log.d(
                TAG,
                "RECOVER_SKIP reason=$reason cooldownRemainingMs=${streamHealth.lastRecoveryAttemptMs + RECOVERY_COOLDOWN_MS - now}",
            )
            return@recovery
        }

        streamHealth.lastRecoveryAttemptMs = now
        streamHealth.recoveryAttemptCount++
        streamHealth.consecutiveHealthFailures++

        val player = exoPlayer
        val level = if (
            requestedLevel == RecoveryLevel.SOFT &&
            streamHealth.consecutiveHealthFailures >= HARD_ESCALATION_FAILURES
        ) {
            RecoveryLevel.HARD
        } else {
            requestedLevel
        }

        Log.w(
            TAG,
            "HEALTH_FAIL reason=$reason state=${player.playbackState} isPlaying=${player.isPlaying} " +
                "frameAgeMs=${now - streamHealth.lastRenderedFrameAtMs} " +
                "attempt=${streamHealth.recoveryAttemptCount} level=$level",
        )

        streamHealth.resetTracking()
        val recoveryStartedAt = System.currentTimeMillis()
        streamHealth.markRecoveryAttempt(level, reason, recoveryStartedAt)

        when (level) {
            RecoveryLevel.SOFT -> {
                loadStream(player, streamUrlState.value, streamHealth)
                Log.i(
                    TAG,
                    "RECOVER_ATTEMPT level=soft reason=$reason",
                )
            }
            RecoveryLevel.HARD -> {
                Log.w(TAG, "RECOVER_FAIL escalating level=hard reason=$reason")
                val newPlayer = createNurseryPlayer(context, streamHealth, recoveryHolder)
                exoPlayer = newPlayer
                playerGeneration++
                loadStream(newPlayer, streamUrlState.value, streamHealth)
                Log.i(
                    TAG,
                    "RECOVER_ATTEMPT level=hard reason=$reason",
                )
            }
        }
    }

    DisposableEffect(Unit) {
        val activity = context as? Activity
        val window = activity?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        Log.d(TAG, "Screen keep-on enabled")

        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            Log.d(TAG, "Screen keep-on disabled")
        }
    }

    DisposableEffect(exoPlayer) {
        val playerToRelease = exoPlayer
        val generation = playerGeneration
        onDispose {
            Log.d(TAG, "Releasing ExoPlayer generation=$generation")
            playerToRelease.release()
        }
    }

    LaunchedEffect(streamUrl) {
        streamHealth.resetTracking()
        loadStream(exoPlayer, streamUrl, streamHealth)
    }

    LaunchedEffect(lifecycleOwner) {
        while (isActive) {
            delay(HEALTH_CHECK_INTERVAL_MS)
            if (!lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                continue
            }

            val failureReason = evaluateStreamHealth(exoPlayer, streamHealth)
            if (failureReason != null) {
                recoveryHolder.callback(RecoveryLevel.SOFT, failureReason)
            }
        }
    }

    DisposableEffect(lifecycleOwner, exoPlayer) {
        val player = exoPlayer
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    Log.d(TAG, "App resumed - restarting stream for surface attachment")
                    coroutineScope.launch {
                        delay(100)
                        streamHealth.resetTracking()
                        loadStream(player, streamUrlState.value, streamHealth)
                    }
                }
                Lifecycle.Event.ON_PAUSE -> {
                    Log.d(TAG, "App paused - pausing player")
                    if (player.isPlaying) {
                        player.pause()
                    }
                }
                else -> Unit
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    key(playerGeneration) {
        AndroidView(
            modifier = modifier.fillMaxSize(),
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                }
            },
            update = { view ->
                if (view.player != exoPlayer) {
                    view.player = exoPlayer
                }
            },
        )
    }
}
