package me.rerere.rikkahub.data.memory

import me.rerere.rikkahub.data.ai.rag.EmbeddingResult

interface MemoryEmbeddingClient {
    fun getEmbeddingModelId(assistantId: String? = null): String
    suspend fun embedWithModelId(text: String, assistantId: String? = null): EmbeddingResult
}
