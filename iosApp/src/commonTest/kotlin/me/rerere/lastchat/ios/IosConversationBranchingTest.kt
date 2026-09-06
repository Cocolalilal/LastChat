package me.rerere.lastchat.ios

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import me.rerere.ai.ui.MessageNode
import me.rerere.ai.ui.UIMessage

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
}
