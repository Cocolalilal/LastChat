package me.rerere.asr.local

import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineMoonshineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineSenseVoiceModelConfig
import com.k2fsa.sherpa.onnx.OfflineWhisperModelConfig
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineRecognizer
import com.k2fsa.sherpa.onnx.OnlineRecognizerConfig
import com.k2fsa.sherpa.onnx.OnlineStream
import com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

class SherpaSttRuntime {
    private val mutex = Mutex()
    private var loaded: LoadedRecognizer? = null

    suspend fun transcribe(model: InstalledSherpaModel, samples: FloatArray): String =
        withContext(Dispatchers.Default) {
            // Own the recognizer for the entire decode; unload must not free a live native handle.
            mutex.withLock {
                val recognizer = when (val current = loaded) {
                    is LoadedRecognizer.Offline -> if (current.matches(model)) current.recognizer else loadOffline(model)
                    else -> loadOffline(model)
                }
                val stream = recognizer.createStream()
                try {
                    stream.acceptWaveform(samples, SAMPLE_RATE)
                    recognizer.decode(stream)
                    recognizer.getResult(stream).text.trim()
                } finally {
                    stream.release()
                }
            }
        }

    suspend fun openOnlineSession(model: InstalledSherpaModel): OnlineSession {
        var opened: OnlineSession? = null
        try {
            return withContext(Dispatchers.Default) {
                mutex.lock()
                try {
                    val recognizer = when (val current = loaded) {
                        is LoadedRecognizer.Online -> if (current.matches(model)) current.recognizer else loadOnline(model)
                        else -> loadOnline(model)
                    }
                    OnlineSession(recognizer, recognizer.createStream()) { mutex.unlock() }.also { opened = it }
                } catch (error: Throwable) {
                    mutex.unlock()
                    throw error
                }
            }
        } catch (error: Throwable) {
            // withContext can cancel after creating the resource but before delivering it.
            opened?.close()
            throw error
        }
    }

    suspend fun unload() = mutex.withLock { disposeLocked() }

    private fun loadOffline(model: InstalledSherpaModel): OfflineRecognizer {
        require(!model.streaming) { "${model.id} is a streaming model" }
        requireModelFiles(
            model,
            when (model.family) {
                SherpaModelFamily.WHISPER,
                SherpaModelFamily.MOONSHINE -> listOf(
                    SherpaFileRole.ENCODER,
                    SherpaFileRole.DECODER,
                    SherpaFileRole.TOKENS,
                )
                SherpaModelFamily.SENSE_VOICE -> listOf(SherpaFileRole.MODEL, SherpaFileRole.TOKENS)
                SherpaModelFamily.ONLINE_TRANSDUCER -> emptyList()
            },
        )
        disposeLocked()
        val modelConfig = when (model.family) {
            SherpaModelFamily.WHISPER -> OfflineModelConfig(
                whisper = OfflineWhisperModelConfig(
                    encoder = model.file(SherpaFileRole.ENCODER),
                    decoder = model.file(SherpaFileRole.DECODER),
                    language = model.config.language,
                    task = "transcribe",
                ),
                tokens = model.file(SherpaFileRole.TOKENS),
                numThreads = model.config.numThreads.coerceIn(1, 8),
                modelType = "whisper",
            )

            SherpaModelFamily.SENSE_VOICE -> OfflineModelConfig(
                senseVoice = OfflineSenseVoiceModelConfig(
                    model = model.file(SherpaFileRole.MODEL),
                    language = model.config.language,
                    useInverseTextNormalization = model.config.useInverseTextNormalization,
                ),
                tokens = model.file(SherpaFileRole.TOKENS),
                numThreads = model.config.numThreads.coerceIn(1, 8),
                modelType = "sense_voice",
            )

            SherpaModelFamily.MOONSHINE -> OfflineModelConfig(
                moonshine = OfflineMoonshineModelConfig(
                    encoder = model.file(SherpaFileRole.ENCODER),
                    mergedDecoder = model.file(SherpaFileRole.DECODER),
                ),
                tokens = model.file(SherpaFileRole.TOKENS),
                numThreads = model.config.numThreads.coerceIn(1, 8),
                modelType = "moonshine",
            )

            SherpaModelFamily.ONLINE_TRANSDUCER -> error("Online model passed to offline runtime")
        }
        val recognizer = OfflineRecognizer(
            config = OfflineRecognizerConfig(
                featConfig = FeatureConfig(sampleRate = SAMPLE_RATE, featureDim = 80),
                modelConfig = modelConfig,
                decodingMethod = "greedy_search",
            )
        )
        loaded = LoadedRecognizer.Offline(model, recognizer)
        return recognizer
    }

    private fun loadOnline(model: InstalledSherpaModel): OnlineRecognizer {
        require(model.streaming && model.family == SherpaModelFamily.ONLINE_TRANSDUCER)
        requireModelFiles(
            model,
            listOf(SherpaFileRole.ENCODER, SherpaFileRole.DECODER, SherpaFileRole.JOINER, SherpaFileRole.TOKENS),
        )
        disposeLocked()
        val recognizer = OnlineRecognizer(
            assetManager = null,
            config = OnlineRecognizerConfig(
                featConfig = FeatureConfig(sampleRate = SAMPLE_RATE, featureDim = 80),
                modelConfig = OnlineModelConfig(
                    transducer = OnlineTransducerModelConfig(
                        encoder = model.file(SherpaFileRole.ENCODER),
                        decoder = model.file(SherpaFileRole.DECODER),
                        joiner = model.file(SherpaFileRole.JOINER),
                    ),
                    tokens = model.file(SherpaFileRole.TOKENS),
                    numThreads = model.config.numThreads.coerceIn(1, 8),
                    modelType = model.onlineModelType,
                ),
                enableEndpoint = true,
                decodingMethod = "greedy_search",
            ),
        )
        loaded = LoadedRecognizer.Online(model, recognizer)
        return recognizer
    }

    private fun disposeLocked() {
        when (val current = loaded) {
            is LoadedRecognizer.Offline -> current.recognizer.release()
            is LoadedRecognizer.Online -> current.recognizer.release()
            null -> Unit
        }
        loaded = null
    }

    private fun requireModelFiles(model: InstalledSherpaModel, roles: List<String>) {
        val missing = roles.filter { role ->
            model.files[role]?.let(::File)?.isFile != true
        }
        require(missing.isEmpty()) {
            "Local speech model is incomplete (${missing.joinToString()}). Re-download it from Local models."
        }
    }

    private sealed interface LoadedRecognizer {
        val model: InstalledSherpaModel
        fun matches(other: InstalledSherpaModel): Boolean = model.id == other.id && model.config == other.config &&
            model.revision == other.revision && model.files == other.files && model.family == other.family &&
            model.onlineModelType == other.onlineModelType

        data class Offline(override val model: InstalledSherpaModel, val recognizer: OfflineRecognizer) : LoadedRecognizer
        data class Online(override val model: InstalledSherpaModel, val recognizer: OnlineRecognizer) : LoadedRecognizer
    }

    class OnlineSession internal constructor(
        private val recognizer: OnlineRecognizer,
        private val stream: OnlineStream,
        private val onClose: () -> Unit,
    ) {
        private var closed = false
        fun accept(samples: FloatArray): String = synchronized(this) {
            check(!closed) { "Speech session is closed" }
            stream.acceptWaveform(samples, SAMPLE_RATE)
            decodeReady()
            recognizer.getResult(stream).text.trim()
        }

        fun isEndpoint(): Boolean = synchronized(this) {
            check(!closed) { "Speech session is closed" }
            recognizer.isEndpoint(stream)
        }

        fun reset() = synchronized(this) {
            check(!closed) { "Speech session is closed" }
            recognizer.reset(stream)
        }

        fun finish(): String = synchronized(this) {
            check(!closed) { "Speech session is closed" }
            // The online decoder needs a full final chunk to flush the last spoken words.
            stream.acceptWaveform(FloatArray(SAMPLE_RATE * 8 / 10), SAMPLE_RATE)
            stream.inputFinished()
            decodeReady()
            recognizer.getResult(stream).text.trim()
        }

        fun close() = synchronized(this) {
            if (!closed) {
                closed = true
                try { stream.release() } finally { onClose() }
            }
        }

        private fun decodeReady() {
            while (recognizer.isReady(stream)) recognizer.decode(stream)
        }
    }

    companion object {
        const val SAMPLE_RATE = 16_000
    }
}
