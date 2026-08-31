package me.rerere.tts.controller

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.ByteArrayDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.rerere.tts.model.AudioFormat
import me.rerere.tts.model.PlaybackState
import me.rerere.tts.model.PlaybackStatus
import me.rerere.tts.model.TTSResponse

class AudioPlayer(context: Context) : TtsAudioPlayer {
    private val player = ExoPlayer.Builder(context).build()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _playbackState = MutableStateFlow(PlaybackState())
    override val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private var positionJob: Job? = null
    private var totalChunksCount = 0

    init {
        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val chunkIndex = mediaItem?.mediaId?.toIntOrNull()
                val current1Based = if (chunkIndex != null) chunkIndex + 1 else player.currentMediaItemIndex + 1
                _playbackState.update {
                    it.copy(
                        currentChunkIndex = current1Based,
                        totalChunks = totalChunksCount.coerceAtLeast(current1Based),
                        positionMs = 0L,
                        durationMs = if (player.duration > 0) player.duration else 0L,
                        status = if (player.isPlaying) PlaybackStatus.Playing else it.status
                    )
                }
            }

            override fun onPlaybackStateChanged(state: Int) {
                when (state) {
                    Player.STATE_BUFFERING -> {
                        _playbackState.update { it.copy(status = PlaybackStatus.Buffering) }
                        stopPositionUpdates()
                    }
                    Player.STATE_READY -> {
                        val isPlaying = player.isPlaying
                        val duration = if (player.duration > 0) player.duration else playbackState.value.durationMs
                        val chunkIndex = player.currentMediaItem?.mediaId?.toIntOrNull()
                        val current1Based = if (chunkIndex != null) chunkIndex + 1 else player.currentMediaItemIndex + 1
                        _playbackState.update {
                            it.copy(
                                status = if (isPlaying) PlaybackStatus.Playing else PlaybackStatus.Paused,
                                durationMs = duration,
                                positionMs = player.currentPosition,
                                currentChunkIndex = current1Based,
                                totalChunks = totalChunksCount.coerceAtLeast(current1Based)
                            )
                        }
                        if (isPlaying) startPositionUpdates() else stopPositionUpdates()
                    }
                    Player.STATE_ENDED -> {
                        stopPositionUpdates()
                        _playbackState.update {
                            it.copy(
                                status = PlaybackStatus.Ended,
                                positionMs = player.duration.coerceAtLeast(it.positionMs),
                                durationMs = if (player.duration > 0) player.duration else it.durationMs
                            )
                        }
                    }
                    Player.STATE_IDLE -> {
                        stopPositionUpdates()
                        _playbackState.update { it.copy(status = PlaybackStatus.Idle) }
                    }
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                stopPositionUpdates()
                _playbackState.update { it.copy(status = PlaybackStatus.Error, errorMessage = error.message) }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                val status = if (isPlaying) PlaybackStatus.Playing else {
                    if (player.playbackState == Player.STATE_ENDED) PlaybackStatus.Ended
                    else if (player.playbackState == Player.STATE_IDLE) PlaybackStatus.Idle
                    else PlaybackStatus.Paused
                }
                _playbackState.update { it.copy(status = status) }
                if (isPlaying) startPositionUpdates() else stopPositionUpdates()
            }
        })
    }

    override fun pause() {
        player.pause()
        stopPositionUpdates()
        _playbackState.update { it.copy(status = PlaybackStatus.Paused) }
    }

    override fun resume() {
        if (player.playbackState == Player.STATE_IDLE && player.mediaItemCount > 0) {
            player.prepare()
        }
        player.play()
        _playbackState.update { it.copy(status = PlaybackStatus.Playing) }
    }

    override fun stop() {
        player.stop()
        player.clearMediaItems()
        stopPositionUpdates()
        totalChunksCount = 0
        _playbackState.update { PlaybackState(status = PlaybackStatus.Idle) }
    }

    override fun clear() {
        player.clearMediaItems()
        totalChunksCount = 0
    }

    override fun release() {
        stop()
        player.release()
    }

    override fun seekBy(ms: Long) {
        val target = (player.currentPosition + ms).coerceAtLeast(0L)
        player.seekTo(target)
    }

    override fun setSpeed(speed: Float) {
        player.playbackParameters = PlaybackParameters(speed)
        _playbackState.update { it.copy(speed = speed) }
    }

    override fun skipNext() {
        if (player.hasNextMediaItem()) {
            player.seekToNextMediaItem()
        }
    }

    override fun setTotalChunks(total: Int) {
        totalChunksCount = total
        _playbackState.update { it.copy(totalChunks = total) }
    }

    @OptIn(UnstableApi::class)
    override fun enqueue(chunkIndex: Int, totalChunks: Int, response: TTSResponse) {
        totalChunksCount = totalChunks
        val bytes = if (response.format == AudioFormat.PCM) {
            pcmToWavBytes(response.audioData, response.sampleRate ?: 24000)
        } else response.audioData

        val dataSourceFactory = DataSource.Factory { ByteArrayDataSource(bytes) }
        val mediaItem = MediaItem.Builder()
            .setMediaId(chunkIndex.toString())
            .setUri(Uri.EMPTY)
            .build()
        val mediaSource = ProgressiveMediaSource.Factory(dataSourceFactory)
            .createMediaSource(mediaItem)

        val isIdleOrEnded = player.playbackState == Player.STATE_IDLE || player.playbackState == Player.STATE_ENDED
        player.addMediaSource(mediaSource)

        if (isIdleOrEnded) {
            player.prepare()
            player.play()
        } else if (!player.isPlaying && player.playWhenReady) {
            player.play()
        }

        _playbackState.update {
            it.copy(
                totalChunks = totalChunks,
                status = if (player.isPlaying) PlaybackStatus.Playing else PlaybackStatus.Buffering
            )
        }
    }

    private fun startPositionUpdates() {
        if (positionJob?.isActive == true) return
        positionJob = scope.launch(Dispatchers.Main.immediate) {
            while (true) {
                val chunkIndex = player.currentMediaItem?.mediaId?.toIntOrNull()
                val current1Based = if (chunkIndex != null) chunkIndex + 1 else player.currentMediaItemIndex + 1
                _playbackState.update {
                    it.copy(
                        positionMs = player.currentPosition,
                        durationMs = if (player.duration > 0) player.duration else it.durationMs,
                        currentChunkIndex = current1Based,
                        totalChunks = totalChunksCount.coerceAtLeast(current1Based)
                    )
                }
                delay(100)
            }
        }
    }

    private fun stopPositionUpdates() {
        positionJob?.cancel()
        positionJob = null
    }
}


