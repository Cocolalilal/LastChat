package me.rerere.rikkahub.data.ai.rag

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import me.rerere.locallm.InstalledLocalModel
import me.rerere.locallm.LiteRtEmbedder
import me.rerere.locallm.LocalModelStore
import me.rerere.locallm.TEXT_EMBEDDING_MODEL_ID
import me.rerere.locallm.hasUsableTextEmbeddingFile
import me.rerere.rikkahub.AppScope
import java.io.File
import java.security.MessageDigest

data class EmbeddingResult(val embeddings: List<List<Float>>, val modelId: String)
sealed interface EmbeddingAvailability {
    data class Available(val modelId: String, val modelName: String) : EmbeddingAvailability
    data class Unavailable(val reason: String) : EmbeddingAvailability
}
class EmbeddingUnavailableException(message: String, cause: Throwable? = null) : IllegalStateException(message, cause)

/** One local vector space. Deprecated provider selections are never executed. */
class EmbeddingService(private val store: LocalModelStore, private val embedder: LiteRtEmbedder, scope: AppScope,
    private val inferenceManager: me.rerere.common.inference.LocalInferenceManager) : me.rerere.rikkahub.data.memory.MemoryEmbeddingClient {
    private val installed = store.models.stateIn(scope, SharingStarted.Eagerly, emptyList())
    @Volatile private var verified: Pair<String, String>? = null
    private fun model() = installed.value.firstOrNull { it.id == TEXT_EMBEDDING_MODEL_ID && it.isEmbedding }
    private fun stamp(model: InstalledLocalModel): String {
        val file = File(model.filePath)
        return "${model.commitHash}|${model.filePath}|${file.length()}|${file.lastModified()}"
    }
    @Suppress("UNUSED_PARAMETER")
    fun getAvailability(assistantId: String? = null): EmbeddingAvailability {
        val model = model()
        return if (model != null && model.hasUsableTextEmbeddingFile()) {
            EmbeddingAvailability.Available(getEmbeddingModelId(), "EmbeddingGemma 2 Text")
        } else EmbeddingAvailability.Unavailable("Download EmbeddingGemma 2 Text to enable semantic recall. Your memories remain available.")
    }
    @Suppress("UNUSED_PARAMETER")
    override fun getEmbeddingModelId(assistantId: String?): String {
        val model = model() ?: return "embeddinggemma2:missing"
        val stamp = stamp(model)
        return verified?.takeIf { it.first == stamp }?.second ?: "embeddinggemma2:unverified:$stamp"
    }
    suspend fun embed(text: String, assistantId: String? = null): List<Float> = embedBatch(listOf(text), assistantId).embeddings.first()
    override suspend fun embedWithModelId(text: String, assistantId: String?): EmbeddingResult = embedBatch(listOf(text), assistantId)
    @Suppress("UNUSED_PARAMETER")
    suspend fun embedBatch(texts: List<String>, assistantId: String? = null): EmbeddingResult = withContext(Dispatchers.IO) {
        val model = store.current().firstOrNull { it.id == TEXT_EMBEDDING_MODEL_ID && it.isEmbedding }
            ?: throw EmbeddingUnavailableException("Download EmbeddingGemma 2 Text first.")
        val file = File(model.filePath)
        if (!model.hasUsableTextEmbeddingFile()) throw EmbeddingUnavailableException("EmbeddingGemma 2 Text is missing. Download it again.")
        val before = stamp(model)
        val key = verified?.takeIf { it.first == before }?.second ?: run {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().buffered().use { input ->
                val buffer = ByteArray(65536)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    digest.update(buffer, 0, count)
                }
            }
            val sha = digest.digest().joinToString("") { "%02x".format(it) }
            "embeddinggemma2:$sha:similarity-v1:768:unit".also { verified = before to it }
        }
        try {
            val values = inferenceManager.withLease(me.rerere.common.inference.LocalInferenceWorkload.EMBEDDING, model.id) {
                embedder.embed(model.copy(embeddingDimension = 768), texts)
            }
            check(stamp(model) == before) { "Embedding model changed during inference. Retry the operation." }
            check(values.size == texts.size && values.all { v -> v.size == 768 && v.all(Float::isFinite) && v.any { it != 0f } }) {
                "Embedding runtime returned invalid vectors."
            }
            EmbeddingResult(values, key)
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            throw EmbeddingUnavailableException(error.message ?: "Local embedding failed", error)
        }
    }
}
