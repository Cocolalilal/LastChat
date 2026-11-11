package me.rerere.rikkahub.data.ai

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart

/** Only the current user turn participates; older phone tasks never restart a later chat. */
internal fun phoneUseNeedsCompletion(messages: List<UIMessage>): Boolean {
    val turn = messages.drop((messages.indexOfLast { it.role == MessageRole.USER }).coerceAtLeast(0))
    val lastPhone = turn.flatMap { it.parts }.filterIsInstance<UIMessagePart.ToolCall>()
        .lastOrNull { it.toolName == "phone_use" } ?: return false
    val finished = runCatching {
        Json.parseToJsonElement(lastPhone.arguments).jsonObject["action"]?.jsonPrimitive?.content == "finish"
    }.getOrDefault(false)
    val reply = messages.lastOrNull()?.takeIf { it.role == MessageRole.ASSISTANT }?.toContentText().orEmpty()
    return !finished || reply.isBlank()
}

internal const val PHONE_USE_CONTINUATION_PROMPT =
    "The phone-use task has not been closed. Continue any remaining work and verify the requested outcome. " +
        "When done, or when a blocker requires the user, call phone_use with action finish, then give a concise final reply. " +
        "Do not repeat an already completed or irreversible action."
