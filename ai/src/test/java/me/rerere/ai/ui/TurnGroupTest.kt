package me.rerere.ai.ui

import kotlinx.serialization.json.JsonPrimitive
import me.rerere.ai.core.MessageRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TurnGroupTest {

    @Test
    fun multiStepToolLoop_groupedIntoSingleAtomicTurnGroup() {
        val messages = listOf(
            UIMessage.user("Run command and check files"),
            UIMessage(
                role = MessageRole.ASSISTANT,
                parts = listOf(UIMessagePart.ToolCall("call-1", "run_command", """{"command":"ls"}"""))
            ),
            UIMessage(
                role = MessageRole.TOOL,
                parts = listOf(UIMessagePart.ToolResult("call-1", "run_command", JsonPrimitive("file.txt"), JsonPrimitive("{}")))
            ),
            UIMessage(
                role = MessageRole.ASSISTANT,
                parts = listOf(UIMessagePart.ToolCall("call-2", "read_file", """{"path":"file.txt"}"""))
            ),
            UIMessage(
                role = MessageRole.TOOL,
                parts = listOf(UIMessagePart.ToolResult("call-2", "read_file", JsonPrimitive("hello"), JsonPrimitive("{}")))
            ),
            UIMessage.assistant("Finished examining file.")
        )

        val groups = messages.toTurnGroups()
        assertEquals(1, groups.size)

        val turn = groups.first()
        assertEquals(0, turn.startIndex)
        assertEquals(5, turn.endIndex)
        assertEquals(6, turn.messages.size)
        assertTrue(turn.isUserTurn)
        assertEquals(2, turn.toolCalls.size)
        assertEquals(2, turn.toolResults.size)
        assertFalse(turn.hasPendingToolCall)
    }

    @Test
    fun distinctConversations_separatedIntoDistinctTurnGroups() {
        val messages = listOf(
            UIMessage.user("Turn 1 prompt"),
            UIMessage.assistant("Turn 1 reply"),
            UIMessage.user("Turn 2 prompt"),
            UIMessage(
                role = MessageRole.ASSISTANT,
                parts = listOf(UIMessagePart.ToolCall("call-1", "search", """{"query":"test"}"""))
            ),
            UIMessage(
                role = MessageRole.TOOL,
                parts = listOf(UIMessagePart.ToolResult("call-1", "search", JsonPrimitive("res"), JsonPrimitive("{}")))
            ),
            UIMessage.assistant("Turn 2 final reply"),
        )

        val groups = messages.toTurnGroups()
        assertEquals(2, groups.size)

        val g1 = groups[0]
        assertEquals(0, g1.startIndex)
        assertEquals(1, g1.endIndex)

        val g2 = groups[1]
        assertEquals(2, g2.startIndex)
        assertEquals(5, g2.endIndex)
    }

    @Test
    fun snapToTurnGroupBoundary_neverSplitsToolExchange() {
        val messages = listOf(
            UIMessage.user("Turn 1"),
            UIMessage.assistant("Reply 1"),
            UIMessage.user("Turn 2"), // index 2
            UIMessage(
                role = MessageRole.ASSISTANT,
                parts = listOf(UIMessagePart.ToolCall("call-1", "tool", "{}")) // index 3
            ),
            UIMessage(
                role = MessageRole.TOOL,
                parts = listOf(UIMessagePart.ToolResult("call-1", "tool", JsonPrimitive("ok"), JsonPrimitive("{}"))) // index 4
            ),
            UIMessage.assistant("Reply 2"), // index 5
        )

        // Slicing at index 3 (assistant tool call) or 4 (tool result) must snap to start of turn 2 (index 2)
        assertEquals(2, messages.snapToTurnGroupBoundary(3, preferEarlier = true))
        assertEquals(2, messages.snapToTurnGroupBoundary(4, preferEarlier = true))
        assertEquals(2, messages.snapToTurnGroupBoundary(5, preferEarlier = true))

        // When not preferring earlier, it snaps past the whole turn (index 6)
        assertEquals(6, messages.snapToTurnGroupBoundary(3, preferEarlier = false))
        assertEquals(6, messages.snapToTurnGroupBoundary(4, preferEarlier = false))
    }

    @Test
    fun limitTurnGroups_retainsCleanWholeTurns() {
        val messages = listOf(
            UIMessage.user("T1"),
            UIMessage.assistant("R1"),
            UIMessage.user("T2"),
            UIMessage.assistant("R2"),
            UIMessage.user("T3"),
            UIMessage.assistant("R3"),
        )

        val kept = messages.limitTurnGroups(2)
        assertEquals(4, kept.size)
        assertEquals("T2", (kept[0].parts.first() as UIMessagePart.Text).text)
    }
}
