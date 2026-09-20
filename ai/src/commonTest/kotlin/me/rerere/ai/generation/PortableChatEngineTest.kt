package me.rerere.ai.generation

import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.MessageNode
import me.rerere.ai.ui.ToolApprovalState
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.ai.ui.currentVersionMessages
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class PortableChatEngineTest {
    private val engine = PortableChatEngine()

    @Test
    fun editMessageBranchesNodeAndKeepsVersionTag() {
        val original = UIMessage.user("Hi")
        val nodes = listOf(MessageNode.of(original))
        val edited = engine.editMessage(
            nodes = nodes,
            messageId = original.id,
            parts = listOf(UIMessagePart.Text("Hello")),
        )
        assertEquals(2, edited.single().messages.size)
        assertEquals("Hello", edited.single().currentMessage.toText())
        assertEquals(original.versionTag, edited.single().currentMessage.versionTag)
    }

    @Test
    fun forkThroughMessageCopiesPrefix() {
        val user = UIMessage.user("Hi")
        val assistant = UIMessage.assistant("Answer")
        val after = UIMessage.user("More")
        val nodes = listOf(MessageNode.of(user), MessageNode.of(assistant), MessageNode.of(after))
        val fork = assertNotNull(engine.forkThroughMessage(nodes, assistant.id))
        assertEquals(listOf("Hi", "Answer"), fork.currentVersionMessages().map { it.toText() })
    }

    @Test
    fun selectTurnVersionSwitchesAssistantSiblings() {
        val targetId = Uuid.random()
        val nodes = listOf(
            MessageNode.of(UIMessage.user("hi")),
            MessageNode(
                id = targetId,
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
        )
        val updated = engine.selectTurnVersion(nodes, targetId, 1)
        assertEquals(1, updated[1].selectIndex)
        assertEquals(1, updated[2].selectIndex)
        assertFalse(updated.any { it.messages.isEmpty() })
    }

    @Test
    fun deleteUserMessageTruncatesFollowingNodes() {
        val user = UIMessage.user("Hi")
        val assistant = UIMessage.assistant("Answer")
        val deleted = engine.deleteMessage(
            listOf(MessageNode.of(user), MessageNode.of(assistant)),
            user.id,
        )
        assertTrue(deleted.isEmpty())
    }

    @Test
    fun storeRoundTripsConversationRecords() = runBlocking {
        val record = PortableConversationRecord(
            id = "c1",
            assistantId = "a1",
            title = "Chat",
            messageNodes = listOf(MessageNode.of(UIMessage.user("Hi"))),
        )
        engine.saveConversation(record)
        val loaded = assertNotNull(engine.loadConversation("c1"))
        assertEquals("Chat", loaded.title)
        assertEquals("Hi", loaded.messageNodes.currentVersionMessages().single().toText())
    }

    @Test
    fun persistenceModeDefaultsToNormal() {
        assertEquals(PortablePersistenceMode.NORMAL, engine.persistenceMode("missing"))
        engine.setPersistenceMode("c1", PortablePersistenceMode.TEMPORARY)
        assertEquals(PortablePersistenceMode.TEMPORARY, engine.persistenceMode("c1"))
        engine.setPersistenceMode("c1", PortablePersistenceMode.NORMAL)
        assertEquals(PortablePersistenceMode.NORMAL, engine.persistenceMode("c1"))
    }

    @Test
    fun applyToolApprovalStateUpdatesCall() {
        val call = UIMessagePart.ToolCall(
            toolCallId = "t1",
            toolName = "ask_user",
            arguments = "{}",
            approvalState = ToolApprovalState.Pending,
        )
        val nodes = listOf(
            MessageNode.of(UIMessage(role = MessageRole.ASSISTANT, parts = listOf(call))),
        )
        val updated = engine.applyToolApprovalState(nodes, "t1", ToolApprovalState.Approved)
        assertEquals(ToolApprovalState.Approved, updated.single().currentMessage.getToolCalls().single().approvalState)
    }

    @Test
    fun attachJobTracksGenerationUntilCompletion() {
        val job = Job()
        engine.attachJob("c1", job)
        assertTrue(engine.isGenerating("c1"))
        engine.stop("c1")
        assertTrue(job.isCancelled)
        assertFalse(engine.isGenerating("c1"))
    }
}
