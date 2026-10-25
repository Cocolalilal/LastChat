package me.rerere.locallm

import android.content.Context
import android.os.Build
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.EmbeddingEngine
import com.google.ai.edge.litertlm.EmbeddingEngineConfig
import com.google.ai.edge.litertlm.EmbeddingOptions
import com.google.ai.edge.litertlm.InputData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import me.rerere.common.platform.PlatformLog
import java.io.File

/**
 * On-device text embeddings using LiteRT-LM's [EmbeddingEngine] and EmbeddingGemma 2 Text bundles.
 * Keeps one engine resident and serializes calls. Supported on arm64-v8a and x86_64.
 */
class LiteRtEmbedder(@Suppress("unused") private val context: Context) {

    private val mutex = Mutex()

    @Volatile
    private var loaded: Loaded? = null

    private data class Loaded(val key: String, val engine: EmbeddingEngine)

    fun isSupported(model: LocalModelMetadata): Boolean =
        supportsLocalEmbedding(model.modelFile, Build.SUPPORTED_ABIS?.toList().orEmpty())

    /**
     * Embeds [texts] with the installed embedding [model], returning one vector per input. Loads the
     * model on first use (disposing any previously loaded one). Throws with a stable key message on
     * unsupported ABI or missing files.
     */
    suspend fun embed(model: InstalledLocalModel, texts: List<String>): List<List<Float>> {
        check(model.kind == LocalModelKind.EMBEDDING) { "not_embedding_model" }
        check(model.id != RETIRED_EMBEDDING_MODEL_ID && usesLiteRtEmbeddingEngine(model.filePath)) {
            "embedding_model_retired:install_$TEXT_EMBEDDING_MODEL_ID"
        }
        check(supportsLocalEmbedding(model.filePath, Build.SUPPORTED_ABIS?.toList().orEmpty())) {
            "embedding_unsupported_abi"
        }
        check(File(model.filePath).exists()) { "embedding_model_missing" }
        if (texts.isEmpty()) return emptyList()

        return withContext(Dispatchers.IO) {
            mutex.withLock {
                val embedder = acquireLocked(model)
                texts.map { text ->
                    currentCoroutineContext().ensureActive()
                    embedder.engine.computeEmbeddingAsync(
                        listOf(InputData.Text(embeddingGemma2Text(text))),
                        EmbeddingOptions(normalize = true, outputSize = model.embeddingDimension ?: 768),
                    ).embedding.toList()
                }
            }
        }
    }

    /** Whether an embedding model is currently loaded (used to release memory on deletion). */
    fun currentModelKey(): String? = loaded?.key

    /**
     * Drops the resident embedding runtime before another heavyweight local workload starts.
     * The LiteRT embedding engine is explicitly closed.
     */
    suspend fun unload() = mutex.withLock {
        disposeLocked()
    }

    private fun acquireLocked(model: InstalledLocalModel): Loaded {
        // CPU is the default; GPU is used only when explicitly requested and not marked failed.
        val useGpu = model.config.accelerator == LocalAccelerator.GPU && !model.runtimeFlags.gpuCrashed
        val key = "${model.filePath}|${model.commitHash}|$useGpu"
        loaded?.let { if (it.key == key) return it }
        disposeLocked()
        PlatformLog.i(TAG, "Loading embedding model ${model.id} (gpu=$useGpu)")
        val engine = EmbeddingEngine(
            EmbeddingEngineConfig(
                modelPath = model.filePath,
                backend = if (useGpu) Backend.GPU() else Backend.CPU(),
                maxInputLength = model.defaultConfig.maxContextLength ?: 8192,
            )
        )
        engine.initialize()
        val result = Loaded(key, engine)
        loaded = result
        return result
    }

    private fun disposeLocked() {
        val previous = loaded
        loaded = null
        previous?.engine?.close()
    }

    companion object {
        private const val TAG = "LiteRtEmbedder"
    }
}
