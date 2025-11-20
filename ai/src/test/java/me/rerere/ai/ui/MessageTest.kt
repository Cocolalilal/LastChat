package me.rerere.ai.ui

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import me.rerere.ai.core.MessageRole
import org.junit.Assert.assertEquals
import org.junit.Test

class MessageTest {

    @Test
    fun `limitContext with size 0 should return original list`() {
        val messages = createTestMessages(5)
        val result = messages.limitContext(0)
        assertEquals(messages, result)
    }

    @Test
    fun `limitContext with negative size should return original list`() {
        val messages = createTestMessages(5)
        val result = messages.limitContext(-1)
        assertEquals(messages, result)
    }

    @Test
    fun `limitContext with size greater than list size should return original list`() {
        val messages = createTestMessages(3)
        val result = messages.limitContext(5)
        assertEquals(messages, result)
    }

    @Test
    fun `limitContext with normal size should return last N messages`() {
        val messages = createTestMessages(5)
        val result = messages.limitContext(3)
        assertEquals(3, result.size)
        assertEquals(messages.subList(2, 5), result)
    }

    @Test
    fun `limitContext with tool result at start should include corresponding tool call`() {
        val messages = listOf(
            UIMessage(role = MessageRole.USER, parts = listOf(UIMessagePart.Text("User message"))),
            UIMessage(
                role = MessageRole.ASSISTANT, parts = listOf(
                    UIMessagePart.ToolCall("call1", "test_tool", "{}")
                )
            ),
            UIMessage(
                role = MessageRole.USER, parts = listOf(
                    UIMessagePart.ToolResult("call1", "test_tool", JsonPrimitive("result"), JsonPrimitive("{}"))
                )
            ),
            UIMessage(role = MessageRole.ASSISTANT, parts = listOf(UIMessagePart.Text("Final response")))
        )

        val result = messages.limitContext(2)
        assertEquals(4, result.size)
        assertEquals(messages, result)
    }

    @Test
    fun `limitContext with tool call at start should include corresponding user message`() {
        val messages = listOf(
            UIMessage(role = MessageRole.USER, parts = listOf(UIMessagePart.Text("User query"))),
            UIMessage(
                role = MessageRole.ASSISTANT, parts = listOf(
                    UIMessagePart.ToolCall("call1", "test_tool", "{}")
                )
            ),
            UIMessage(
                role = MessageRole.USER, parts = listOf(
                    UIMessagePart.ToolResult("call1", "test_tool", JsonPrimitive("result"), JsonPrimitive("{}"))
                )
            ),
            UIMessage(role = MessageRole.ASSISTANT, parts = listOf(UIMessagePart.Text("Final response")))
        )

        val result = messages.limitContext(2)
        assertEquals(4, result.size)
        assertEquals(messages, result)
    }

    @Test
    fun `limitContext with tool result that chains to tool call and user message`() {
        val messages = listOf(
            UIMessage(role = MessageRole.USER, parts = listOf(UIMessagePart.Text("Initial query"))),
            UIMessage(
                role = MessageRole.ASSISTANT, parts = listOf(
                    UIMessagePart.ToolCall("call1", "test_tool", "{}")
                )
            ),
            UIMessage(
                role = MessageRole.USER, parts = listOf(
                    UIMessagePart.ToolResult("call1", "test_tool", JsonPrimitive("result"), JsonPrimitive("{}"))
                )
            ),
            UIMessage(role = MessageRole.ASSISTANT, parts = listOf(UIMessagePart.Text("Response 1"))),
            UIMessage(role = MessageRole.ASSISTANT, parts = listOf(UIMessagePart.Text("Response 2")))
        )

        // Request only 1 message but tool result should chain back to include user message
        val result = messages.limitContext(1)
        assertEquals(1, result.size)
        assertEquals(messages.subList(4, 5), result)
    }

    @Test
    fun `limitContext with multiple tool calls should find earliest user message`() {
        val messages = listOf(
            UIMessage(role = MessageRole.USER, parts = listOf(UIMessagePart.Text("User query"))),
            UIMessage(
                role = MessageRole.ASSISTANT, parts = listOf(
                    UIMessagePart.ToolCall("call1", "tool1", "{}")
                )
            ),
            UIMessage(
                role = MessageRole.ASSISTANT, parts = listOf(
                    UIMessagePart.ToolCall("call2", "tool2", "{}")
                )
            ),
            UIMessage(role = MessageRole.ASSISTANT, parts = listOf(UIMessagePart.Text("Final response")))
        )

        val result = messages.limitContext(2)
        assertEquals(4, result.size)
        assertEquals(messages, result)
    }

    @Test
    fun `limitContext with tool result but no corresponding tool call should not adjust`() {
        val messages = listOf(
            UIMessage(role = MessageRole.USER, parts = listOf(UIMessagePart.Text("User 1"))),
            UIMessage(role = MessageRole.ASSISTANT, parts = listOf(UIMessagePart.Text("Assistant 1"))),
            UIMessage(
                role = MessageRole.USER, parts = listOf(
                    UIMessagePart.ToolResult("orphan", "test_tool", JsonPrimitive("result"), JsonPrimitive("{}"))
                )
            ),
            UIMessage(role = MessageRole.ASSISTANT, parts = listOf(UIMessagePart.Text("Assistant 2")))
        )

        val result = messages.limitContext(2)
        assertEquals(2, result.size)
        assertEquals(messages.subList(2, 4), result)
    }

    @Test
    fun `limitContext with tool call but no corresponding user message should not adjust further`() {
        val messages = listOf(
            UIMessage(role = MessageRole.ASSISTANT, parts = listOf(UIMessagePart.Text("Assistant 1"))),
            UIMessage(
                role = MessageRole.ASSISTANT, parts = listOf(
                    UIMessagePart.ToolCall("call1", "test_tool", "{}")
                )
            ),
            UIMessage(role = MessageRole.ASSISTANT, parts = listOf(UIMessagePart.Text("Assistant 2")))
        )

        val result = messages.limitContext(2)
        assertEquals(2, result.size)
        assertEquals(messages.subList(1, 3), result)
    }

    @Test
    fun `limitContext with empty list should return empty list`() {
        val messages = emptyList<UIMessage>()
        val result = messages.limitContext(5)
        assertEquals(emptyList<UIMessage>(), result)
    }

    @Test
    fun `limitContext with single message should return that message`() {
        val messages = listOf(
            UIMessage(role = MessageRole.USER, parts = listOf(UIMessagePart.Text("Single message")))
        )
        val result = messages.limitContext(1)
        assertEquals(1, result.size)
        assertEquals(messages, result)
    }

    @Test
    fun `limitContext with complex chain of tool calls and results`() {
        val messages = listOf(
            UIMessage(role = MessageRole.USER, parts = listOf(UIMessagePart.Text("Initial query"))),
            UIMessage(
                role = MessageRole.ASSISTANT, parts = listOf(
                    UIMessagePart.ToolCall("call1", "tool1", "{}")
                )
            ),
            UIMessage(
                role = MessageRole.USER, parts = listOf(
                    UIMessagePart.ToolResult("call1", "tool1", JsonPrimitive("result1"), JsonPrimitive("{}"))
                )
            ),
            UIMessage(
                role = MessageRole.ASSISTANT, parts = listOf(
                    UIMessagePart.ToolCall("call2", "tool2", "{}")
                )
            ),
            UIMessage(
                role = MessageRole.USER, parts = listOf(
                    UIMessagePart.ToolResult("call2", "tool2", JsonPrimitive("result2"), JsonPrimitive("{}"))
                )
            ),
            UIMessage(role = MessageRole.ASSISTANT, parts = listOf(UIMessagePart.Text("Final response")))
        )

        // Request 3 messages starting from tool result, should include the whole chain
        val result = messages.limitContext(3)
        assertEquals(6, result.size)
        assertEquals(messages, result)
    }

    private fun createTestMessages(count: Int): List<UIMessage> {
        return (0 until count).map { i ->
            UIMessage(
                role = if (i % 2 == 0) MessageRole.USER else MessageRole.ASSISTANT,
                parts = listOf(UIMessagePart.Text("Message $i"))
            )
        }
    }
}
