package me.rerere.rikkahub.data.ai.rag

import me.rerere.ai.provider.ProviderManager
import me.rerere.ai.provider.ModelType
import me.rerere.rikkahub.data.datastore.DISABLED_MODEL_ID
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.findModelById
import me.rerere.rikkahub.data.datastore.findProvider
import kotlin.uuid.Uuid

data class EmbeddingResult(
    val embeddings: List<List<Float>>,
    val modelId: String
)

sealed interface EmbeddingAvailability {
    data class Available(val modelId: String, val modelName: String) : EmbeddingAvailability
    data class Unavailable(val reason: String) : EmbeddingAvailability
}

class EmbeddingUnavailableException(message: String, cause: Throwable? = null) :
    IllegalStateException(message, cause)

class EmbeddingService(
    private val providerManager: ProviderManager,
    private val settingsStore: SettingsStore
) {
    fun getAvailability(assistantId: String? = null): EmbeddingAvailability {
        val settings = settingsStore.settingsFlow.value
        val modelId = selectedModelId(settings, assistantId)
        return getAvailability(settings, modelId)
    }

    private fun getAvailability(
        settings: Settings,
        modelId: Uuid,
    ): EmbeddingAvailability {
        if (modelId == DISABLED_MODEL_ID) {
            return EmbeddingAvailability.Unavailable("No embedding model is selected. Lexical memory retrieval remains available.")
        }
        val model = settings.findModelById(modelId)
            ?: return EmbeddingAvailability.Unavailable("The selected embedding model is no longer available. Choose an installed or configured embedding model.")
        if (model.type != ModelType.EMBEDDING) {
            return EmbeddingAvailability.Unavailable("The selected model is not an embedding model.")
        }
        val providerSetting = model.findProvider(settings.providers)
            ?: return EmbeddingAvailability.Unavailable("The provider for the selected embedding model is unavailable.")
        if (!providerSetting.enabled) {
            return EmbeddingAvailability.Unavailable("The provider for the selected embedding model is disabled.")
        }
        val provider = runCatching { providerManager.getProviderByType(providerSetting) }.getOrNull()
            ?: return EmbeddingAvailability.Unavailable("The embedding provider is not registered in this runtime.")
        if (!provider.supportsEmbeddings) {
            return EmbeddingAvailability.Unavailable("${providerSetting::class.simpleName} does not support embeddings.")
        }
        return EmbeddingAvailability.Available(modelId.toString(), model.displayName.ifBlank { model.modelId })
    }

    private fun selectedModelId(
        settings: Settings,
        assistantId: String?,
    ) = settings.let {
        if (assistantId != null) {
            it.assistants.find { assistant -> assistant.id.toString() == assistantId }?.embeddingModelId
                ?: it.embeddingModelId
        } else {
            it.embeddingModelId
        }
    }

    /**
     * Get the current embedding model ID for an assistant (or global if not set)
     */
    fun getEmbeddingModelId(assistantId: String? = null): String {
        val settings = settingsStore.settingsFlow.value
        return selectedModelId(settings, assistantId).toString()
    }

    suspend fun embed(text: String, assistantId: String? = null): List<Float> {
        return embedBatch(listOf(text), assistantId).embeddings.first()
    }

    suspend fun embedWithModelId(text: String, assistantId: String? = null): EmbeddingResult {
        val result = embedBatch(listOf(text), assistantId)
        return EmbeddingResult(result.embeddings, result.modelId)
    }

    suspend fun embedBatch(texts: List<String>, assistantId: String? = null): EmbeddingResult {
        val settings = settingsStore.settingsFlow.value
        val modelId = selectedModelId(settings, assistantId)
        if (texts.isEmpty()) return EmbeddingResult(emptyList(), modelId.toString())
        val availability = getAvailability(settings, modelId)
        if (availability is EmbeddingAvailability.Unavailable) {
            throw EmbeddingUnavailableException(availability.reason)
        }

        val model = settings.findModelById(modelId)
            ?: throw EmbeddingUnavailableException("Embedding model not found: $modelId")
        
        // Check if provider supports embeddings
        val providerSetting = model.findProvider(settings.providers)
            ?: throw EmbeddingUnavailableException("Provider not found for embedding model")
        val provider = providerManager.getProviderByType(providerSetting)
        val embeddingResult = try {
            provider.createEmbedding(providerSetting, texts, model)
        } catch (throwable: Throwable) {
            if (throwable is kotlinx.coroutines.CancellationException) throw throwable
            throw EmbeddingUnavailableException(
                "Embedding request failed for ${model.displayName.ifBlank { model.modelId }}: ${throwable.message ?: throwable::class.simpleName}",
                throwable,
            )
        }
        if (embeddingResult.size != texts.size) {
            throw EmbeddingUnavailableException(
                "Embedding provider returned ${embeddingResult.size} vectors for ${texts.size} inputs.",
            )
        }
        val dimension = embeddingResult.firstOrNull()?.size ?: 0
        if (dimension <= 0 || embeddingResult.any { vector ->
                vector.size != dimension || vector.any { value -> !value.isFinite() }
            }
        ) {
            throw EmbeddingUnavailableException("Embedding provider returned invalid or inconsistent vectors.")
        }
        
        return EmbeddingResult(embeddingResult, modelId.toString())
    }
}

