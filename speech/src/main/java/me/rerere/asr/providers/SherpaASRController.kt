package me.rerere.asr.providers

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.rerere.asr.ASRController
import me.rerere.asr.ASRState
import me.rerere.asr.ASRStatus
import me.rerere.asr.appendAmplitude
import me.rerere.asr.calculateRmsAmplitude
import me.rerere.asr.local.InstalledSherpaModel
import me.rerere.asr.local.SherpaModelStore
import me.rerere.asr.local.SherpaSttRuntime
import me.rerere.common.inference.LocalInferenceManager
import me.rerere.common.inference.LocalInferenceWorkload
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class SherpaASRController(
    private val context: Context,
    private val modelId: String,
    private val store: SherpaModelStore,
    private val runtime: SherpaSttRuntime,
    private val inferenceManager: LocalInferenceManager,
) : ASRController {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow(ASRState(isAvailable = true))
    override val state: StateFlow<ASRState> = _state.asStateFlow()

    private var sessionJob: Job? = null
    private var audioRecord: AudioRecord? = null
    private var callback: ((String) -> Unit)? = null
    @Volatile private var stopRequested = false

    override fun start(onTranscriptChange: (String) -> Unit) {
        if (state.value.isRecording) return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            fail("Microphone permission is required")
            return
        }
        stopRequested = false
        callback = onTranscriptChange
        _state.value = ASRState(status = ASRStatus.Connecting, isAvailable = true)
        sessionJob = scope.launch(Dispatchers.IO) {
            inferenceManager.withLease(LocalInferenceWorkload.SPEECH, modelId) {
                runCatching {
                    val model = store.get(modelId) ?: error("Local speech model is not installed")
                    if (stopRequested) {
                        finishIdle("")
                        return@runCatching
                    }
                    if (model.streaming) runOnline(model) else runOffline(model)
                }.onFailure { error ->
                    if (error !is kotlinx.coroutines.CancellationException) fail(error.message ?: "Local transcription failed")
                }
            }
        }
    }

    override fun stop() {
        if (!state.value.isRecording) return
        stopRequested = true
        _state.update { it.copy(status = ASRStatus.Stopping) }
        runCatching { audioRecord?.stop() }
    }

    override fun dispose() {
        runCatching { audioRecord?.stop() }
        audioRecord?.release()
        audioRecord = null
        sessionJob?.cancel()
        scope.cancel()
    }

    private suspend fun runOffline(model: InstalledSherpaModel) {
        val chunks = Channel<FloatArray>(capacity = Channel.UNLIMITED)
        val completed = mutableListOf<String>()
        val decoder = scope.launch(Dispatchers.Default) {
            for (samples in chunks) {
                val text = runtime.transcribe(model, samples)
                if (text.isNotBlank()) {
                    completed += text
                    publish(completed.joinToString(" "))
                }
            }
        }

        val recorder = createRecorder()
        audioRecord = recorder
        val segment = ByteArrayOutputStream()
        var speechSeen = false
        var silentBuffers = 0
        val readBuffer = ByteArray(BUFFER_BYTES)
        recorder.startRecording()
        _state.update { it.copy(status = ASRStatus.Listening) }
        try {
            while (!stopRequested && recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                val read = recorder.read(readBuffer, 0, readBuffer.size)
                if (read <= 0) continue
                segment.write(readBuffer, 0, read)
                val amplitude = calculateRmsAmplitude(readBuffer, read)
                _state.update { it.copy(amplitudes = it.amplitudes.appendAmplitude(amplitude)) }
                if (amplitude >= SPEECH_AMPLITUDE) {
                    speechSeen = true
                    silentBuffers = 0
                } else if (speechSeen) {
                    silentBuffers++
                }
                val longEnoughSilence = silentBuffers >= SILENT_BUFFERS_TO_FLUSH
                val maxSegmentReached = segment.size() >= MAX_SEGMENT_BYTES
                if (speechSeen && (longEnoughSilence || maxSegmentReached)) {
                    chunks.send(pcm16ToFloat(segment.toByteArray()))
                    segment.reset()
                    speechSeen = false
                    silentBuffers = 0
                }
            }
        } finally {
            recorder.release()
            audioRecord = null
            if (segment.size() >= MIN_SEGMENT_BYTES) chunks.send(pcm16ToFloat(segment.toByteArray()))
            chunks.close()
            decoder.join()
            finishIdle(completed.joinToString(" "))
        }
    }

    private suspend fun runOnline(model: InstalledSherpaModel) {
        val session = runtime.openOnlineSession(model)
        val completed = mutableListOf<String>()
        val recorder = createRecorder()
        audioRecord = recorder
        val readBuffer = ByteArray(BUFFER_BYTES)
        recorder.startRecording()
        _state.update { it.copy(status = ASRStatus.Listening) }
        try {
            while (!stopRequested && recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                val read = recorder.read(readBuffer, 0, readBuffer.size)
                if (read <= 0) continue
                val amplitude = calculateRmsAmplitude(readBuffer, read)
                _state.update { it.copy(amplitudes = it.amplitudes.appendAmplitude(amplitude)) }
                val partial = session.accept(pcm16ToFloat(readBuffer, read))
                publish(joinTranscript(completed, partial))
                if (session.isEndpoint() && partial.isNotBlank()) {
                    completed += partial
                    session.reset()
                }
            }
            val final = session.finish()
            if (final.isNotBlank()) completed += final
        } finally {
            recorder.release()
            audioRecord = null
            session.close()
            finishIdle(completed.joinToString(" "))
        }
    }

    private fun createRecorder(): AudioRecord {
        val minBuffer = AudioRecord.getMinBufferSize(
            SherpaSttRuntime.SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        return AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            SherpaSttRuntime.SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            maxOf(minBuffer, BUFFER_BYTES * 2),
        ).also { check(it.state == AudioRecord.STATE_INITIALIZED) { "Unable to initialize microphone" } }
    }

    private fun publish(text: String) {
        _state.update { it.copy(transcript = text) }
        scope.launch { callback?.invoke(text) }
    }

    private fun finishIdle(text: String) {
        publish(text)
        _state.update { it.copy(status = ASRStatus.Idle) }
    }

    private fun fail(message: String) {
        _state.value = ASRState(
            status = ASRStatus.Error,
            isAvailable = true,
            errorMessage = message,
        )
    }

    private fun pcm16ToFloat(bytes: ByteArray, length: Int = bytes.size): FloatArray {
        val shorts = ByteBuffer.wrap(bytes, 0, length)
            .order(ByteOrder.LITTLE_ENDIAN)
            .asShortBuffer()
        return FloatArray(shorts.remaining()) { shorts.get(it) / 32768f }
    }

    private fun joinTranscript(completed: List<String>, partial: String): String =
        (completed + partial.takeIf { it.isNotBlank() }).filterNotNull().joinToString(" ")

    companion object {
        private const val BUFFER_BYTES = 3_200 // 100 ms at 16 kHz mono PCM16
        private const val SPEECH_AMPLITUDE = 0.18f
        private const val SILENT_BUFFERS_TO_FLUSH = 8
        private const val MIN_SEGMENT_BYTES = 16_000 // 500 ms
        private const val MAX_SEGMENT_BYTES = 640_000 // 20 seconds
    }
}
