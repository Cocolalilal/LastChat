package me.rerere.rikkahub.service

import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.MessageNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
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
    fun normalizeConversationDropsEmptyNodesAndRepairsAssistantTurnSelection() {
        val conversation = Conversation.ofId(
            id = Uuid.random(),
            messages = listOf(
                MessageNode.of(UIMessage.user("hi")),
                MessageNode(
                    messages = listOf(
                        UIMessage.assistant("assistant v1").copy(versionTag = "v1"),
                        UIMessage.assistant("assistant v2").copy(versionTag = "v2"),
                    ),
                    selectIndex = 1,
                ),
                MessageNode(messages = emptyList(), selectIndex = 0),
                MessageNode(
                    messages = listOf(
                        UIMessage.assistant("tool result").copy(versionTag = "v1"),
                    ),
                    selectIndex = 4,
                ),
            ),
        )

        val normalized = normalizeConversation(conversation)

        assertEquals(3, normalized.messageNodes.size)
        assertEquals(1, normalized.messageNodes[1].selectIndex)
        assertEquals(0, normalized.messageNodes[2].selectIndex)
        assertEquals("v2", normalized.messageNodes[1].currentMessage.versionTag)
        assertEquals("v1", normalized.messageNodes[2].currentMessage.versionTag)
    }

    @Test
    fun selectConversationTurnVersionFallsBackWithoutEmptyingAssistantTurn() {
        val targetNodeId = Uuid.random()
        val conversation = Conversation.ofId(
            id = Uuid.random(),
            messages = listOf(
                MessageNode.of(UIMessage.user("hi")),
                MessageNode(
                    id = targetNodeId,
                    messages = listOf(
                        UIMessage.assistant("draft 1").copy(versionTag = "v1"),
                        UIMessage.assistant("draft 2").copy(versionTag = "v2"),
                    ),
                    selectIndex = 0,
                ),
                MessageNode(
                    messages = listOf(
                        UIMessage.assistant("tool 1").copy(versionTag = "v1"),
                        UIMessage.assistant("tool 2").copy(versionTag = "v3"),
                    ),
                    selectIndex = 0,
                ),
            ),
        )

        val updated = selectConversationTurnVersion(
            conversation = conversation,
            nodeId = targetNodeId,
            selectIndex = 1,
        )

        assertEquals(1, updated.messageNodes[1].selectIndex)
        assertEquals(1, updated.messageNodes[2].selectIndex)
        assertFalse(updated.messageNodes.any { it.messages.isEmpty() })
    }

    @Test
    fun buildForkConversationSnapshotKeepsAssistantAndCopiesAttachments() {
        val assistantId = Uuid.parse("00000000-0000-0000-0000-000000000401")
        val messageId = Uuid.parse("00000000-0000-0000-0000-000000000402")
        val now = Instant.parse("2026-03-14T12:00:00Z")
        val conversation = Conversation.ofId(
            id = Uuid.parse("00000000-0000-0000-0000-000000000403"),
            assistantId = assistantId,
            messages = listOf(
                MessageNode.of(UIMessage.user("hi")),
                MessageNode.of(
                    UIMessage(
                        role = MessageRole.ASSISTANT,
                        parts = listOf(
                            UIMessagePart.Image(url = "file:///tmp/image.png")
                        )
                    ).copy(id = messageId)
                ),
                MessageNode.of(UIMessage.assistant("after fork")),
            ),
        )

        val fork = buildForkConversationSnapshot(
            conversation = conversation,
            messageId = messageId,
            copyAttachmentUrl = { url -> "$url-copy" },
            newConversationId = Uuid.parse("00000000-0000-0000-0000-000000000404"),
            now = now,
        )

        assertNotNull(fork)
        assertEquals(assistantId, fork!!.assistantId)
        assertEquals(Uuid.parse("00000000-0000-0000-0000-000000000404"), fork.id)
        assertEquals(now, fork.createAt)
        assertEquals(now, fork.updateAt)
        assertEquals(2, fork.messageNodes.size)
        val image = fork.messageNodes[1].currentMessage.parts.filterIsInstance<UIMessagePart.Image>().single()
        assertEquals("file:///tmp/image.png-copy", image.url)
    }
}
