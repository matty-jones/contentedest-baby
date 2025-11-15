package com.contentedest.baby.ui.nursery

import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView

@Composable
fun NurseryScreen(streamUrl: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { ctx ->
            WebView(ctx).apply {
                // Set WebView background to ensure it's visible
                setBackgroundColor(android.graphics.Color.BLACK)
                
                webViewClient = object : WebViewClient() {
                    override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                        super.onPageStarted(view, url, favicon)
                        Log.d("NurseryScreen", "Page started loading: $url")
                    }
                    
                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        Log.d("NurseryScreen", "Page finished loading: $url")
                        
                        // Check page content and video elements
                        view?.evaluateJavascript("""
                            (function() {
                                var info = {
                                    title: document.title,
                                    videoCount: document.getElementsByTagName('video').length,
                                    bodyHTML: document.body ? document.body.innerHTML.substring(0, 500) : 'no body',
                                    hasCanvas: document.getElementsByTagName('canvas').length > 0
                                };
                                return JSON.stringify(info);
                            })();
                        """.trimIndent()) { result ->
                            Log.d("NurseryScreen", "Page content: $result")
                        }
                        
                        // Inject CSS and force video to play
                        val cssInjection = """
                            (function() {
                                var style = document.createElement('style');
                                style.innerHTML = 'video { width: 100vw !important; height: 100vh !important; min-width: 100% !important; min-height: 100% !important; object-fit: contain !important; position: fixed !important; top: 0 !important; left: 0 !important; z-index: 9999 !important; background: black !important; display: block !important; visibility: visible !important; opacity: 1 !important; } body, html { margin: 0 !important; padding: 0 !important; overflow: hidden !important; width: 100vw !important; height: 100vh !important; position: fixed !important; top: 0 !important; left: 0 !important; background: black !important; } * { box-sizing: border-box !important; }';
                                document.head.appendChild(style);
                                
                                // Also set body and html styles directly
                                document.body.style.margin = '0';
                                document.body.style.padding = '0';
                                document.body.style.overflow = 'hidden';
                                document.body.style.width = '100%';
                                document.body.style.height = '100%';
                                document.body.style.position = 'fixed';
                                document.body.style.top = '0';
                                document.body.style.left = '0';
                                document.body.style.background = 'black';
                                document.documentElement.style.margin = '0';
                                document.documentElement.style.padding = '0';
                                document.documentElement.style.overflow = 'hidden';
                                document.documentElement.style.width = '100%';
                                document.documentElement.style.height = '100%';
                                document.documentElement.style.position = 'fixed';
                                document.documentElement.style.top = '0';
                                document.documentElement.style.left = '0';
                                document.documentElement.style.background = 'black';
                                
                                // Also try to resize and play existing video elements
                                var videos = document.getElementsByTagName('video');
                                console.log('Found ' + videos.length + ' video elements');
                                for (var i = 0; i < videos.length; i++) {
                                    var video = videos[i];
                                    video.style.width = '100vw';
                                    video.style.height = '100vh';
                                    video.style.minWidth = '100%';
                                    video.style.minHeight = '100%';
                                    video.style.objectFit = 'contain';
                                    video.style.position = 'fixed';
                                    video.style.top = '0';
                                    video.style.left = '0';
                                    video.style.zIndex = '9999';
                                    video.style.backgroundColor = 'black';
                                    video.style.display = 'block';
                                    video.style.visibility = 'visible';
                                    video.style.opacity = '1';
                                    video.setAttribute('playsinline', 'true');
                                    video.setAttribute('autoplay', 'true');
                                    video.muted = true; // Mute to allow autoplay
                                    video.play().then(function() {
                                        console.log('Video playing successfully');
                                    }).catch(function(err) {
                                        console.log('Video play error: ' + err);
                                    });
                                    console.log('Resized and attempted to play video element ' + i);
                                    console.log('Video computed style: width=' + window.getComputedStyle(video).width + ', height=' + window.getComputedStyle(video).height + ', display=' + window.getComputedStyle(video).display + ', visibility=' + window.getComputedStyle(video).visibility);
                                }
                            })();
                        """.trimIndent()
                        view?.evaluateJavascript(cssInjection, null)
                        
                        // Retry after delays in case video elements are created dynamically
                        view?.postDelayed({
                            view.evaluateJavascript("""
                                (function() {
                                    var videos = document.getElementsByTagName('video');
                                    console.log('Delayed check: Found ' + videos.length + ' video elements');
                                    for (var i = 0; i < videos.length; i++) {
                                        var video = videos[i];
                                        video.style.width = '100%';
                                        video.style.height = '100%';
                                        video.style.objectFit = 'contain';
                                        video.style.position = 'absolute';
                                        video.style.top = '0';
                                        video.style.left = '0';
                                        video.style.zIndex = '1';
                                        video.muted = true;
                                        video.play().catch(function(err) {
                                            console.log('Delayed play error: ' + err);
                                        });
                                    }
                                    return videos.length;
                                })();
                            """.trimIndent()) { count ->
                                Log.d("NurseryScreen", "Video elements found after 2s: $count")
                            }
                        }, 2000)
                        
                        view?.postDelayed({
                            view.evaluateJavascript("""
                                (function() {
                                    var videos = document.getElementsByTagName('video');
                                    console.log('Second delayed check: Found ' + videos.length + ' video elements');
                                    var info = [];
                                    for (var i = 0; i < videos.length; i++) {
                                        var video = videos[i];
                                        video.style.width = '100%';
                                        video.style.height = '100%';
                                        video.style.objectFit = 'contain';
                                        video.style.position = 'absolute';
                                        video.style.top = '0';
                                        video.style.left = '0';
                                        video.style.zIndex = '1';
                                        video.muted = true;
                                        var playPromise = video.play();
                                        if (playPromise !== undefined) {
                                            playPromise.catch(function(err) {
                                                console.log('Second delayed play error: ' + err);
                                            });
                                        }
                                        info.push({
                                            paused: video.paused,
                                            readyState: video.readyState,
                                            videoWidth: video.videoWidth,
                                            videoHeight: video.videoHeight,
                                            src: video.src || 'no src'
                                        });
                                    }
                                    return JSON.stringify(info);
                                })();
                            """.trimIndent()) { info ->
                                Log.d("NurseryScreen", "Video info after 5s: $info")
                            }
                        }, 5000)
                    }
                    
                    override fun onReceivedError(
                        view: WebView?,
                        request: WebResourceRequest?,
                        error: WebResourceError?
                    ) {
                        super.onReceivedError(view, request, error)
                        Log.e("NurseryScreen", "WebView error: ${error?.description} (${error?.errorCode}) for ${request?.url}")
                    }
                    
                    override fun onReceivedHttpError(
                        view: WebView?,
                        request: WebResourceRequest?,
                        errorResponse: android.webkit.WebResourceResponse?
                    ) {
                        super.onReceivedHttpError(view, request, errorResponse)
                        Log.e("NurseryScreen", "HTTP error: ${errorResponse?.statusCode} for ${request?.url}")
                    }
                }
                
                webChromeClient = object : WebChromeClient() {
                    override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                        Log.d("NurseryScreen", "Console [${consoleMessage?.messageLevel()}]: ${consoleMessage?.message()} -- From line ${consoleMessage?.lineNumber()} of ${consoleMessage?.sourceId()}")
                        return true
                    }
                    
                    override fun onProgressChanged(view: WebView?, newProgress: Int) {
                        super.onProgressChanged(view, newProgress)
                        if (newProgress == 100) {
                            Log.d("NurseryScreen", "Page load progress: 100%")
                        }
                    }
                }
                
                // Enable JavaScript and DOM storage for WebRTC/HTML5 video
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                // databaseEnabled is deprecated and no longer needed
                
                // Video playback settings
                settings.mediaPlaybackRequiresUserGesture = false
                settings.allowFileAccess = true
                settings.allowContentAccess = true
                
                // Layout and rendering settings
                settings.useWideViewPort = true
                settings.loadWithOverviewMode = true
                settings.layoutAlgorithm = android.webkit.WebSettings.LayoutAlgorithm.NORMAL
                
                // Mixed content (HTTP/HTTPS) - allow for local network streams
                settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                
                // Enable hardware acceleration for video
                setLayerType(WebView.LAYER_TYPE_HARDWARE, null)
                
                // Set user agent to help with video compatibility
                settings.userAgentString = settings.userAgentString + " ContentedestBaby/1.0"
                
                Log.d("NurseryScreen", "Loading URL: $streamUrl")
                Log.d("NurseryScreen", "WebView settings - JS: ${settings.javaScriptEnabled}, DOM: ${settings.domStorageEnabled}, Hardware: ${layerType == WebView.LAYER_TYPE_HARDWARE}")
                loadUrl(streamUrl)
            }
        },
        update = { view ->
            if (view.url != streamUrl) {
                view.loadUrl(streamUrl)
            }
        }
    )
}
