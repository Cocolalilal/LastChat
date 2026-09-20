package me.rerere.ai.generation

import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.rerere.ai.core.MessageRole
import me.rerere.ai.core.Tool
import me.rerere.ai.core.ToolApprovalMode
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.TextGenerationParams
import me.rerere.ai.ui.MessageChunk
import me.rerere.ai.ui.ToolApprovalState
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessageChoice
import me.rerere.ai.ui.UIMessagePart
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class PortableGenerationLoopTest {
    private val engine = PortableChatEngine()
    private val model = Model(modelId = "test", displayName = "Test")

    @Test
    fun streamMergesAssistantChunksWithHandleMessageChunk() = runBlocking {
        val user = UIMessage.user("Hi")
        val placeholder = UIMessage.assistant("")
        val result = engine.generate(
            session(
                messages = listOf(user, placeholder),
                streamTurns = listOf(
                    listOf(textDelta("Hel"), textDelta("lo")),
                ),
            )
        )

        assertEquals(1, result.steps)
        assertFalse(result.pendingApproval)
        assertEquals("Hello", result.messages.last().toText())
        assertEquals(placeholder.id, result.messages.last().id)
    }

    @Test
    fun executesToolsAndRunsASecondProviderTurn() = runBlocking {
        var echoCalls = 0
        val echo = Tool(
            name = "echo",
            description = "echo",
            execute = { args ->
                echoCalls += 1
                buildJsonObject { put("ok", (args as JsonObject)["value"] ?: JsonPrimitive("missing")) }
            },
        )
        val user = UIMessage.user("Use echo")
        val result = engine.generate(
            session(
                messages = listOf(user),
                tools = listOf(echo),
                streamTurns = listOf(
                    listOf(toolCallChunk("t1", "echo", """{"value":"pong"}""")),
                    listOf(textDelta("done")),
                ),
            )
        )

        assertEquals(1, echoCalls)
        assertEquals(2, result.steps)
        assertEquals(MessageRole.TOOL, result.messages[result.messages.lastIndex - 1].role)
        assertEquals("done", result.messages.last().toText())
        val toolResult = result.messages[result.messages.lastIndex - 1].parts
            .filterIsInstance<UIMessagePart.ToolResult>()
            .single()
        assertEquals("pong", (toolResult.content as JsonObject)["ok"]?.jsonPrimitive?.content)
    }

    @Test
    fun requiresApprovalMarksPendingAndSkipsExecute() = runBlocking {
        var executed = false
        val gated = Tool(
            name = "look_at_screen",
            description = "gated",
            approvalMode = ToolApprovalMode.RequiresApproval,
            execute = {
                executed = true
                JsonObject(emptyMap())
            },
        )
        val result = engine.generate(
            session(
                messages = listOf(UIMessage.user("Look")),
                tools = listOf(gated),
                streamTurns = listOf(
                    listOf(toolCallChunk("t-pending", "look_at_screen", "{}")),
                ),
            )
        )

        assertTrue(result.pendingApproval)
        assertFalse(executed)
        val pending = result.messages.last().getToolCalls().single()
        assertEquals(ToolApprovalState.Pending, pending.approvalState)
    }

    @Test
    fun injectsToolImagesAsFollowUpUserMessage() = runBlocking {
        val camera = Tool(
            name = "look_at_screen",
            description = "camera",
            execute = {
                buildJsonObject {
                    put(
                        PortableGenerationLoop.INJECTED_IMAGE_KEY,
                        kotlinx.serialization.json.JsonArray(listOf(JsonPrimitive("data:image/png;base64,abc"))),
                    )
                    put("note", JsonPrimitive("captured"))
                }
            },
        )
        val result = engine.generate(
            session(
                messages = listOf(UIMessage.user("See")),
                tools = listOf(camera),
                streamTurns = listOf(
                    listOf(toolCallChunk("t-img", "look_at_screen", "{}")),
                    listOf(textDelta("saw it")),
                ),
            )
        )

        val injected = result.messages.first { message ->
            message.role == MessageRole.USER && message.parts.any { it is UIMessagePart.Image }
        }
        assertTrue(injected.parts.any { it is UIMessagePart.Text })
        assertEquals("data:image/png;base64,abc", injected.parts.filterIsInstance<UIMessagePart.Image>().single().url)
        val toolJson = result.messages.first { it.role == MessageRole.TOOL }
            .parts.filterIsInstance<UIMessagePart.ToolResult>().single().content as JsonObject
        assertFalse(toolJson.containsKey(PortableGenerationLoop.INJECTED_IMAGE_KEY))
        assertEquals("saw it", result.messages.last().toText())
    }

    @Test
    fun formatToolExecutionError_usesConciseMessageWithoutStackTrace() {
        val error = formatToolExecutionError(IllegalStateException("Tool search_web not found"))
        assertEquals("Tool search_web not found", error)
        assertFalse(error.contains("IllegalStateException"))
        assertFalse(error.contains("\tat "))
    }

    private fun session(
        messages: List<UIMessage>,
        tools: List<Tool> = emptyList(),
        streamTurns: List<List<MessageChunk>>,
    ): PortableGenerationSession {
        var turn = 0
        return PortableGenerationSession(
            model = model,
            initialMessages = messages,
            tools = tools,
            streamText = { _, _ ->
                val chunks = streamTurns.getOrElse(turn) { emptyList() }
                turn += 1
                flow { chunks.forEach { emit(it) } }
            },
            generateText = { _, _ -> error("non-stream not used") },
        )
    }

    private fun textDelta(text: String): MessageChunk {
        return MessageChunk(
            id = Uuid.random().toString(),
            model = "test",
            choices = listOf(
                UIMessageChoice(
                    index = 0,
                    delta = UIMessage(role = MessageRole.ASSISTANT, parts = listOf(UIMessagePart.Text(text))),
                    message = null,
                    finishReason = null,
                )
            ),
        )
    }

    private fun toolCallChunk(id: String, name: String, arguments: String): MessageChunk {
        return MessageChunk(
            id = Uuid.random().toString(),
            model = "test",
            choices = listOf(
                UIMessageChoice(
                    index = 0,
                    delta = UIMessage(
                        role = MessageRole.ASSISTANT,
                        parts = listOf(
                            UIMessagePart.ToolCall(
                                toolCallId = id,
                                toolName = name,
                                arguments = arguments,
                            )
                        ),
                    ),
                    message = null,
                    finishReason = "tool_calls",
                )
            ),
        )
    }
}
