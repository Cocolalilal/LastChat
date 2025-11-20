package me.rerere.rikkahub.data.ai.rag

import me.rerere.ai.provider.ProviderManager
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.findModelById
import me.rerere.rikkahub.data.datastore.findProvider

class EmbeddingService(
    private val providerManager: ProviderManager,
    private val settingsStore: SettingsStore
) {
    suspend fun embed(text: String, assistantId: String? = null): List<Float> {
        return embedBatch(listOf(text), assistantId).first()
    }

    suspend fun embedBatch(texts: List<String>, assistantId: String? = null): List<List<Float>> {
        val settings = settingsStore.settingsFlow.value
        
        // Use assistant embedding model if available, otherwise use global
        val modelId = if (assistantId != null) {
            val assistant = settings.assistants.find { it.id.toString() == assistantId }
            assistant?.embeddingModelId ?: settings.embeddingModelId
        } else {
            settings.embeddingModelId
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
        
        return embeddingResult
    }
}
