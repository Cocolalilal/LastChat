package me.rerere.rikkahub.ui.components.chat

import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.MessageNode
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

    @Test
    fun groupIntoTurnsKeepsToolResultsWithInspectedImagesInsideAssistantTurn() {
        val groups = listOf(
            MessageNode.of(UIMessage.user("Show me a chart")),
            MessageNode.of(UIMessage.assistant("Generating chart...")),
            MessageNode.of(
                UIMessage(
                    role = MessageRole.TOOL,
                    parts = listOf(
                        me.rerere.ai.ui.UIMessagePart.ToolResult(
                            toolCallId = "c1",
                            toolName = "workspace_view_image",
                            content = kotlinx.serialization.json.buildJsonObject { },
                            arguments = kotlinx.serialization.json.buildJsonObject { },
                            inspectedImages = listOf(
                                me.rerere.ai.ui.ToolResultImage(
                                    url = "",
                                    title = "chart.png",
                                )
                            )
                        )
                    ),
                )
            ),
            MessageNode.of(UIMessage.assistant("Here is the completed chart")),
            MessageNode.of(UIMessage.user("Thanks, now do another")),
        ).groupIntoTurns()

        assertEquals(3, groups.size)
        assertEquals(MessageRole.USER, groups[0].role)
        assertEquals(MessageRole.ASSISTANT, groups[1].role)
        assertEquals(3, groups[1].nodes.size)
        assertEquals(MessageRole.USER, groups[2].role)
    }

    @Test
    fun actionTargetNodeAndSpeechTextUseSelectedShorterRegenerationInsteadOfOlderTrailingNode() {
        val v1ToolCall = me.rerere.ai.ui.UIMessage(
            role = MessageRole.ASSISTANT,
            parts = listOf(
                me.rerere.ai.ui.UIMessagePart.ToolCall(
                    toolCallId = "call-1",
                    toolName = "search_web",
                    arguments = "{}",
                )
            ),
            versionTag = "v1",
        )
        val v1ToolResult = me.rerere.ai.ui.UIMessage(
            role = MessageRole.TOOL,
            parts = listOf(
                me.rerere.ai.ui.UIMessagePart.ToolResult(
                    toolCallId = "call-1",
                    toolName = "search_web",
                    content = kotlinx.serialization.json.buildJsonObject { },
                    arguments = kotlinx.serialization.json.buildJsonObject { },
                )
            ),
            versionTag = "v1",
        )
        val v1FinalReply = UIMessage.assistant("Older reply from v1").copy(versionTag = "v1")
        val v2DirectReply = UIMessage.assistant("Selected regeneration v2").copy(versionTag = "v2")

        val v2SelectedGroup = listOf(
            MessageNode(messages = listOf(v1ToolCall, v2DirectReply), selectIndex = 1),
            MessageNode.of(v1ToolResult),
            MessageNode.of(v1FinalReply),
        ).groupIntoTurns().single()

        assertEquals(v2DirectReply.id, v2SelectedGroup.actionTargetNode.currentMessage.id)
        assertEquals("Selected regeneration v2", v2SelectedGroup.visibleAssistantSpeechText())

        val v1SelectedGroup = listOf(
            MessageNode(messages = listOf(v1ToolCall, v2DirectReply), selectIndex = 0),
            MessageNode.of(v1ToolResult),
            MessageNode.of(v1FinalReply),
        ).groupIntoTurns().single()

        assertEquals(v1FinalReply.id, v1SelectedGroup.actionTargetNode.currentMessage.id)
        assertEquals("Older reply from v1", v1SelectedGroup.visibleAssistantSpeechText())
    }

    @Test
    fun actionTargetNodeAndSpeechTextIgnoreLaterRegenerationTrailingNodesWhenSwitchingBack() {
        val v1Reply = UIMessage.assistant("Original reply v1").copy(versionTag = "v1")
        val v2LeadIn = UIMessage.assistant("Checking tools...").copy(versionTag = "v2")
        val v2FinalReply = UIMessage.assistant("Longer regenerated reply v2").copy(versionTag = "v2")

        val switchedBackToV1 = listOf(
            MessageNode(messages = listOf(v1Reply, v2LeadIn), selectIndex = 0),
            MessageNode.of(v2FinalReply),
        ).groupIntoTurns().single()

        assertEquals(v1Reply.id, switchedBackToV1.actionTargetNode.currentMessage.id)
        assertEquals("Original reply v1", switchedBackToV1.visibleAssistantSpeechText())

        val selectedV2 = listOf(
            MessageNode(messages = listOf(v1Reply, v2LeadIn), selectIndex = 1),
            MessageNode.of(v2FinalReply),
        ).groupIntoTurns().single()

        assertEquals(v2FinalReply.id, selectedV2.actionTargetNode.currentMessage.id)
        assertEquals("Checking tools...\n\nLonger regenerated reply v2", selectedV2.visibleAssistantSpeechText())
    }
}
