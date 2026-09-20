package me.rerere.ai.generation

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.rerere.ai.core.MessageRole
import me.rerere.ai.core.Tool
import me.rerere.ai.core.ToolApprovalMode
import me.rerere.ai.core.merge
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.TextGenerationParams
import me.rerere.ai.ui.MessageChunk
import me.rerere.ai.ui.ToolApprovalState
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.ai.ui.handleMessageChunk
import kotlin.time.Clock

/**
 * The single stream + tool-step generation loop used by Android [GenerationHandler]
 * and iOS [me.rerere.lastchat.ios.IosAppController]. Hosts prepare provider
 * messages, tools, and visual transforms; this class owns merge, tool execution,
 * approval pending, injected images, and the 256-step ceiling.
 */
class PortableGenerationLoop(
    private val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    },
) {
    companion object {
        const val MAX_TOOL_STEPS = 256
        const val STREAMING_CHECKPOINT_INTERVAL_MS = 1_000L
        const val INJECTED_IMAGE_KEY = "__inject_user_image_parts"
        const val INJECTED_IMAGE_PROMPT = "Screenshot of the user's screen captured at summon:"
    }

    suspend fun run(session: PortableGenerationSession): PortableGenerationResult {
        var conversationMessages = session.initialMessages
        require(conversationMessages.isNotEmpty()) { "messages must not be empty" }
        var pendingApproval = false
        var steps = 0
        var lastCheckpointAt = session.nowMs()

        for (stepIndex in 0 until session.maxSteps) {
            steps = stepIndex + 1
            val tools = session.rebuildTools(stepIndex, conversationMessages)
            val turn = session.prepareTurn(stepIndex, conversationMessages, tools)
            conversationMessages = turn.conversationMessages
            val params = turn.params
            if (session.stream) {
                session.streamText(turn.providerMessages, params).collect { chunk ->
                    conversationMessages = session.onStreamChunk(conversationMessages, chunk, session.model)
                    session.onMessages(
                        session.visualTransform(conversationMessages),
                        PortableGenerationUpdate.Streaming,
                    )
                    val now = session.nowMs()
                    if (now - lastCheckpointAt >= session.checkpointIntervalMs) {
                        session.onCheckpoint(conversationMessages)
                        lastCheckpointAt = now
                    }
                }
            } else {
                val chunk = session.generateText(turn.providerMessages, params)
                conversationMessages = session.onStreamChunk(conversationMessages, chunk, session.model)
            }
            conversationMessages = session.afterAssistantTurn(conversationMessages)
            session.onMessages(conversationMessages, PortableGenerationUpdate.AssistantTurnFinished)

            val toolCalls = conversationMessages.lastOrNull()?.getToolCalls().orEmpty()
            if (toolCalls.isEmpty()) {
                break
            }

            val results = ArrayList<UIMessagePart.ToolResult>()
            val pendingToolCallIds = mutableSetOf<String>()
            for (toolCall in toolCalls) {
                val tool = tools.find { it.name == toolCall.toolName }
                if (tool?.approvalMode == ToolApprovalMode.RequiresApproval) {
                    pendingToolCallIds += toolCall.toolCallId
                    continue
                }
                val args = runCatching { parseToolCallArguments(toolCall.arguments) }
                    .getOrElse { JsonObject(emptyMap()) }
                val content = runCatching {
                    requireNotNull(tool) { "Tool ${toolCall.toolName} not found" }
                    session.onExecutingTool(toolCall.toolCallId)
                    try {
                        tool.execute(args)
                    } finally {
                        session.onExecutingTool(null)
                    }
                }.getOrElse { failure ->
                    buildJsonObject {
                        put("error", JsonPrimitive(formatToolExecutionError(failure)))
                    }
                }
                results += UIMessagePart.ToolResult(
                    toolName = toolCall.toolName,
                    toolCallId = toolCall.toolCallId,
                    content = content,
                    arguments = args,
                    metadata = toolCall.metadata,
                )
            }

            if (pendingToolCallIds.isNotEmpty()) {
                conversationMessages = conversationMessages.markPendingToolCalls(pendingToolCallIds)
                session.onMessages(conversationMessages, PortableGenerationUpdate.PendingApproval)
                if (results.isNotEmpty()) {
                    conversationMessages = appendToolResults(conversationMessages, results, session)
                    session.onMessages(
                        session.afterToolResults(conversationMessages),
                        PortableGenerationUpdate.ToolResults,
                    )
                }
                pendingApproval = true
                break
            }

            conversationMessages = appendToolResults(conversationMessages, results, session)
            conversationMessages = session.afterToolResults(conversationMessages)
            session.onMessages(conversationMessages, PortableGenerationUpdate.ToolResults)
            session.onCheckpoint(conversationMessages)
            lastCheckpointAt = session.nowMs()
        }

        return PortableGenerationResult(
            messages = conversationMessages,
            steps = steps,
            pendingApproval = pendingApproval,
        )
    }

    private suspend fun appendToolResults(
        messages: List<UIMessage>,
        results: List<UIMessagePart.ToolResult>,
        session: PortableGenerationSession,
    ): List<UIMessage> {
        val (sanitized, injectedImages) = extractInjectedImageParts(results)
        var next = messages + UIMessage(role = MessageRole.TOOL, parts = sanitized)
        if (injectedImages.isNotEmpty()) {
            next = next + UIMessage(
                role = MessageRole.USER,
                parts = listOf(UIMessagePart.Text(session.injectedImagePrompt)) + injectedImages,
            )
        }
        return next
    }

    fun parseToolCallArguments(arguments: String): JsonElement {
        val trimmed = arguments.trim()
        if (trimmed.isEmpty()) return JsonObject(emptyMap())
        json.parseToJsonElementOrNull(trimmed)?.let { return it }
        val start = trimmed.indexOf('{')
        val end = trimmed.lastIndexOf('}')
        if (start >= 0 && end > start) {
            json.parseToJsonElementOrNull(trimmed.substring(start, end + 1))?.let { return it }
        }
        error("Invalid tool arguments")
    }
}

data class PortableTurnRequest(
    val conversationMessages: List<UIMessage>,
    val providerMessages: List<UIMessage>,
    val params: TextGenerationParams,
)

enum class PortableGenerationUpdate {
    Streaming,
    AssistantTurnFinished,
    ToolResults,
    PendingApproval,
}

data class PortableGenerationResult(
    val messages: List<UIMessage>,
    val steps: Int,
    val pendingApproval: Boolean,
)

data class PortableGenerationSession(
    val model: Model,
    val initialMessages: List<UIMessage>,
    val tools: List<Tool> = emptyList(),
    val stream: Boolean = true,
    val maxSteps: Int = PortableGenerationLoop.MAX_TOOL_STEPS,
    val checkpointIntervalMs: Long = PortableGenerationLoop.STREAMING_CHECKPOINT_INTERVAL_MS,
    val injectedImagePrompt: String = PortableGenerationLoop.INJECTED_IMAGE_PROMPT,
    val streamText: suspend (messages: List<UIMessage>, params: TextGenerationParams) -> Flow<MessageChunk>,
    val generateText: suspend (messages: List<UIMessage>, params: TextGenerationParams) -> MessageChunk,
    val prepareTurn: suspend (
        step: Int,
        messages: List<UIMessage>,
        tools: List<Tool>,
    ) -> PortableTurnRequest = { _, messages, stepTools ->
        PortableTurnRequest(
            conversationMessages = messages,
            providerMessages = messages,
            params = TextGenerationParams(model = model, tools = stepTools),
        )
    },
    val rebuildTools: suspend (step: Int, messages: List<UIMessage>) -> List<Tool> = { _, _ -> tools },
    val afterAssistantTurn: suspend (List<UIMessage>) -> List<UIMessage> = { it },
    val afterToolResults: suspend (List<UIMessage>) -> List<UIMessage> = { it },
    val visualTransform: suspend (List<UIMessage>) -> List<UIMessage> = { it },
    val onStreamChunk: suspend (current: List<UIMessage>, chunk: MessageChunk, model: Model) -> List<UIMessage> =
        { current, chunk, chunkModel ->
            var next = current.handleMessageChunk(chunk = chunk, model = chunkModel)
            chunk.usage?.let { usage ->
                next = next.mapIndexed { index, message ->
                    if (index == next.lastIndex) {
                        message.copy(usage = message.usage.merge(usage))
                    } else {
                        message
                    }
                }
            }
            next
        },
    val onMessages: suspend (messages: List<UIMessage>, reason: PortableGenerationUpdate) -> Unit = { _, _ -> },
    val onCheckpoint: suspend (List<UIMessage>) -> Unit = {},
    val onExecutingTool: (String?) -> Unit = {},
    val nowMs: () -> Long = { Clock.System.now().toEpochMilliseconds() },
)

fun formatToolExecutionError(throwable: Throwable): String {
    return throwable.message
        ?.takeIf { it.isNotBlank() }
        ?: (throwable::class.simpleName ?: "Tool execution failed")
}

fun List<UIMessage>.markPendingToolCalls(toolCallIds: Set<String>): List<UIMessage> {
    if (toolCallIds.isEmpty()) return this
    return mapIndexed { index, message ->
        if (index != lastIndex) {
            message
        } else {
            message.copy(
                parts = message.parts.map { part ->
                    if (part is UIMessagePart.ToolCall && part.toolCallId in toolCallIds) {
                        part.copy(approvalState = ToolApprovalState.Pending)
                    } else {
                        part
                    }
                },
            )
        }
    }
}

fun extractInjectedImageParts(
    results: List<UIMessagePart.ToolResult>,
): Pair<List<UIMessagePart.ToolResult>, List<UIMessagePart.Image>> {
    val images = mutableListOf<UIMessagePart.Image>()
    val sanitized = results.map { result ->
        val content = result.content
        if (content is JsonObject && content.containsKey(PortableGenerationLoop.INJECTED_IMAGE_KEY)) {
            val urls = (content[PortableGenerationLoop.INJECTED_IMAGE_KEY] as? JsonArray)
                ?.mapNotNull { element ->
                    element.jsonPrimitive.contentOrNull?.takeIf { url -> url.isNotBlank() }
                }
                .orEmpty()
            urls.forEach { images += UIMessagePart.Image(url = it) }
            result.copy(content = JsonObject(content - PortableGenerationLoop.INJECTED_IMAGE_KEY))
        } else {
            result
        }
    }
    return sanitized to images
}

private fun Json.parseToJsonElementOrNull(raw: String): JsonElement? {
    return runCatching { parseToJsonElement(raw) }.getOrNull()
}
