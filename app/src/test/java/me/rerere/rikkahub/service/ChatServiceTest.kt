package me.rerere.rikkahub.service

import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.ToolApprovalState
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.MessageNode
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import kotlin.uuid.Uuid

class ChatServiceTest {
    @Test
    fun dropDanglingAutoToolCallNodes_removesOnlyAutoToolCallsWithoutResults() {
        val autoNode = MessageNode.of(
            UIMessage(
                role = MessageRole.ASSISTANT,
                parts = listOf(
                    UIMessagePart.ToolCall(
                        toolCallId = "auto-call",
                        toolName = "search_web",
                        arguments = """{"query":"weather"}""",
                    )
                ),
            )
        )
        val pendingNode = MessageNode.of(
            UIMessage(
                role = MessageRole.ASSISTANT,
                parts = listOf(
                    UIMessagePart.ToolCall(
                        toolCallId = "pending-call",
                        toolName = "ask_user",
                        arguments = """{"questions":[{"id":"scope","question":"Which scope?"}]}""",
                        approvalState = ToolApprovalState.Pending,
                    )
                ),
            )
        )

        val cleaned = dropDanglingAutoToolCallNodes(listOf(autoNode, pendingNode))

        assertTrue(cleaned[0].messages.isEmpty())
        assertEquals(1, cleaned[1].messages.size)
        assertEquals("pending-call", cleaned[1].currentMessage.getToolCalls().single().toolCallId)
    }

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
    fun hasDurableAssistantProgressRequiresVisibleAssistantWork() {
        assertFalse(
            UIMessage(
                role = MessageRole.ASSISTANT,
                parts = listOf(UIMessagePart.Text("   ")),
            ).hasDurableAssistantProgress()
        )
        assertFalse(UIMessage.user("hello").hasDurableAssistantProgress())
        assertTrue(UIMessage.assistant("reply").hasDurableAssistantProgress())
        assertTrue(
            UIMessage(
                role = MessageRole.ASSISTANT,
                parts = listOf(UIMessagePart.Reasoning("thinking")),
            ).hasDurableAssistantProgress()
        )
        assertTrue(
            UIMessage(
                role = MessageRole.ASSISTANT,
                parts = listOf(
                    UIMessagePart.ToolCall(
                        toolCallId = "call",
                        toolName = "search_web",
                        arguments = "{}",
                    )
                ),
            ).hasDurableAssistantProgress()
        )
        assertFalse(
            UIMessage(
                role = MessageRole.ASSISTANT,
                parts = emptyList(),
                annotations = listOf(
                    me.rerere.ai.ui.UIMessageAnnotation.OcrActivity(
                        source = me.rerere.ai.ui.UIMessageAnnotation.OcrActivity.Source.IMAGE,
                        fileName = "photo.png",
                    )
                ),
            ).hasDurableAssistantProgress()
        )
    }

    @Test
    fun shouldPersistStreamingCheckpointThrottlesDurableAssistantProgress() {
        val conversation = Conversation.ofId(
            id = Uuid.random(),
            messages = listOf(
                MessageNode.of(UIMessage.user("hi")),
                MessageNode.of(UIMessage.assistant("partial")),
            ),
        )

        assertTrue(
            shouldPersistStreamingCheckpoint(
                conversation = conversation,
                nowMs = 10_000L,
                lastPersistMs = 0L,
            )
        )
        assertFalse(
            shouldPersistStreamingCheckpoint(
                conversation = conversation,
                nowMs = 10_500L,
                lastPersistMs = 10_000L,
            )
        )
        assertTrue(
            shouldPersistStreamingCheckpoint(
                conversation = conversation,
                nowMs = 11_000L,
                lastPersistMs = 10_000L,
            )
        )
    }

    @Test
    fun needsAssistantReplyAfterToolResultDetectsTrailingToolResult() {
        val conversation = Conversation.ofId(
            id = Uuid.random(),
            messages = listOf(
                MessageNode.of(UIMessage.user("search it")),
                MessageNode.of(
                    UIMessage(
                        role = MessageRole.ASSISTANT,
                        parts = listOf(
                            UIMessagePart.ToolCall(
                                toolCallId = "call-1",
                                toolName = "search_web",
                                arguments = """{"query":"kotlin"}""",
                            )
                        ),
                    )
                ),
                MessageNode.of(
                    UIMessage(
                        role = MessageRole.TOOL,
                        parts = listOf(
                            UIMessagePart.ToolResult(
                                toolCallId = "call-1",
                                toolName = "search_web",
                                content = JsonPrimitive("result"),
                                arguments = JsonPrimitive("""{"query":"kotlin"}"""),
                            )
                        ),
                    )
                ),
            ),
        )

        assertTrue(conversation.needsAssistantReplyAfterToolResult())
    }

    @Test
    fun needsAssistantReplyAfterToolResultDetectsBlankAssistantAfterToolResult() {
        val conversation = Conversation.ofId(
            id = Uuid.random(),
            messages = listOf(
                MessageNode.of(UIMessage.user("search it")),
                MessageNode.of(
                    UIMessage(
                        role = MessageRole.TOOL,
                        parts = listOf(
                            UIMessagePart.ToolResult(
                                toolCallId = "call-1",
                                toolName = "search_web",
                                content = JsonPrimitive("result"),
                                arguments = JsonPrimitive("""{"query":"kotlin"}"""),
                            )
                        ),
                    )
                ),
                MessageNode.of(
                    UIMessage(
                        role = MessageRole.ASSISTANT,
                        parts = emptyList(),
                    )
                ),
            ),
        )

        assertTrue(conversation.needsAssistantReplyAfterToolResult())
    }

    @Test
    fun needsAssistantReplyAfterToolResultIgnoresVisibleAssistantReply() {
        val conversation = Conversation.ofId(
            id = Uuid.random(),
            messages = listOf(
                MessageNode.of(UIMessage.user("search it")),
                MessageNode.of(
                    UIMessage(
                        role = MessageRole.TOOL,
                        parts = listOf(
                            UIMessagePart.ToolResult(
                                toolCallId = "call-1",
                                toolName = "search_web",
                                content = JsonPrimitive("result"),
                                arguments = JsonPrimitive("""{"query":"kotlin"}"""),
                            )
                        ),
                    )
                ),
                MessageNode.of(UIMessage.assistant("Here is the answer.")),
            ),
        )

        assertFalse(conversation.needsAssistantReplyAfterToolResult())
    }

    @Test
    fun canAutoResumeAssistantReplyAllowsVisiblePartialReply() {
        val conversation = Conversation.ofId(
            id = Uuid.random(),
            messages = listOf(
                MessageNode.of(UIMessage.user("hi")),
                MessageNode.of(UIMessage.assistant("partial reply")),
            ),
        )

        assertTrue(conversation.canAutoResumeAssistantReply())
    }

    @Test
    fun canAutoResumeAssistantReplyAllowsReasoningOnlyStall() {
        val conversation = Conversation.ofId(
            id = Uuid.random(),
            messages = listOf(
                MessageNode.of(UIMessage.user("hi")),
                MessageNode.of(
                    UIMessage(
                        role = MessageRole.ASSISTANT,
                        parts = listOf(UIMessagePart.Reasoning("thinking hard")),
                    )
                ),
            ),
        )

        assertTrue(conversation.canAutoResumeAssistantReply())
    }

    @Test
    fun mergeLiveMessagesIfIncomingIsStaleKeepsGeneratedAssistantReply() {
        val conversationId = Uuid.parse("00000000-0000-0000-0000-000000000501")
        val userMessage = UIMessage.user("hi").copy(
            id = Uuid.parse("00000000-0000-0000-0000-000000000502"),
        )
        val assistantMessage = UIMessage.assistant("finished reply").copy(
            id = Uuid.parse("00000000-0000-0000-0000-000000000503"),
        )
        val liveConversation = Conversation.ofId(
            id = conversationId,
            messages = listOf(
                MessageNode.of(userMessage),
                MessageNode.of(assistantMessage),
            ),
        ).copy(
            updateAt = Instant.parse("2026-05-26T10:00:05Z"),
        )
        val staleMetadataUpdate = Conversation.ofId(
            id = conversationId,
            messages = listOf(MessageNode.of(userMessage)),
        ).copy(
            title = "Fresh title",
            chatSuggestions = listOf("Next"),
            updateAt = Instant.parse("2026-05-26T10:00:00Z"),
        )

        val merged = mergeLiveMessagesIfIncomingIsStale(
            liveConversation = liveConversation,
            incomingConversation = staleMetadataUpdate,
        )

        assertEquals("Fresh title", merged.title)
        assertEquals(listOf("Next"), merged.chatSuggestions)
        assertEquals(2, merged.messageNodes.size)
        assertEquals("finished reply", merged.currentMessages.last().toContentText())
    }

    @Test
    fun mergeLiveMessagesIfIncomingIsStaleAllowsNewerMessageRemoval() {
        val conversationId = Uuid.parse("00000000-0000-0000-0000-000000000511")
        val userMessage = UIMessage.user("hi").copy(
            id = Uuid.parse("00000000-0000-0000-0000-000000000512"),
        )
        val assistantMessage = UIMessage.assistant("finished reply").copy(
            id = Uuid.parse("00000000-0000-0000-0000-000000000513"),
        )
        val liveConversation = Conversation.ofId(
            id = conversationId,
            messages = listOf(
                MessageNode.of(userMessage),
                MessageNode.of(assistantMessage),
            ),
        ).copy(
            updateAt = Instant.parse("2026-05-26T10:00:00Z"),
        )
        val newerDeletion = Conversation.ofId(
            id = conversationId,
            messages = listOf(MessageNode.of(userMessage)),
        ).copy(
            updateAt = Instant.parse("2026-05-26T10:00:05Z"),
        )

        val merged = mergeLiveMessagesIfIncomingIsStale(
            liveConversation = liveConversation,
            incomingConversation = newerDeletion,
        )

        assertEquals(1, merged.messageNodes.size)
        assertEquals("hi", merged.currentMessages.single().toContentText())
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
    fun normalizeConversationDropsEmptyOcrPlaceholders() {
        val conversation = Conversation.ofId(
            id = Uuid.random(),
            messages = listOf(
                MessageNode.of(UIMessage.user("look at this")),
                MessageNode.of(
                    UIMessage(
                        role = MessageRole.ASSISTANT,
                        parts = emptyList(),
                        annotations = listOf(
                            me.rerere.ai.ui.UIMessageAnnotation.OcrActivity(
                                source = me.rerere.ai.ui.UIMessageAnnotation.OcrActivity.Source.IMAGE,
                                fileName = "photo.png",
                            )
                        ),
                    )
                ),
                MessageNode.of(UIMessage.assistant("actual reply")),
            ),
        )

        val normalized = normalizeConversation(conversation)

        assertEquals(2, normalized.messageNodes.size)
        assertEquals("actual reply", normalized.currentMessages.last().toContentText())
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

        val fork = runBlocking {
            buildForkConversationSnapshot(
                conversation = conversation,
                messageId = messageId,
                copyAttachmentUrl = { url -> "$url-copy" },
                newConversationId = Uuid.parse("00000000-0000-0000-0000-000000000404"),
                now = now,
            )
        }

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
