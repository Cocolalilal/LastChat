package me.rerere.rikkahub.ui.components.chat

import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.data.model.MessageNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageTurnGroupingTest {
    @Test
    fun groupIntoTurnsStartsNewAssistantTurnWhenForcedBreakIsSet() {
        val groups = listOf(
            MessageNode.of(UIMessage.assistant("First reply")),
            MessageNode.of(
                message = UIMessage.assistant("Spontaneous follow-up"),
                forceTurnBreakBefore = true,
            ),
        ).groupIntoTurns()

        assertEquals(2, groups.size)
        assertEquals(MessageRole.ASSISTANT, groups[0].role)
        assertEquals(MessageRole.ASSISTANT, groups[1].role)
        assertEquals("First reply", groups[0].filteredNodes.single().currentMessage.toText())
        assertEquals("Spontaneous follow-up", groups[1].filteredNodes.single().currentMessage.toText())
    }

    @Test
    fun groupIntoTurnsKeepsToolMessagesInsideAssistantTurnWithoutForcedBreak() {
        val groups = listOf(
            MessageNode.of(UIMessage.assistant("Working on it")),
            MessageNode.of(
                UIMessage(
                    role = MessageRole.TOOL,
                    parts = emptyList(),
                )
            ),
        ).groupIntoTurns()

        assertEquals(1, groups.size)
        assertEquals(MessageRole.ASSISTANT, groups.single().role)
        assertTrue(groups.single().nodes.size == 2)
    }

    @Test
    fun nodeWithMostVersionsUsesDistinctVersionsInsteadOfRawSnapshotCount() {
        val canonicalVersionNode = MessageNode(
            messages = listOf(
                UIMessage.assistant("Original reply"),
                UIMessage.assistant("Regenerated reply").copy(versionTag = "regen"),
            ),
            selectIndex = 1,
        )
        val streamingSnapshotNode = MessageNode(
            messages = listOf(
                UIMessage.assistant("").copy(versionTag = "regen"),
                UIMessage.assistant("Partial regen").copy(versionTag = "regen"),
                UIMessage.assistant("Final regen").copy(versionTag = "regen"),
            ),
            selectIndex = 2,
        )

        val group = listOf(canonicalVersionNode, streamingSnapshotNode)
            .groupIntoTurns()
            .single()

        assertEquals(canonicalVersionNode.id, group.nodeWithMostVersions.id)
    }
}
