package me.rerere.asr.providers

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.SystemClock
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
import kotlinx.coroutines.withContext
import me.rerere.asr.ASRController
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ProviderSetting
import me.rerere.asr.ASRState
import me.rerere.asr.ASRStatus
import me.rerere.asr.appendAmplitude
import me.rerere.asr.calculateRmsAmplitude
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.Collections

private const val TAG = "OpenAICompatASR"

// OpenRouter 单次请求 base64 限制约 10MB ≈ 7.5MB raw; 提前在 6MB 触发自动 flush。
private const val MAX_SEGMENT_BYTES = 6 * 1024 * 1024

/**
 * OpenAI-compatible REST STT Controller.
 *
 * Records audio in segments and POSTs each segment to {baseUrl}/audio/transcriptions.
 * - Standard OpenAI-compatible providers (OpenAI, Groq, Regolo, DeepInfra, Together,
 *   Fireworks, xAI, custom): multipart form with `file` (WAV), `model`, `language`,
 *   `response_format`, `temperature`.
 * - OpenRouter (auto-detected via baseUrl): JSON body with `model`, `input_audio`
 *   ({data: base64, format: "wav"}), `language`.
 *
 * The transcribed text from each segment is appended to completedTranscripts and
 * published via onTranscriptChange. stop() does a final flush of remaining PCM.
 */
class OpenAICompatibleASRController(
    private val context: Context,
    private val httpClient: OkHttpClient,
    private val provider: ProviderSetting.OpenAI,
    private val model: Model
) : ASRController {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _state = MutableStateFlow(ASRState(isAvailable = true))
    override val state: StateFlow<ASRState> = _state.asStateFlow()

    private var recorderJob: Job? = null
    private var audioRecord: AudioRecord? = null
    private var onTranscriptChange: ((String) -> Unit)? = null

    private var flushJob: Job? = null

    private val bufferLock = Any()
    private var currentBuffer = ByteArrayOutputStream()
    private var segmentStartElapsedMs = 0L
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
            currentBuffer = ByteArrayOutputStream()
            segmentStartElapsedMs = SystemClock.elapsedRealtime()
            sessionBuffer = ByteArrayOutputStream()
        }
        completedTranscripts.clear()
        flushJob = null

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
                flushJob?.join()
                flushSegment()
            } catch (e: Exception) {
                Log.e(TAG, "Final flush failed", e)
                setError(e.message ?: "STT final flush failed")
            } finally {
                val fullWavBytes = runCatching {
                    val pcmBytes = sessionBuffer.toByteArray()
                    if (pcmBytes.isEmpty()) null else {
                        pcm16ToWav(
                            pcm = pcmBytes,
                            sampleRate = model.sttOptions?.sampleRate ?: 16000,
                            channels = 1,
                            bitsPerSample = 16,
                        )
                    }
                }.getOrNull()

                _state.update { 
                    it.copy(
                        status = ASRStatus.Idle,
                        audioData = fullWavBytes
                    ) 
                }
            }
        }
    }

    override fun dispose() {
        recorderJob?.cancel()
        flushJob?.cancel()
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
                val segmentMs = (model.sttOptions?.segmentDurationSec ?: 0).coerceAtLeast(0) * 1000L
                var silenceStartMs = 0L
                sessionBuffer.reset()
                while (isActive) {
                    val read = recorder.read(buffer, 0, buffer.size)
                    if (read > 0) {
                        val amplitude = calculateRmsAmplitude(buffer, read)
                        _state.update { it.copy(amplitudes = it.amplitudes.appendAmplitude(amplitude)) }

                        val shouldFlush = synchronized(bufferLock) {
                            currentBuffer.write(buffer, 0, read)
                            sessionBuffer.write(buffer, 0, read)
                            if (segmentMs <= 0) {
                                currentBuffer.size() >= MAX_SEGMENT_BYTES
                            } else {
                                val elapsed = SystemClock.elapsedRealtime() - segmentStartElapsedMs
                                currentBuffer.size() >= MAX_SEGMENT_BYTES || elapsed >= segmentMs
                            }
                        }

                        if (shouldFlush) {
                            triggerFlush()
                        }
                        
                        // Simple VAD auto-stop (1.5 seconds of silence)
                        if (amplitude < 0.12f) {
                            if (silenceStartMs == 0L) silenceStartMs = SystemClock.elapsedRealtime()
                            else if (SystemClock.elapsedRealtime() - silenceStartMs > 1500L) {
                                scope.launch { stop() }
                                break
                            }
                        } else {
                            silenceStartMs = 0L
                        }
                    } else if (read < 0) {
                        throw IllegalStateException("AudioRecord read error: $read")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Audio recording failed", e)
                setError(e.message ?: "Audio recording failed")
            } finally {
                releaseRecorder()
            }
        }
    }

    private fun triggerFlush() {
        if (flushJob?.isActive == true) return
        flushJob = scope.launch(Dispatchers.IO) {
            runCatching { flushSegment() }
                .onFailure { 
                    Log.e(TAG, "Segment flush failed", it)
                    setError(it.message ?: "Segment flush failed")
                }
        }
    }

    private suspend fun flushSegment() {
        val pcmBytes = synchronized(bufferLock) {
            if (currentBuffer.size() == 0) return
            val bytes = currentBuffer.toByteArray()
            currentBuffer = ByteArrayOutputStream()
            segmentStartElapsedMs = SystemClock.elapsedRealtime()
            bytes
        }

        val wavBytes = pcm16ToWav(
            pcm = pcmBytes,
            sampleRate = model.sttOptions?.sampleRate ?: 16000,
            channels = 1,
            bitsPerSample = 16,
        )

        val isOpenRouter = provider.baseUrl.contains("openrouter.ai", ignoreCase = true)
        val text = if (isOpenRouter) {
            transcribeOpenRouter(wavBytes)
        } else {
            transcribeMultipart(wavBytes)
        }

        if (text.isNotEmpty()) {
            completedTranscripts.add(text)
            publishTranscript()
        }
    }

    private suspend fun transcribeMultipart(wavBytes: ByteArray): String {
        val sttOptions = model.sttOptions
        val responseFormat = sttOptions?.responseFormat ?: "json"
        val language = sttOptions?.language.orEmpty()
        val prompt = sttOptions?.prompt.orEmpty()
        val temperature = sttOptions?.temperature ?: 0f

        val multipartBuilder = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", "audio.wav", wavBytes.toRequestBody(AUDIO_OCTET_STREAM))
            .addFormDataPart("model", model.modelId)
            .addFormDataPart("response_format", responseFormat)

        if (language.isNotBlank()) {
            multipartBuilder.addFormDataPart("language", language)
        }
        if (prompt.isNotBlank()) {
            multipartBuilder.addFormDataPart("prompt", prompt)
        }
        multipartBuilder.addFormDataPart("temperature", temperature.toString())

        val request = Request.Builder()
            .url("${provider.baseUrl.trimEnd('/')}/audio/transcriptions")
            .addHeader("Authorization", "Bearer ${provider.apiKey}")
            .post(multipartBuilder.build())
            .build()

        return executeTranscription(request, responseFormat)
    }

    private suspend fun transcribeOpenRouter(wavBytes: ByteArray): String {
        val sttOptions = model.sttOptions
        val language = sttOptions?.language.orEmpty()
        val b64 = Base64.encodeToString(wavBytes, Base64.NO_WRAP)
        val body = JSONObject()
            .put("model", model.modelId)
            .put("input_audio", JSONObject().put("data", b64).put("format", "wav"))
        if (language.isNotBlank()) {
            body.put("language", language)
        }

        val request = Request.Builder()
            .url("${provider.baseUrl.trimEnd('/')}/audio/transcriptions")
            .addHeader("Authorization", "Bearer ${provider.apiKey}")
            .addHeader("Content-Type", "application/json")
            .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        // OpenRouter always returns JSON with a `text` field
        return executeTranscription(request, "json")
    }

    private suspend fun executeTranscription(request: Request, responseFormat: String): String {
        return withContext(Dispatchers.IO) {
            httpClient.newCall(request).execute().use { resp ->
                val respBody = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    throw IOException("STT HTTP ${resp.code}: $respBody")
                }
                val trimmedBody = respBody.trim()
                if (trimmedBody.startsWith("{") && trimmedBody.endsWith("}")) {
                    val json = runCatching { JSONObject(trimmedBody) }.getOrNull()
                    if (json != null) {
                        if (json.has("text")) {
                            val txt = json.optString("text", "").trim()
                            if (txt.isNotEmpty()) return@use txt
                        }
                        // Try nested output.text or something if exists
                        val possibleTextKeys = listOf("text", "transcript", "result")
                        for (key in json.keys()) {
                            val v = json.opt(key)
                            if (v is String && v.isNotEmpty() && key in possibleTextKeys) return@use v
                            if (v is JSONObject && v.has("text")) {
                                val nestedTxt = v.optString("text", "").trim()
                                if (nestedTxt.isNotEmpty()) return@use nestedTxt
                            }
                        }
                    }
                }
                
                when (responseFormat) {
                    "text" -> trimmedBody
                    "json", "verbose_json" -> {
                        val json = runCatching { JSONObject(trimmedBody) }.getOrElse {
                            throw IOException("STT response is not valid JSON: $trimmedBody")
                        }
                        json.optString("text", "").trim()
                    }
                    else -> trimmedBody
                }
            }
        }
    }

    private fun publishTranscript() {
        val transcript = completedTranscripts
            .filter { it.isNotBlank() }
            .joinToString(" ")
        _state.update { it.copy(transcript = transcript, errorMessage = null) }
        scope.launch { onTranscriptChange?.invoke(transcript) }
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

    companion object {
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()
        private val AUDIO_OCTET_STREAM = "application/octet-stream".toMediaType()

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
}
