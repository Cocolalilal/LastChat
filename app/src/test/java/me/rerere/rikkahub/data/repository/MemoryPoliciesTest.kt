package me.rerere.rikkahub.data.repository

import me.rerere.rikkahub.data.db.entity.ChatEpisodeEntity
import me.rerere.rikkahub.data.model.AssistantMemory
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryPoliciesTest {
    @Test
    fun shouldPruneEpisode_keepsHighSignificanceEpisodesLonger() {
        val lowSignificance = episode(significance = 1, startTime = NOW - days(40), lastAccessedAt = NOW - days(20))
        val highSignificance = episode(significance = 10, startTime = NOW - days(40), lastAccessedAt = NOW - days(20))

        assertTrue(shouldPruneEpisode(lowSignificance, nowMillis = NOW))
        assertFalse(shouldPruneEpisode(highSignificance, nowMillis = NOW))
    }

    @Test
    fun needsEmbeddingRefresh_detectsMissingOrWrongModel() {
        val missingEmbedding = AssistantMemory(id = 1, hasEmbedding = false, embeddingModelId = null)
        val staleEmbedding = AssistantMemory(id = 2, hasEmbedding = true, embeddingModelId = "old-model")
        val currentEmbedding = AssistantMemory(id = 3, hasEmbedding = true, embeddingModelId = "model-a")

        assertTrue(needsEmbeddingRefresh(missingEmbedding, "model-a"))
        assertTrue(needsEmbeddingRefresh(staleEmbedding, "model-a"))
        assertFalse(needsEmbeddingRefresh(currentEmbedding, "model-a"))
        assertFalse(needsEmbeddingRefresh(currentEmbedding, ""))
    }

    private fun episode(significance: Int, startTime: Long, lastAccessedAt: Long): ChatEpisodeEntity {
        return ChatEpisodeEntity(
            id = significance,
            assistantId = "assistant",
            content = "Episode $significance",
            embedding = null,
            embeddingModelId = null,
            startTime = startTime,
            endTime = startTime,
            lastAccessedAt = lastAccessedAt,
            significance = significance,
            conversationId = "conv-$significance",
        )
    }

    private fun days(count: Long): Long = count * 24 * 60 * 60 * 1000L

    companion object {
        private const val NOW = 1_700_000_000_000L
    }
}
