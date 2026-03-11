package me.rerere.rikkahub.ui.pages.assistant.detail

import me.rerere.rikkahub.data.model.AssistantMemory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AssistantMemorySubPageTest {
    @Test
    fun memoryEmbeddingStatusLabel_returnsWrongModelForMismatchedEmbedding() {
        val memory = AssistantMemory(
            id = 1,
            content = "User likes espresso",
            hasEmbedding = true,
            embeddingModelId = "model-a",
        )

        assertEquals(
            "WRONG MODEL",
            memoryEmbeddingStatusLabel(
                memory = memory,
                currentEmbeddingModelId = "model-b",
                useRagMemoryRetrieval = true,
            )
        )
    }

    @Test
    fun memoryEmbeddingStatusLabel_returnsNoEmbeddingWhenMissing() {
        val memory = AssistantMemory(
            id = 1,
            content = "User likes espresso",
            hasEmbedding = false,
            embeddingModelId = null,
        )

        assertEquals(
            "NO EMBEDDING",
            memoryEmbeddingStatusLabel(
                memory = memory,
                currentEmbeddingModelId = "model-a",
                useRagMemoryRetrieval = true,
            )
        )
    }

    @Test
    fun memoryEmbeddingStatusLabel_returnsNullWhenEmbeddingIsCurrent() {
        val memory = AssistantMemory(
            id = 1,
            content = "User likes espresso",
            hasEmbedding = true,
            embeddingModelId = "model-a",
        )

        assertNull(
            memoryEmbeddingStatusLabel(
                memory = memory,
                currentEmbeddingModelId = "model-a",
                useRagMemoryRetrieval = true,
            )
        )
    }
}
