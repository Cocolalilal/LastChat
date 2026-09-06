package me.rerere.ai.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class MessageNodeVersionSelectionTest {
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

    @Test
    fun mergeCurrentVersionMessagesReplacesSnapshotsInExistingNodes() {
        val node = MessageNode(
            messages = listOf(UIMessage.assistant("Draft")),
        )
        val updated = UIMessage.assistant("Streaming text").copy(id = node.messages[0].id)

        val merged = listOf(node).mergeCurrentVersionMessages(listOf(updated))

        assertEquals(1, merged.size)
        assertEquals("Streaming text", merged[0].currentMessage.toText())
        assertEquals(0, merged[0].selectIndex)
    }

    @Test
    fun mergeCurrentVersionMessagesAppendsNewMessagesAsNodes() {
        val user = UIMessage.user("Hi")
        val nodes = listOf(MessageNode.of(user))
        val assistant = UIMessage.assistant("Hello")
        val tool = UIMessage(role = me.rerere.ai.core.MessageRole.TOOL, parts = emptyList())

        val merged = nodes.mergeCurrentVersionMessages(
            listOf(user, assistant, tool, UIMessage.assistant("")),
        )

        assertEquals(4, merged.size)
        assertEquals(me.rerere.ai.core.MessageRole.ASSISTANT, merged[1].role)
        assertEquals(me.rerere.ai.core.MessageRole.TOOL, merged[2].role)
    }

    @Test
    fun currentVersionMessagesResolvesSelectedVersionPerTurn() {
        val tag = "regen"
        val nodes = listOf(
            MessageNode.of(UIMessage.user("Hi")),
            MessageNode(
                messages = listOf(
                    UIMessage.assistant("Original reply"),
                    UIMessage.assistant("Regenerated reply").copy(versionTag = tag),
                ),
                selectIndex = 1,
            ),
        )

        val visible = nodes.currentVersionMessages()

        assertEquals(listOf("Hi", "Regenerated reply"), visible.map { it.toText() })
    }
}
