package com.xparcade.tvkiosk.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.AbstractYouTubePlayerListener
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views.YouTubePlayerView

@Composable
fun YouTubeTrailerPlayer(
    videoId: String,
    playbackKey: Int,
    modifier: Modifier = Modifier,
    onEnded: () -> Unit,
    onProgress: (Double, Double) -> Unit
) {
    val latestVideoId by rememberUpdatedState(videoId)
    val latestPlaybackKey by rememberUpdatedState(playbackKey)
    val latestOnEnded by rememberUpdatedState(onEnded)
    val latestOnProgress by rememberUpdatedState(onProgress)
    var playerView by remember { mutableStateOf<YouTubePlayerView?>(null) }
    var player by remember { mutableStateOf<YouTubePlayer?>(null) }
    var loadedPlayback by remember { mutableStateOf("") }
    var durationSeconds by remember { mutableStateOf(0.0) }

    DisposableEffect(Unit) {
        onDispose {
            playerView?.release()
            playerView = null
            player = null
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            YouTubePlayerView(context).apply {
                enableAutomaticInitialization = false
                initialize(
                    object : AbstractYouTubePlayerListener() {
                        override fun onReady(youTubePlayer: YouTubePlayer) {
                            player = youTubePlayer
                            val playback = "${latestVideoId}:${latestPlaybackKey}"
                            loadedPlayback = playback
                            youTubePlayer.setVolume(45)
                            youTubePlayer.loadVideo(latestVideoId, 0f)
                        }

                        override fun onStateChange(
                            youTubePlayer: YouTubePlayer,
                            state: PlayerConstants.PlayerState
                        ) {
                            if (state == PlayerConstants.PlayerState.ENDED) {
                                latestOnEnded()
                            }
                        }

                        override fun onCurrentSecond(
                            youTubePlayer: YouTubePlayer,
                            second: Float
                        ) {
                            latestOnProgress(second.toDouble(), durationSeconds)
                        }

                        override fun onVideoDuration(
                            youTubePlayer: YouTubePlayer,
                            duration: Float
                        ) {
                            durationSeconds = duration.toDouble()
                            latestOnProgress(0.0, durationSeconds)
                        }

                        override fun onError(
                            youTubePlayer: YouTubePlayer,
                            error: PlayerConstants.PlayerError
                        ) {
                            latestOnEnded()
                        }
                    },
                    true
                )
                playerView = this
            }
        },
        update = {
            val playback = "$videoId:$playbackKey"
            if (loadedPlayback != playback) {
                loadedPlayback = playback
                durationSeconds = 0.0
                player?.loadVideo(videoId, 0f)
            }
        }
    )
}
