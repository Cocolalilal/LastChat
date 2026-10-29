package me.rerere.rikkahub.data.repository

import android.media.MediaPlayer
import android.media.PlaybackParams
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

private const val TAG = "VoicePlaybackManager"

class VoicePlaybackManager {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var mediaPlayer: MediaPlayer? = null
    private var progressJob: Job? = null

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPositionMs = MutableStateFlow(0L)
    val currentPositionMs: StateFlow<Long> = _currentPositionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private val _speed = MutableStateFlow(1.0f)
    val speed: StateFlow<Float> = _speed.asStateFlow()

    private val _currentFile = MutableStateFlow<File?>(null)
    val currentFile: StateFlow<File?> = _currentFile.asStateFlow()

    @Synchronized
    fun play(file: File) {
        if (_currentFile.value == file && mediaPlayer != null) {
            resume()
            return
        }

        stop()
        runCatching {
            val player = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                prepare()
                setOnCompletionListener {
                    _isPlaying.value = false
                    _currentPositionMs.value = _durationMs.value
                    stopProgressTracking()
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                runCatching {
                    player.playbackParams = PlaybackParams().setSpeed(_speed.value)
                }
            }

            player.start()
            mediaPlayer = player
            _currentFile.value = file
            _durationMs.value = player.duration.toLong().coerceAtLeast(0L)
            _isPlaying.value = true
            startProgressTracking()
        }.onFailure {
            Log.e(TAG, "Failed to play audio file ${file.name}", it)
            stop()
        }
    }

    @Synchronized
    fun pause() {
        runCatching {
            mediaPlayer?.pause()
            _isPlaying.value = false
            stopProgressTracking()
        }
    }

    @Synchronized
    fun resume() {
        runCatching {
            mediaPlayer?.start()
            _isPlaying.value = true
            startProgressTracking()
        }
    }

    @Synchronized
    fun stop() {
        stopProgressTracking()
        runCatching {
            mediaPlayer?.apply {
                try {
                    stop()
                } catch (_: Exception) {}
                release()
            }
        }
        mediaPlayer = null
        _isPlaying.value = false
        _currentPositionMs.value = 0L
    }

    @Synchronized
    fun seekToFraction(fraction: Float) {
        val dur = _durationMs.value
        if (dur > 0L) {
            val targetMs = (dur * fraction.coerceIn(0f, 1f)).toLong()
            seekToMs(targetMs)
        }
    }

    @Synchronized
    fun seekToMs(positionMs: Long) {
        runCatching {
            val clamped = positionMs.coerceIn(0L, _durationMs.value)
            mediaPlayer?.seekTo(clamped.toInt())
            _currentPositionMs.value = clamped
        }
    }

    @Synchronized
    fun skipBy(deltaMs: Long) {
        val current = _currentPositionMs.value
        seekToMs(current + deltaMs)
    }

    @Synchronized
    fun setSpeed(newSpeed: Float) {
        _speed.value = newSpeed
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            runCatching {
                mediaPlayer?.let { player ->
                    val wasPlaying = player.isPlaying
                    player.playbackParams = PlaybackParams().setSpeed(newSpeed)
                    if (!wasPlaying) {
                        player.pause()
                    }
                }
            }
        }
    }

    private fun startProgressTracking() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive && _isPlaying.value) {
                mediaPlayer?.let { player ->
                    runCatching {
                        if (player.isPlaying) {
                            _currentPositionMs.value = player.currentPosition.toLong()
                        }
                    }
                }
                delay(100)
            }
        }
    }

    private fun stopProgressTracking() {
        progressJob?.cancel()
        progressJob = null
    }
}
