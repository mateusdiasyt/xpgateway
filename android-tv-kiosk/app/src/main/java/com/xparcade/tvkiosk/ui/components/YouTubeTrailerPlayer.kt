package com.xparcade.tvkiosk.ui.components

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

private class YouTubePlayerBridge(
    private val onEnded: () -> Unit,
    private val onProgress: (Double, Double) -> Unit
) {
    private val mainHandler = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun ended() {
        mainHandler.post(onEnded)
    }

    @JavascriptInterface
    fun failed() {
        mainHandler.postDelayed(onEnded, 900)
    }

    @JavascriptInterface
    fun progress(current: Double, duration: Double) {
        mainHandler.post { onProgress(current, duration) }
    }
}

private fun playerHtml(videoId: String) = """
    <!doctype html>
    <html>
      <head>
        <meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1">
        <style>
          html, body, #player {
            width: 100%;
            height: 100%;
            margin: 0;
            padding: 0;
            overflow: hidden;
            background: #05070b;
          }
        </style>
      </head>
      <body>
        <div id="player"></div>
        <script src="https://www.youtube.com/iframe_api"></script>
        <script>
          var player;
          var progressTimer;
          function onYouTubeIframeAPIReady() {
            player = new YT.Player('player', {
              videoId: '$videoId',
              playerVars: {
                autoplay: 1,
                controls: 0,
                disablekb: 1,
                fs: 0,
                playsinline: 1,
                rel: 0,
                modestbranding: 1
              },
              events: {
                onReady: function(event) {
                  event.target.setVolume(45);
                  event.target.playVideo();
                  progressTimer = setInterval(function() {
                    if (!player || !player.getDuration) return;
                    AndroidBridge.progress(player.getCurrentTime() || 0, player.getDuration() || 0);
                  }, 1000);
                },
                onStateChange: function(event) {
                  if (event.data === YT.PlayerState.ENDED) {
                    if (progressTimer) clearInterval(progressTimer);
                    AndroidBridge.ended();
                  }
                },
                onError: function() {
                  if (progressTimer) clearInterval(progressTimer);
                  AndroidBridge.failed();
                }
              }
            });
          }
        </script>
      </body>
    </html>
""".trimIndent()

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun YouTubeTrailerPlayer(
    videoId: String,
    modifier: Modifier = Modifier,
    onEnded: () -> Unit,
    onProgress: (Double, Double) -> Unit
) {
    val latestOnEnded by rememberUpdatedState(onEnded)
    val latestOnProgress by rememberUpdatedState(onProgress)
    var webView by remember { mutableStateOf<WebView?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            webView?.stopLoading()
            webView?.removeJavascriptInterface("AndroidBridge")
            webView?.destroy()
            webView = null
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                setBackgroundColor(Color.BLACK)
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.mediaPlaybackRequiresUserGesture = false
                settings.javaScriptCanOpenWindowsAutomatically = false
                webViewClient = WebViewClient()
                webChromeClient = WebChromeClient()
                addJavascriptInterface(
                    YouTubePlayerBridge(
                        onEnded = { latestOnEnded() },
                        onProgress = { current, duration -> latestOnProgress(current, duration) }
                    ),
                    "AndroidBridge"
                )
                tag = videoId
                loadDataWithBaseURL(
                    "https://www.youtube.com",
                    playerHtml(videoId),
                    "text/html",
                    "UTF-8",
                    null
                )
                webView = this
            }
        },
        update = { view ->
            if (view.tag != videoId) {
                view.tag = videoId
                view.loadDataWithBaseURL(
                    "https://www.youtube.com",
                    playerHtml(videoId),
                    "text/html",
                    "UTF-8",
                    null
                )
            }
        }
    )
}
