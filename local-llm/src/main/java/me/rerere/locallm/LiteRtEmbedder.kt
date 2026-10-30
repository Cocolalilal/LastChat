package me.rerere.locallm

import android.content.Context
import android.os.Build
import com.google.ai.edge.localagents.rag.models.EmbedData
import com.google.ai.edge.localagents.rag.models.EmbeddingRequest
import com.google.ai.edge.localagents.rag.models.GemmaEmbeddingModel
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.EmbeddingEngine
import com.google.ai.edge.litertlm.EmbeddingEngineConfig
import com.google.ai.edge.litertlm.EmbeddingOptions
import com.google.ai.edge.litertlm.InputData
import com.google.common.util.concurrent.FutureCallback
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import me.rerere.common.platform.PlatformLog
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * On-device text embedding runtime, the embedding counterpart of [LiteRtRuntime]. Uses LiteRT-LM's
 * [EmbeddingEngine] for EmbeddingGemma 2 bundles and the AI Edge RAG [GemmaEmbeddingModel] for legacy
 * EmbeddingGemma .tflite files.
 *
 * Keeps a single embedding model resident and serializes calls. ABI support depends on the runtime:
 * RAG ships for arm64-v8a only, while LiteRT-LM also supports x86_64.
 */
class LiteRtEmbedder(@Suppress("unused") private val context: Context) {

    private val mutex = Mutex()

    @Volatile
    private var loaded: Loaded? = null

    private sealed class Loaded(val key: String) {
        class Legacy(key: String, val model: GemmaEmbeddingModel) : Loaded(key)
        class LiteRt(key: String, val engine: EmbeddingEngine) : Loaded(key)
    }

    fun isSupported(model: LocalModelMetadata): Boolean =
        supportsLocalEmbedding(model.modelFile, Build.SUPPORTED_ABIS?.toList().orEmpty())

    /**
     * Embeds [texts] with the installed embedding [model], returning one vector per input. Loads the
     * model on first use (disposing any previously loaded one). Throws with a stable key message on
     * unsupported ABI or missing files.
     */
    suspend fun embed(model: InstalledLocalModel, texts: List<String>): List<List<Float>> {
        check(model.kind == LocalModelKind.EMBEDDING) { "not_embedding_model" }
        check(supportsLocalEmbedding(model.filePath, Build.SUPPORTED_ABIS?.toList().orEmpty())) {
            "embedding_unsupported_abi"
        }
        check(File(model.filePath).exists()) { "embedding_model_missing" }
        if (!usesLiteRtEmbeddingEngine(model.filePath)) {
            val tokenizerPath = model.tokenizerPath ?: error("tokenizer_missing")
            check(File(tokenizerPath).exists()) { "embedding_tokenizer_missing" }
        }
        if (texts.isEmpty()) return emptyList()

        return withContext(Dispatchers.IO) {
            mutex.withLock {
                when (val embedder = acquireLocked(model)) {
                    is Loaded.LiteRt -> texts.map { text ->
                        currentCoroutineContext().ensureActive()
                        embedder.engine.computeEmbeddingAsync(
                            listOf(InputData.Text(embeddingGemma2Text(text))),
                            EmbeddingOptions(normalize = true, outputSize = model.embeddingDimension ?: 768),
                        ).embedding.toList()
                    }
                    is Loaded.Legacy -> {
                        // Stored text and queries share this method, so use the symmetric task.
                        val request = EmbeddingRequest.create(
                            texts.map { EmbedData.create(it, EmbedData.TaskType.SEMANTIC_SIMILARITY) }
                        )
                        embedder.model.getBatchEmbeddings(request).awaitResult().map { it.toList() }
                    }
                }
            }
        }
    }

    /** Whether an embedding model is currently loaded (used to release memory on deletion). */
    fun currentModelKey(): String? = loaded?.key

    /**
     * Drops the resident embedding runtime before another heavyweight local workload starts.
     * The LiteRT embedding engine is explicitly closed. Legacy GemmaEmbeddingModel has no close API,
     * so clearing its last strong reference is the most deterministic release available.
     */
    suspend fun unload() = mutex.withLock {
        disposeLocked()
    }

    private fun acquireLocked(model: InstalledLocalModel): Loaded {
        // The RAG GPU delegate is less stable than CPU on many devices and gives little benefit for a
        // 300M embedder; only opt into GPU when the user explicitly forces it.
        val useGpu = model.config.accelerator == LocalAccelerator.GPU && !model.runtimeFlags.gpuCrashed
        val key = "${model.filePath}|${model.commitHash}|$useGpu"
        loaded?.let { if (it.key == key) return it }
        disposeLocked()
        PlatformLog.i(TAG, "Loading embedding model ${model.id} (gpu=$useGpu)")
        val result = if (usesLiteRtEmbeddingEngine(model.filePath)) {
            val engine = EmbeddingEngine(
                EmbeddingEngineConfig(
                    modelPath = model.filePath,
                    backend = if (useGpu) Backend.GPU() else Backend.CPU(),
                    maxInputLength = model.defaultConfig.maxContextLength ?: 8192,
                )
            )
            engine.initialize()
            Loaded.LiteRt(key, engine)
        } else {
            Loaded.Legacy(key, GemmaEmbeddingModel(model.filePath, model.tokenizerPath!!, useGpu))
        }
        loaded = result
        return result
    }

    private fun disposeLocked() {
        val previous = loaded
        loaded = null
        if (previous is Loaded.LiteRt) previous.engine.close()
    }

    private suspend fun <T> ListenableFuture<T>.awaitResult(): T =
        suspendCancellableCoroutine { cont ->
            Futures.addCallback(
                this,
                object : FutureCallback<T> {
                    override fun onSuccess(result: T) = cont.resume(result)
                    override fun onFailure(t: Throwable) = cont.resumeWithException(t)
                },
                MoreExecutors.directExecutor(),
            )
            cont.invokeOnCancellation { cancel(false) }
        }

    companion object {
        private const val TAG = "LiteRtEmbedder"
    }
}
