package me.rerere.rikkahub.service.stt

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Base64
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import me.rerere.ai.core.MessageRole
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ProviderManager
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.asr.ASRController
import me.rerere.asr.ASRState
import me.rerere.asr.ASRStatus
import me.rerere.asr.appendAmplitude
import me.rerere.asr.calculateRmsAmplitude
import java.io.ByteArrayOutputStream
import java.util.Collections

private const val TAG = "ChatMultimodalASR"

class ChatMultimodalASRController(
    private val context: Context,
    private val providerManager: ProviderManager,
    private val provider: ProviderSetting,
    private val model: Model,
    private val thinkingBudget: Int?,
    private val prompt: String,
) : ASRController {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _state = MutableStateFlow(ASRState(isAvailable = true))
    override val state: StateFlow<ASRState> = _state.asStateFlow()

    private var recorderJob: Job? = null
    private var audioRecord: AudioRecord? = null
    private var onTranscriptChange: ((String) -> Unit)? = null

    private val bufferLock = Any()
    private var sessionBuffer = ByteArrayOutputStream()
    private val completedTranscripts = Collections.synchronizedList(mutableListOf<String>())

    override fun start(onTranscriptChange: (String) -> Unit) {
        if (state.value.isRecording) return
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            setError("Microphone permission is required")
            return
        }

        this.onTranscriptChange = onTranscriptChange
        synchronized(bufferLock) {
            sessionBuffer = ByteArrayOutputStream()
        }
        completedTranscripts.clear()

        _state.update {
            ASRState(
                status = ASRStatus.Listening,
                isAvailable = true,
            )
        }
        startRecorder()
    }

    override fun stop() {
        if (!state.value.isRecording) return
        recorderJob?.cancel()
        releaseRecorder()
        _state.update { it.copy(status = ASRStatus.Stopping) }

        scope.launch(Dispatchers.IO) {
            try {
                val pcmBytes = sessionBuffer.toByteArray()
                if (pcmBytes.isNotEmpty()) {
                    val sampleRate = model.sttOptions?.sampleRate ?: 16000
                    val wavBytes = pcm16ToWav(
                        pcm = pcmBytes,
                        sampleRate = sampleRate,
                        channels = 1,
                        bitsPerSample = 16,
                    )
                    
                    val base64Wav = Base64.encodeToString(wavBytes, Base64.NO_WRAP)
                    val audioUrl = "data:audio/wav;base64,$base64Wav"
                    
                    val messages = listOf(
                        UIMessage(
                            role = MessageRole.USER,
                            parts = listOf(
                                UIMessagePart.Text(prompt),
                                UIMessagePart.Audio(url = audioUrl)
                            )
                        )
                    )

                    var fullTranscript = ""
                    providerManager.getProviderByType(provider).streamText(
                        providerSetting = provider,
                        messages = messages,
                        params = me.rerere.ai.provider.TextGenerationParams(
                            model = model,
                            thinkingBudget = thinkingBudget ?: 0
                        )
                    ).collect { update ->
                        val textChunks = update.choices.firstOrNull()?.delta?.parts?.filterIsInstance<UIMessagePart.Text>()
                        if (!textChunks.isNullOrEmpty()) {
                            fullTranscript += textChunks.joinToString("") { it.text }
                            
                            // Publish incrementally
                            _state.update { it.copy(transcript = fullTranscript, errorMessage = null) }
                            onTranscriptChange?.invoke(fullTranscript)
                        }
                    }
                    
                    _state.update { 
                        it.copy(
                            status = ASRStatus.Idle,
                            audioData = wavBytes
                        ) 
                    }
                } else {
                    _state.update { it.copy(status = ASRStatus.Idle) }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Transcription failed", e)
                setError(e.message ?: "Transcription failed")
            }
        }
    }

    override fun dispose() {
        recorderJob?.cancel()
        releaseRecorder()
        scope.cancel()
    }

    @SuppressLint("MissingPermission")
    private fun startRecorder() {
        recorderJob?.cancel()
        recorderJob = scope.launch(Dispatchers.IO) {
            val sampleRate = model.sttOptions?.sampleRate ?: 16000
            val minBufferSize = AudioRecord.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
            )
            val bufferSize = minBufferSize
                .coerceAtLeast(sampleRate / 10 * 2)
                .coerceAtLeast(4096)

            val recorder = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize * 2,
            )
            audioRecord = recorder

            try {
                recorder.startRecording()
                val buffer = ByteArray(bufferSize)
                sessionBuffer.reset()
                while (isActive) {
                    val read = recorder.read(buffer, 0, buffer.size)
                    if (read > 0) {
                        val amplitude = calculateRmsAmplitude(buffer, read)
                        _state.update { it.copy(amplitudes = it.amplitudes.appendAmplitude(amplitude)) }

                        synchronized(bufferLock) {
                            sessionBuffer.write(buffer, 0, read)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Audio recording error", e)
                setError("Audio recording error: ${e.message}")
            } finally {
                releaseRecorder()
            }
        }
    }

    private fun setError(message: String) {
        _state.update {
            it.copy(
                status = ASRStatus.Error,
                errorMessage = message,
            )
        }
    }

    private fun releaseRecorder() {
        recorderJob = null
        runCatching { audioRecord?.stop() }
        runCatching { audioRecord?.release() }
        audioRecord = null
    }

    private fun pcm16ToWav(
        pcm: ByteArray,
        sampleRate: Int,
        channels: Int,
        bitsPerSample: Int,
    ): ByteArray {
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val blockAlign = channels * bitsPerSample / 8
        val dataSize = pcm.size
        val out = ByteArrayOutputStream(44 + dataSize)

        out.write("RIFF".toByteArray(Charsets.US_ASCII))
        writeIntLE(out, 36 + dataSize)
        out.write("WAVE".toByteArray(Charsets.US_ASCII))
        out.write("fmt ".toByteArray(Charsets.US_ASCII))
        writeIntLE(out, 16)
        writeShortLE(out, 1)
        writeShortLE(out, channels)
        writeIntLE(out, sampleRate)
        writeIntLE(out, byteRate)
        writeShortLE(out, blockAlign)
        writeShortLE(out, bitsPerSample)
        out.write("data".toByteArray(Charsets.US_ASCII))
        writeIntLE(out, dataSize)
        out.write(pcm)
        return out.toByteArray()
    }

    private fun writeIntLE(out: ByteArrayOutputStream, value: Int) {
        out.write(value and 0xFF)
        out.write((value shr 8) and 0xFF)
        out.write((value shr 16) and 0xFF)
        out.write((value shr 24) and 0xFF)
    }

    private fun writeShortLE(out: ByteArrayOutputStream, value: Int) {
        out.write(value and 0xFF)
        out.write((value shr 8) and 0xFF)
    }
}
