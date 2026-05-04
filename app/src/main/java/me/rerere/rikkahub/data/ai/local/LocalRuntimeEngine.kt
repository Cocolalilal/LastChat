package me.rerere.rikkahub.data.ai.local

import kotlinx.coroutines.flow.Flow
import me.rerere.ai.provider.TextGenerationParams
import me.rerere.ai.ui.MessageChunk
import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.data.db.entity.LocalModelInstallEntity

data class LocalRuntimeStats(
    val promptTokens: Int? = null,
    val completionTokens: Int? = null,
    val durationMs: Long? = null,
)

interface LocalRuntimeEngine {
    suspend fun load(install: LocalModelInstallEntity)

    suspend fun warmup(install: LocalModelInstallEntity)

    suspend fun generate(
        install: LocalModelInstallEntity,
        messages: List<UIMessage>,
        params: TextGenerationParams,
    ): MessageChunk

    fun stream(
        install: LocalModelInstallEntity,
        messages: List<UIMessage>,
        params: TextGenerationParams,
    ): Flow<MessageChunk>

    suspend fun createEmbeddings(
        install: LocalModelInstallEntity,
        input: List<String>,
    ): List<List<Float>> {
        throw LocalRuntimeUnavailableException("This local runtime does not support embeddings.")
    }

    suspend fun cancel()

    suspend fun unload()

    suspend fun getStats(): LocalRuntimeStats?
}

class LocalRuntimeBusyException : IllegalStateException("Another local generation is already active.")

class LocalRuntimeUnavailableException(message: String) : IllegalStateException(message)
