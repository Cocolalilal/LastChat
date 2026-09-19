package me.rerere.lastchat.ios

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import me.rerere.ai.ui.MessageNode
import me.rerere.ai.ui.UIMessage
import me.rerere.lastchat.ios.backup.IosBackupDataImporter

class IosConversationBranchingTest {
    @Test
    fun migratedToNodesWrapsLegacyMessages() {
        val conversation = IosConversation(
            messages = listOf(UIMessage.user("Hi"), UIMessage.assistant("Hello")),
        )
        val migrated = conversation.migratedToNodes()

        assertEquals(2, migrated.messageNodes.size)
        assertTrue(migrated.messages.isEmpty())
        assertEquals(listOf("Hi", "Hello"), migrated.currentMessages.map { it.toText() })
    }

    @Test
    fun mergedAppendsAndReplacesNodesById() {
        val user = UIMessage.user("Hi")
        val conversation = IosConversation(messageNodes = listOf(MessageNode.of(user)))
        val streaming = UIMessage.assistant("Hel")
        val withDraft = conversation.merged(listOf(user, streaming))

        assertEquals(2, withDraft.messageNodes.size)

        val streamed = withDraft.merged(
            listOf(user, UIMessage.assistant("Hello").copy(id = streaming.id)),
        )

        assertEquals(2, streamed.messageNodes.size)
        assertEquals("Hello", streamed.currentMessages[1].toText())
    }

    @Test
    fun withoutTrailingBlankAssistantRemovesFreshPlaceholder() {
        val user = UIMessage.user("Hi")
        val conversation = IosConversation(
            messageNodes = listOf(MessageNode.of(user), MessageNode.of(UIMessage.assistant(""))),
        )

        val cleaned = conversation.withoutTrailingBlankAssistant()

        assertEquals(1, cleaned.messageNodes.size)
    }

    @Test
    fun withoutTrailingBlankAssistantRestoresPreviousVersionOnRegenerateCancel() {
        val user = UIMessage.user("Hi")
        val original = UIMessage.assistant("Original reply")
        val blankRegen = UIMessage.assistant("").copy(versionTag = "regen")
        val node = MessageNode(messages = listOf(original, blankRegen), selectIndex = 1)
        val conversation = IosConversation(
            messageNodes = listOf(MessageNode.of(user), node),
        )

        val cleaned = conversation.withoutTrailingBlankAssistant()

        assertEquals(2, cleaned.messageNodes.size)
        assertEquals("Original reply", cleaned.messageNodes[1].currentMessage.toText())
    }

    @Test
    fun currentMessagesHidesInactiveVersionsAfterRegeneration() {
        val user = UIMessage.user("Hi")
        val v1 = UIMessage.assistant("Original reply")
        val v2 = UIMessage.assistant("Regenerated").copy(versionTag = "regen")
        val conversation = IosConversation(
            messageNodes = listOf(
                MessageNode.of(user),
                MessageNode(messages = listOf(v1, v2), selectIndex = 1),
            ),
        )

        assertEquals(listOf("Hi", "Regenerated"), conversation.currentMessages.map { it.toText() })
    }

    @Test
    fun withEditedMessageBranchesNodeAndKeepsVersionTag() {
        val user = UIMessage.user("Original question")
        val assistant = UIMessage.assistant("Answer")
        val conversation = IosConversation(
            messageNodes = listOf(MessageNode.of(user), MessageNode.of(assistant)),
        )

        val edited = conversation.withEditedMessage(
            user.id.toString(),
            listOf(me.rerere.ai.ui.UIMessagePart.Text("Edited question")),
        )

        val userNode = edited.messageNodes[0]
        assertEquals(2, userNode.messages.size)
        assertEquals(1, userNode.selectIndex)
        assertEquals("Edited question", userNode.currentMessage.toText())
        assertEquals(1, edited.messageNodes[1].messages.size)
        assertEquals(listOf("Edited question", "Answer"), edited.currentMessages.map { it.toText() })
    }

    @Test
    fun withDeletedMessageTruncatesFromUserNode() {
        val user1 = UIMessage.user("First")
        val user2 = UIMessage.user("Second")
        val conversation = IosConversation(
            messageNodes = listOf(
                MessageNode.of(user1),
                MessageNode.of(UIMessage.assistant("A")),
                MessageNode.of(user2),
                MessageNode.of(UIMessage.assistant("B")),
            ),
        )

        val deleted = conversation.withDeletedMessage(user1.id.toString())

        assertTrue(deleted.messageNodes.isEmpty())
    }

    @Test
    fun withDeletedMessageRemovesRelatedToolChainForAssistantMessage() {
        val user = UIMessage.user("Hi")
        val assistantWithCall = UIMessage.assistant("").copy(
            parts = listOf(
                me.rerere.ai.ui.UIMessagePart.ToolCall(
                    toolCallId = "call-1", toolName = "search_web", arguments = "{}",
                ),
            ),
        )
        val toolResult = UIMessage(
            role = me.rerere.ai.core.MessageRole.TOOL,
            parts = listOf(
                me.rerere.ai.ui.UIMessagePart.ToolResult(
                    toolCallId = "call-1",
                    toolName = "search_web",
                    content = kotlinx.serialization.json.JsonPrimitive("{}"),
                    arguments = kotlinx.serialization.json.JsonPrimitive("{}"),
                ),
            ),
        )
        val assistantFinal = UIMessage.assistant("Done")
        val conversation = IosConversation(
            messageNodes = listOf(
                MessageNode.of(user),
                MessageNode.of(assistantWithCall),
                MessageNode.of(toolResult),
                MessageNode.of(assistantFinal),
            ),
        )

        val deleted = conversation.withDeletedMessage(assistantFinal.id.toString())

        assertEquals(1, deleted.messageNodes.size)
        assertEquals("Hi", deleted.currentMessages.single().toText())
    }

    @Test
    fun buildIosForkConversationCopiesNodesUpToMessage() {
        val user = UIMessage.user("Hi")
        val assistant = UIMessage.assistant("Answer")
        val after = UIMessage.user("More")
        val conversation = IosConversation(
            title = "Chat",
            messageNodes = listOf(
                MessageNode.of(user),
                MessageNode.of(assistant),
                MessageNode.of(after),
            ),
        )

        val fork = assertNotNull(buildIosForkConversation(conversation, assistant.id.toString()))

        assertTrue(fork.id != conversation.id)
        assertEquals("Chat", fork.title)
        assertEquals(conversation.assistantId, fork.assistantId)
        assertEquals(listOf("Hi", "Answer"), fork.currentMessages.map { it.toText() })
    }

    @Test
    fun mapConversationRowDecodesAndroidNodesJson() {
        val warnings = mutableListOf<String>()
        val conversation = IosBackupDataImporter.mapConversationRow(
            row = mapOf(
                "id" to "99999999-9999-9999-9999-999999999999",
                "assistant_id" to "88888888-8888-8888-8888-888888888888",
                "title" to "Backed up chat",
                "nodes" to """
                    [
                      {"messages":[{"role":"USER","parts":[{"type":"text","text":"Hi"}],"id":"11111111-2222-3333-4444-555555555555","createdAt":0}]},
                      {"messages":[{"role":"ASSISTANT","parts":[{"type":"text","text":"Hello"}],"id":"22222222-3333-4444-5555-666666666666","createdAt":0}]}
                    ]
                """.trimIndent(),
                "update_at" to "1700000100000",
                "is_pinned" to "1",
            ),
            warnings = warnings,
        )

        assertNotNull(conversation)
        assertEquals("Backed up chat", conversation.title)
        assertTrue(conversation.isPinned)
        assertEquals(2, conversation.messageNodes.size)
        assertEquals(
            listOf("Hi", "Hello"),
            conversation.currentMessages.map { it.toText() },
        )
        assertTrue(warnings.isEmpty())
    }

    @Test
    fun mapConversationRowReportsUnparsableNodes() {
        val warnings = mutableListOf<String>()
        val conversation = IosBackupDataImporter.mapConversationRow(
            row = mapOf(
                "id" to "99999999-9999-9999-9999-999999999999",
                "nodes" to "not-json",
            ),
            warnings = warnings,
        )

        assertNull(conversation)
        assertEquals(1, warnings.size)
    }

    @Test
    fun mapMemoryRowImportsContentWithoutEmbeddings() {
        val memory = IosBackupDataImporter.mapMemoryRow(
            mapOf(
                "id" to "42",
                "assistant_id" to "88888888-8888-8888-8888-888888888888",
                "content" to "User likes tea",
                "type" to "0",
                "created_at" to "1700000000000",
            ),
        )

        assertNotNull(memory)
        assertEquals(42, memory.id)
        assertEquals("User likes tea", memory.content)
        assertEquals(0, memory.type)
        assertNull(memory.embeddings)
        assertNull(memory.embeddingModelId)
    }

    @Test
    fun androidAttachmentRemapResolvesStagedManagedFiles() {
        val staged = mutableMapOf<String, String>()
        val remap = { url: String ->
            IosBackupDataImporter.androidAttachmentRemap(url) { storagePath ->
                staged[storagePath]
            }
        }
        staged["upload/photo.jpg"] = "file:///container/upload/photo.jpg"
        staged["images/pic name.png"] = "file:///container/images/pic name.png"

        assertEquals(
            "file:///container/upload/photo.jpg",
            remap("file:///data/user/0/me.rerere.rikkahub/files/upload/photo.jpg"),
        )
        assertEquals(
            "file:///container/images/pic name.png",
            remap("file:///data/user/0/me.rerere.rikkahub/files/images/pic%20name.png"),
        )
        assertNull(remap("https://example.com/image.png"))
        assertNull(remap("file:///data/user/0/me.rerere.rikkahub/files/workspaces/root/file.txt"))
        assertNull(remap("file:///data/user/0/me.rerere.rikkahub/files/upload/missing.jpg"))
    }
}
