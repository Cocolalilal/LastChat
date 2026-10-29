package me.rerere.rikkahub.data.repository

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.os.Environment
import android.os.SystemClock
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val TAG = "VoiceRecordingManager"

enum class RecordingState {
    Idle,
    Recording,
    Paused,
    Stopping,
}

class VoiceRecordingManager(
    private val context: Context,
) {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var mediaRecorder: MediaRecorder? = null
    private var currentOutputFile: File? = null
    private var tickerJob: Job? = null

    private var startTimestamp = 0L
    private var accumulatedTimeMs = 0L

    private val _recordingState = MutableStateFlow(RecordingState.Idle)
    val recordingState: StateFlow<RecordingState> = _recordingState.asStateFlow()

    private val _elapsedTimeMs = MutableStateFlow(0L)
    val elapsedTimeMs: StateFlow<Long> = _elapsedTimeMs.asStateFlow()

    private val _currentAmplitude = MutableStateFlow(0f)
    val currentAmplitude: StateFlow<Float> = _currentAmplitude.asStateFlow()

    private fun getStorageDirectory(): File {
        val publicDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_RECORDINGS), "LastChat")
        if (!publicDir.exists()) {
            val created = publicDir.mkdirs()
            if (!created && !publicDir.exists()) {
                val internalDir = File(context.filesDir, "recordings")
                if (!internalDir.exists()) internalDir.mkdirs()
                return internalDir
            }
        }
        return publicDir
    }

    @Synchronized
    fun startRecording(): Result<File> {
        if (_recordingState.value == RecordingState.Recording) {
            return Result.failure(IllegalStateException("Already recording"))
        }

        return runCatching {
            val dir = getStorageDirectory()
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val file = File(dir, "Recording_$timestamp.m4a")

            val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

            recorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(128000)
                setAudioSamplingRate(44100)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }

            mediaRecorder = recorder
            currentOutputFile = file
            startTimestamp = SystemClock.elapsedRealtime()
            accumulatedTimeMs = 0L
            _elapsedTimeMs.value = 0L
            _recordingState.value = RecordingState.Recording

            startPolling()
            file
        }.onFailure { e ->
            Log.e(TAG, "Failed to start recording", e)
            cleanup()
        }
    }

    @Synchronized
    fun pauseRecording() {
        if (_recordingState.value != RecordingState.Recording) return
        runCatching {
            mediaRecorder?.pause()
            accumulatedTimeMs += SystemClock.elapsedRealtime() - startTimestamp
            _recordingState.value = RecordingState.Paused
            _currentAmplitude.value = 0f
        }.onFailure { Log.e(TAG, "Failed to pause recording", it) }
    }

    @Synchronized
    fun resumeRecording() {
        if (_recordingState.value != RecordingState.Paused) return
        runCatching {
            mediaRecorder?.resume()
            startTimestamp = SystemClock.elapsedRealtime()
            _recordingState.value = RecordingState.Recording
        }.onFailure { Log.e(TAG, "Failed to resume recording", it) }
    }

    @Synchronized
    fun stopRecording(): Result<File> {
        val file = currentOutputFile
        if (_recordingState.value == RecordingState.Idle || file == null) {
            return Result.failure(IllegalStateException("No active recording"))
        }

        _recordingState.value = RecordingState.Stopping
        return runCatching {
            tickerJob?.cancel()
            tickerJob = null

            mediaRecorder?.apply {
                try {
                    stop()
                } catch (e: Exception) {
                    Log.w(TAG, "MediaRecorder stop failed, audio might be too short", e)
                }
                release()
            }
            mediaRecorder = null
            _recordingState.value = RecordingState.Idle
            _currentAmplitude.value = 0f
            currentOutputFile = null
            file
        }.onFailure {
            cleanup()
        }
    }

    @Synchronized
    fun discardRecording() {
        val file = currentOutputFile
        cleanup()
        file?.delete()
    }

    private fun startPolling() {
        tickerJob?.cancel()
        tickerJob = scope.launch {
            while (isActive && _recordingState.value == RecordingState.Recording) {
                val now = SystemClock.elapsedRealtime()
                _elapsedTimeMs.value = accumulatedTimeMs + (now - startTimestamp)

                mediaRecorder?.let { rec ->
                    val maxAmp = runCatching { rec.maxAmplitude }.getOrDefault(0)
                    val normalized = (maxAmp / 32767f).coerceIn(0f, 1f)
                    _currentAmplitude.value = normalized
                }
                delay(80)
            }
        }
    }

    private fun cleanup() {
        tickerJob?.cancel()
        tickerJob = null
        try {
            mediaRecorder?.release()
        } catch (_: Exception) {}
        mediaRecorder = null
        currentOutputFile = null
        _recordingState.value = RecordingState.Idle
        _currentAmplitude.value = 0f
        _elapsedTimeMs.value = 0L
    }
}
