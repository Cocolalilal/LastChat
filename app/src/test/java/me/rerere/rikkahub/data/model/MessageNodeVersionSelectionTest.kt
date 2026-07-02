package me.rerere.rikkahub.data.model

import me.rerere.ai.ui.UIMessage
import org.junit.Assert.assertEquals
import org.junit.Test

class MessageNodeVersionSelectionTest {
    @Test
    fun getMessageNodeByMessageFindsUpdatedMessageWithSameId() {
        val originalMessage = UIMessage.assistant("Fresh reply")
        val storedMessage = originalMessage.copy(generationDurationMs = 1200L)
        val node = MessageNode.of(storedMessage)
        val conversation = Conversation.ofId(
            id = kotlin.uuid.Uuid.random(),
            messages = listOf(node),
        )

        assertEquals(node, conversation.getMessageNodeByMessage(originalMessage))
    }

    @Test
    fun versionSelectionIndicesCollapseSnapshotsWithSameVersionTag() {
        val node = MessageNode(
            messages = listOf(
                UIMessage.assistant("Original reply"),
                UIMessage.assistant("").copy(versionTag = "regen"),
                UIMessage.assistant("Partial regen").copy(versionTag = "regen"),
                UIMessage.assistant("Final regen").copy(versionTag = "regen"),
            ),
            selectIndex = 3,
        )

        assertEquals(listOf(0, 3), node.versionSelectionIndices())
        assertEquals(1, node.versionSelectionPosition())
    }

    @Test
    fun versionSelectionPositionTreatsPlaceholderAndFinalSnapshotAsSameVersion() {
        val node = MessageNode(
            messages = listOf(
                UIMessage.assistant("Original reply"),
                UIMessage.assistant("").copy(versionTag = "regen"),
                UIMessage.assistant("Final regen").copy(versionTag = "regen"),
            ),
            selectIndex = 1,
        )

        assertEquals(listOf(0, 2), node.versionSelectionIndices())
        assertEquals(1, node.versionSelectionPosition())
    }
}
