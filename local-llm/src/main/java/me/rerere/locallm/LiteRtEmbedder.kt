package me.rerere.locallm

import android.content.Context
import android.os.Build
import com.google.ai.edge.localagents.rag.models.EmbedData
import com.google.ai.edge.localagents.rag.models.EmbeddingRequest
import com.google.ai.edge.localagents.rag.models.GemmaEmbeddingModel
import com.google.common.util.concurrent.FutureCallback
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import me.rerere.common.platform.PlatformLog
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * On-device text embedding runtime, the embedding counterpart of [LiteRtRuntime]. Drives the AI Edge
 * RAG [GemmaEmbeddingModel] (EmbeddingGemma) — a separate native runtime from the LiteRT-LM generation
 * engine, which has no embedding API.
 *
 * Keeps a single embedding model resident (they are ~180MB) and serializes calls, since the underlying
 * native model is not guaranteed thread-safe. The RAG native libraries ship for `arm64-v8a` only, so
 * [isSupported] gates the feature on other ABIs (x86_64 emulators, legacy 32-bit devices).
 */
class LiteRtEmbedder(@Suppress("unused") private val context: Context) {

    private val mutex = Mutex()

    @Volatile
    private var loaded: Loaded? = null

    private class Loaded(val key: String, val model: GemmaEmbeddingModel)

    /** True when this device's ABI can run the RAG embedding native libraries. */
    val isSupported: Boolean
        get() = Build.SUPPORTED_ABIS?.any { it.equals(REQUIRED_ABI, ignoreCase = true) } == true

    /**
     * Embeds [texts] with the installed embedding [model], returning one vector per input. Loads the
     * model on first use (disposing any previously loaded one). Throws with a stable key message on
     * unsupported ABI or missing files.
     */
    suspend fun embed(model: InstalledLocalModel, texts: List<String>): List<List<Float>> {
        check(model.kind == LocalModelKind.EMBEDDING) { "not_embedding_model" }
        check(isSupported) { "embedding_unsupported_abi" }
        val tokenizerPath = model.tokenizerPath ?: error("tokenizer_missing")
        check(File(model.filePath).exists()) { "embedding_model_missing" }
        check(File(tokenizerPath).exists()) { "embedding_tokenizer_missing" }
        if (texts.isEmpty()) return emptyList()

        return mutex.withLock {
            val embedder = acquireLocked(model, tokenizerPath)
            // A symmetric task type: stored documents and the query both flow through this one method,
            // so they must be embedded into the same space (RETRIEVAL_QUERY/DOCUMENT would split them).
            val request = EmbeddingRequest.create(
                texts.map { EmbedData.create(it, EmbedData.TaskType.SEMANTIC_SIMILARITY) }
            )
            embedder.getBatchEmbeddings(request).awaitResult().map { it.toList() }
        }
    }

    /** Whether an embedding model is currently loaded (used to release memory on deletion). */
    fun currentModelKey(): String? = loaded?.key

    /**
     * Drops the resident embedding runtime before another heavyweight local workload starts.
     * GemmaEmbeddingModel currently exposes no close API, so clearing the last strong reference is
     * the most deterministic release available to the host.
     */
    suspend fun unload() = mutex.withLock {
        loaded = null
    }

    private fun acquireLocked(model: InstalledLocalModel, tokenizerPath: String): GemmaEmbeddingModel {
        // The RAG GPU delegate is less stable than CPU on many devices and gives little benefit for a
        // 300M embedder; only opt into GPU when the user explicitly forces it.
        val useGpu = model.config.accelerator == LocalAccelerator.GPU
        val key = "${model.filePath}|$useGpu"
        loaded?.let { if (it.key == key) return it.model }
        loaded = null // GemmaEmbeddingModel exposes no dispose; drop the reference and let it be GC'd.
        PlatformLog.i(TAG, "Loading embedding model ${model.id} (gpu=$useGpu)")
        val embedder = GemmaEmbeddingModel(model.filePath, tokenizerPath, useGpu)
        loaded = Loaded(key, embedder)
        return embedder
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
        private const val REQUIRED_ABI = "arm64-v8a"
    }
}
