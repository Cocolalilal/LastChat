package me.rerere.ai.ui

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.rerere.ai.core.MessageRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    @Test
    fun `toContentText excludes reasoning parts`() {
        val message = UIMessage(
            role = MessageRole.ASSISTANT,
            parts = listOf(
                UIMessagePart.Reasoning("Internal chain of thought"),
                UIMessagePart.Text("Final response"),
            )
        )

        assertEquals("Final response", message.toContentText())
    }

    @Test
    fun `toContentText strips think tags from text parts`() {
        val message = UIMessage(
            role = MessageRole.ASSISTANT,
            parts = listOf(
                UIMessagePart.Text("<think>Internal chain of thought</think>\nFinal response"),
            )
        )

        assertEquals("Final response", message.toContentText())
    }

    @Test
    fun `reasoning summary title is derived across streamed chunks`() {
        val metadata = buildJsonObject {
            put("reasoning_kind", "summary")
        }
        val message = UIMessage(
            role = MessageRole.ASSISTANT,
            parts = listOf(
                UIMessagePart.Reasoning(
                    reasoning = "",
                    finishedAt = null,
                    metadata = metadata
                )
            )
        )

        val withPartialTitle = message + reasoningChunk("**Checking", metadata)
        val partialReasoning = withPartialTitle.parts.filterIsInstance<UIMessagePart.Reasoning>().single()
        assertEquals(null, partialReasoning.title)

        val withCompleteTitle = withPartialTitle + reasoningChunk(" details**\n\nLooking at the request.", metadata)
        val reasoning = withCompleteTitle.parts.filterIsInstance<UIMessagePart.Reasoning>().single()
        assertEquals("Checking details", reasoning.title)
        assertEquals("**Checking details**\n\nLooking at the request.", reasoning.reasoning)
    }

    @Test
    fun `extractReasoningSummaryTitle only extracts from valid markdown headers and bold`() {
        // Bold title
        assertEquals("Researching", "**Researching**\nSome context".extractReasoningSummaryTitle())
        // Underline title
        assertEquals("Planning", "__Planning__\nStep 1".extractReasoningSummaryTitle())
        // Heading titles
        assertEquals("Analyzing Constraints", "### Analyzing Constraints\nSome info".extractReasoningSummaryTitle())
        assertEquals("Final Step", "# Final Step\nWait".extractReasoningSummaryTitle())

        // Bullet lists/numbered lists containing bold/underlines
        assertEquals("List Bold", "- **List Bold**\nDetails".extractReasoningSummaryTitle())
        assertEquals("Num Bold", "1. **Num Bold**\nDetails".extractReasoningSummaryTitle())
        assertEquals("Star Underline", "* __Star Underline__\nDetails".extractReasoningSummaryTitle())

        // Plain text should be rejected
        assertEquals(null, "Thinking about user request".extractReasoningSummaryTitle())
        assertEquals(null, "Thinking about user request:".extractReasoningSummaryTitle())
        assertEquals(null, "Thinking about user request.\nAnother line".extractReasoningSummaryTitle())
    }

    @Test
    fun `extractLatestReasoningSummaryTitle extracts the last valid header`() {
        val text = """
            **Step 1: Check inputs**
            Some logic here
            
            ### Step 2: Formulate plan
            Formulating logic
            
            Plain text sentence at the end.
        """.trimIndent()
        assertEquals("Step 2: Formulate plan", text.extractLatestReasoningSummaryTitle())
    }

    private fun createTestMessages(count: Int): List<UIMessage> {
        return (0 until count).map { i ->
            UIMessage(
                role = if (i % 2 == 0) MessageRole.USER else MessageRole.ASSISTANT,
                parts = listOf(UIMessagePart.Text("Message $i"))
            )
        }
    }

    private fun reasoningChunk(text: String, metadata: kotlinx.serialization.json.JsonObject): MessageChunk {
        return MessageChunk(
            id = "chunk",
            model = "test",
            choices = listOf(
                UIMessageChoice(
                    index = 0,
                    delta = UIMessage(
                        role = MessageRole.ASSISTANT,
                        parts = listOf(
                            UIMessagePart.Reasoning(
                                reasoning = text,
                                finishedAt = null,
                                metadata = metadata
                            )
                        )
                    ),
                    message = null,
                    finishReason = null
                )
            )
        )
    }

    @Test
    fun `appendChunk merges blank ID tool call chunks`() {
        val initialMessage = UIMessage(
            role = MessageRole.ASSISTANT,
            parts = listOf(
                UIMessagePart.ToolCall(
                    toolCallId = "",
                    toolName = "eval_python",
                    arguments = ""
                )
            )
        )

        // Simulating streaming delta chunks with blank ID and empty tool name
        val chunk1 = MessageChunk(
            id = "chunk-1",
            model = "test",
            choices = listOf(
                UIMessageChoice(
                    index = 0,
                    delta = UIMessage(
                        role = MessageRole.ASSISTANT,
                        parts = listOf(
                            UIMessagePart.ToolCall(
                                toolCallId = "",
                                toolName = "",
                                arguments = """{"code":"print("""
                            )
                        )
                    ),
                    message = null,
                    finishReason = null
                )
            )
        )

        val chunk2 = MessageChunk(
            id = "chunk-2",
            model = "test",
            choices = listOf(
                UIMessageChoice(
                    index = 0,
                    delta = UIMessage(
                        role = MessageRole.ASSISTANT,
                        parts = listOf(
                            UIMessagePart.ToolCall(
                                toolCallId = "",
                                toolName = "",
                                arguments = """'hello')"}"""
                            )
                        )
                    ),
                    message = null,
                    finishReason = null
                )
            )
        )

        val result1 = initialMessage + chunk1
        val result2 = result1 + chunk2

        assertEquals(1, result2.parts.size)
        val toolCall = result2.parts.single() as UIMessagePart.ToolCall
        assertEquals("eval_python", toolCall.toolName)
        assertEquals("""{"code":"print('hello')"}""", toolCall.arguments)
        assertEquals("", toolCall.toolCallId)
    }

    @Test
    fun `appendChunk does not duplicate repeated complete tool names`() {
        val initialMessage = UIMessage(
            role = MessageRole.ASSISTANT,
            parts = listOf(
                UIMessagePart.ToolCall(
                    toolCallId = "",
                    toolName = "search_web",
                    arguments = ""
                )
            )
        )

        val chunk = MessageChunk(
            id = "chunk-search",
            model = "test",
            choices = listOf(
                UIMessageChoice(
                    index = 0,
                    delta = UIMessage(
                        role = MessageRole.ASSISTANT,
                        parts = listOf(
                            UIMessagePart.ToolCall(
                                toolCallId = "",
                                toolName = "search_web",
                                arguments = """{"query":"kotlin"}"""
                            )
                        )
                    ),
                    message = null,
                    finishReason = null
                )
            )
        )

        val result = initialMessage + chunk

        val toolCall = result.parts.single() as UIMessagePart.ToolCall
        assertEquals("search_web", toolCall.toolName)
        assertEquals("""{"query":"kotlin"}""", toolCall.arguments)
    }

    @Test
    fun `appendChunk keeps separate complete blank ID tool calls separate`() {
        val initialMessage = UIMessage(
            role = MessageRole.ASSISTANT,
            parts = listOf(
                UIMessagePart.ToolCall(
                    toolCallId = "",
                    toolName = "search_web",
                    arguments = """{"query":"planning agents"}"""
                )
            )
        )

        val chunk = MessageChunk(
            id = "chunk-search-2",
            model = "test",
            choices = listOf(
                UIMessageChoice(
                    index = 0,
                    delta = UIMessage(
                        role = MessageRole.ASSISTANT,
                        parts = listOf(
                            UIMessagePart.ToolCall(
                                toolCallId = "",
                                toolName = "search_web",
                                arguments = """{"query":"coding agents benchmarks"}"""
                            )
                        )
                    ),
                    message = null,
                    finishReason = null
                )
            )
        )

        val result = initialMessage + chunk

        assertEquals(2, result.parts.size)
        assertTrue(result.parts.all { it is UIMessagePart.ToolCall })
        val first = result.parts[0] as UIMessagePart.ToolCall
        val second = result.parts[1] as UIMessagePart.ToolCall
        assertEquals("search_web", first.toolName)
        assertEquals("""{"query":"planning agents"}""", first.arguments)
        assertEquals("search_web", second.toolName)
        assertEquals("""{"query":"coding agents benchmarks"}""", second.arguments)
    }
}
