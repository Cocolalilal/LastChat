package me.rerere.rikkahub.data.ai

import me.rerere.rikkahub.data.model.AssistantMemory
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.uuid.Uuid

class MemoryContextCoordinatorTest {
    @Test
    fun regenerationExcludesOnlySourcesFromTheActiveConversation() {
        val activeConversationId = Uuid.random()
        val otherConversationId = Uuid.random()
        val memories = listOf(
            AssistantMemory(
                id = 1,
                content = "reply being regenerated",
                conversationId = activeConversationId.toString(),
            ),
            AssistantMemory(
                id = 2,
                content = "memory from another chat",
                conversationId = otherConversationId.toString(),
            ),
            AssistantMemory(
                id = 3,
                content = "global core memory",
                conversationId = null,
            ),
        )

        val filtered = memories.excludingConversationSources(activeConversationId)

        assertEquals(listOf(2, 3), filtered.map { it.id })
    }

    @Test
    fun normalGenerationKeepsAllMemorySources() {
        val memories = listOf(
            AssistantMemory(id = 1, content = "one", conversationId = Uuid.random().toString()),
            AssistantMemory(id = 2, content = "two", conversationId = null),
        )

        assertEquals(memories, memories.excludingConversationSources(null))
    }
}
