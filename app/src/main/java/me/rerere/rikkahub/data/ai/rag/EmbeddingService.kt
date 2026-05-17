package me.rerere.rikkahub.data.ai.rag

import me.rerere.ai.provider.ProviderManager
import me.rerere.rikkahub.data.datastore.DISABLED_MODEL_ID
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.findModelById
import me.rerere.rikkahub.data.datastore.findProvider

data class EmbeddingResult(
    val embeddings: List<List<Float>>,
    val modelId: String
)

class EmbeddingService(
    private val providerManager: ProviderManager,
    private val settingsStore: SettingsStore
) {
    /**
     * Get the current embedding model ID for an assistant (or global if not set)
     */
    fun getEmbeddingModelId(assistantId: String? = null): String {
        val settings = settingsStore.settingsFlow.value
        val modelId = if (assistantId != null) {
            val assistant = settings.assistants.find { it.id.toString() == assistantId }
            assistant?.embeddingModelId ?: settings.embeddingModelId
        } else {
            settings.embeddingModelId
        }
        return modelId.toString()
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
        
        // Use assistant embedding model if available, otherwise use global
        val modelId = if (assistantId != null) {
            val assistant = settings.assistants.find { it.id.toString() == assistantId }
            assistant?.embeddingModelId ?: settings.embeddingModelId
        } else {
            settings.embeddingModelId
        }
        check(modelId != DISABLED_MODEL_ID) {
            "Embedding model is disabled."
        }
        
        val model = settings.findModelById(modelId) ?: error("Embedding model not found: $modelId")
        
        // Check if provider supports embeddings
        val providerSetting = model.findProvider(settings.providers) ?: error("Provider not found for embedding model")
        val provider = providerManager.getProviderByType(providerSetting)
        
        // Check if provider supports embeddings (OpenAI does, others may not)
        val embeddingResult = provider.createEmbedding(providerSetting, texts, model)
        if (embeddingResult.isEmpty() && texts.isNotEmpty()) {
            error("Provider ${providerSetting::class.simpleName} does not support embeddings or returned empty result")
        }
        
        return EmbeddingResult(embeddingResult, modelId.toString())
    }
}

