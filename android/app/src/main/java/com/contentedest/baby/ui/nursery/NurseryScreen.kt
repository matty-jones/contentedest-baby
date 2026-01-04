package com.contentedest.baby.ui.nursery

import android.app.Activity
import android.net.Uri
import android.util.Log
import android.view.WindowManager
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.rtsp.RtspMediaSource
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView

@Composable
fun NurseryScreen(streamUrl: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    
    // Keep screen on while viewing the nursery camera (full-screen media app behavior)
    DisposableEffect(Unit) {
        val activity = context as? Activity
        val window = activity?.window
        
        // Add FLAG_KEEP_SCREEN_ON to prevent screen timeout
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        Log.d("NurseryScreen", "Screen keep-on enabled")
        
        onDispose {
            // Remove FLAG_KEEP_SCREEN_ON when leaving the screen
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            Log.d("NurseryScreen", "Screen keep-on disabled")
        }
    }
    
    // Create and remember ExoPlayer instance
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            // Configure video scaling for full-screen display
            videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT
            // Enable repeat mode to keep stream playing
            repeatMode = Player.REPEAT_MODE_ONE
            // Add error listener
            addListener(object : Player.Listener {
                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    Log.e("NurseryScreen", "ExoPlayer error: ${error.message}")
                    Log.e("NurseryScreen", "Error type: ${error.errorCode}, cause: ${error.cause?.message}")
                    error.cause?.printStackTrace()
                }
                
                override fun onPlaybackStateChanged(playbackState: Int) {
                    when (playbackState) {
                        Player.STATE_BUFFERING -> Log.d("NurseryScreen", "Player buffering")
                        Player.STATE_READY -> Log.d("NurseryScreen", "Player ready")
                        Player.STATE_ENDED -> Log.d("NurseryScreen", "Player ended")
                        Player.STATE_IDLE -> Log.d("NurseryScreen", "Player idle")
                    }
                }
            })
        }
    }
    
    // Function to load/reload the RTSP stream
    val loadStream: () -> Unit = {
        Log.d("NurseryScreen", "Loading RTSP stream: $streamUrl")
        try {
            // Stop and clear any existing media
            exoPlayer.stop()
            exoPlayer.clearMediaItems()
            
            val uri = Uri.parse(streamUrl)
            // Create RTSP media source factory with TCP transport (more reliable than UDP)
            // TCP is required by many RTSP servers to avoid firewall/NAT issues
            val rtspFactory = RtspMediaSource.Factory()
                .setForceUseRtpTcp(true) // Use TCP instead of UDP for RTP transport
            
            val mediaItem = MediaItem.fromUri(uri)
            val mediaSource = rtspFactory.createMediaSource(mediaItem)
            
            exoPlayer.setMediaSource(mediaSource)
            exoPlayer.prepare()
            exoPlayer.play()
            Log.d("NurseryScreen", "RTSP stream prepared and playing")
        } catch (e: Exception) {
            Log.e("NurseryScreen", "Failed to load RTSP stream", e)
            e.printStackTrace()
        }
    }
    
    // Set media source when URL changes
    LaunchedEffect(streamUrl) {
        loadStream()
    }
    
    // Handle lifecycle events - reconnect stream when app resumes
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    Log.d("NurseryScreen", "App resumed - checking stream connection")
                    // Use coroutine scope with Main dispatcher to check state after a brief delay
                    // Player state might not be immediately updated when lifecycle event fires
                    try {
                        CoroutineScope(Dispatchers.Main).launch {
                            try {
                                delay(100) // Small delay to let player state stabilize
                                
                                val playbackState = exoPlayer.playbackState
                                val isPlaying = exoPlayer.isPlaying
                                val hasError = exoPlayer.playerError != null
                                
                                Log.d("NurseryScreen", "Player state after resume: playbackState=$playbackState, isPlaying=$isPlaying, hasError=$hasError")
                                
                                // Check if we need to reconnect
                                val needsReconnect = playbackState == Player.STATE_IDLE || 
                                                    playbackState == Player.STATE_ENDED || 
                                                    hasError ||
                                                    (!isPlaying && playbackState != Player.STATE_BUFFERING)
                                
                                // Always restart stream on resume to ensure proper surface attachment
                                // RTSP streams need fresh connection after background
                                Log.d("NurseryScreen", "Restarting stream on resume to ensure proper surface attachment")
                                loadStream()
                            } catch (e: Exception) {
                                Log.e("NurseryScreen", "Error in resume check coroutine", e)
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("NurseryScreen", "Error launching resume check coroutine", e)
                    }
                }
                Lifecycle.Event.ON_PAUSE -> {
                    Log.d("NurseryScreen", "App paused - pausing player")
                    // Pause player when going to background
                    if (exoPlayer.isPlaying) {
                        exoPlayer.pause()
                    }
                }
                else -> {}
            }
        }
        
        lifecycleOwner.lifecycle.addObserver(observer)
        
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    
    // Handle lifecycle - release player when composable is disposed
    DisposableEffect(Unit) {
        onDispose {
            Log.d("NurseryScreen", "Releasing ExoPlayer")
            exoPlayer.release()
        }
    }
    
    // Embed PlayerView in Compose
    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { ctx ->
            PlayerView(ctx).apply {
                player = exoPlayer
                useController = false // Hide controls for full-screen video
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
            }
        },
        update = { view ->
            // Ensure player is always attached
            if (view.player != exoPlayer) {
                view.player = exoPlayer
            }
        }
    )
}
