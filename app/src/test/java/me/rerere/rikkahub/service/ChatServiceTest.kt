package me.rerere.rikkahub.service

import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.MessageNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.uuid.Uuid

class ChatServiceTest {
    @Test
    fun shouldPreserveInMemoryConversationKeepsAssistantSeededDrafts() {
        val conversation = Conversation.ofId(
            id = Uuid.random(),
            messages = listOf(MessageNode.of(UIMessage.assistant("Hey there"))),
        )

        assertTrue(
            shouldPreserveInMemoryConversation(
                conversation = conversation,
                persistenceMode = ChatPersistenceMode.PERSIST_ON_REPLY,
            )
        )
    }

    @Test
    fun shouldPreserveInMemoryConversationKeepsPopulatedNormalChats() {
        val conversation = Conversation.ofId(
            id = Uuid.random(),
            messages = listOf(MessageNode.of(UIMessage.assistant("Follow-up"))),
        )

        assertTrue(
            shouldPreserveInMemoryConversation(
                conversation = conversation,
                persistenceMode = ChatPersistenceMode.NORMAL,
            )
        )
    }

    @Test
    fun shouldPreserveInMemoryConversationRejectsEmptyNormalChats() {
        val conversation = Conversation.ofId(id = Uuid.random())

        assertFalse(
            shouldPreserveInMemoryConversation(
                conversation = conversation,
                persistenceMode = ChatPersistenceMode.NORMAL,
            )
        )
    }

    @Test
    fun appendStandaloneAssistantTurnAddsSeparateMessageNode() {
        val existingNode = MessageNode.of(UIMessage.assistant("Original"))
        val conversation = Conversation.ofId(
            id = Uuid.random(),
            messages = listOf(existingNode),
        )

        val updated = appendStandaloneAssistantTurn(
            conversation = conversation,
            content = "Follow-up",
        )

        assertEquals(2, updated.messageNodes.size)
        assertNotSame(existingNode, updated.messageNodes.last())
        assertEquals("Original", updated.messageNodes.first().currentMessage.toText())
        assertEquals("Follow-up", updated.messageNodes.last().currentMessage.toText())
        assertEquals(1, updated.messageNodes.first().messages.size)
        assertEquals(1, updated.messageNodes.last().messages.size)
    }
}
