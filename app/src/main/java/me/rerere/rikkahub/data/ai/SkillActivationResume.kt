package me.rerere.rikkahub.data.ai

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.ToolApprovalState
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.ai.ui.toTurnGroups
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.Skill
import me.rerere.rikkahub.data.model.resolveSkillCommand
import kotlin.uuid.Uuid

private const val ACTIVATION_TOOL = "manage_skills"

/** Resume a paused turn's successful activations without carrying skills into the next user turn. */
internal fun resolveTurnScopedSkillIds(
    messages: List<UIMessage>,
    skills: List<Skill>,
    assistant: Assistant,
): Set<Uuid> {
    val activeTurn = messages.toTurnGroups().lastOrNull()?.messages ?: return emptySet()
    val command = activeTurn.first().takeIf { it.role == MessageRole.USER }?.toText().orEmpty()
    val active = setOfNotNull(resolveSkillCommand(command, skills, assistant.id)?.id).toMutableSet()

    val allowed = skills.asSequence().filter {
        it.canActivateAutonomously(assistant.id)
    }.map { it.id }.toSet()
    if (allowed.isEmpty()) return active

    val pendingActivations = mutableSetOf<String>()
    activeTurn.forEach { message ->
        when (message.role) {
            MessageRole.ASSISTANT -> message.getToolCalls().forEach { call ->
                if (call.toolName == ACTIVATION_TOOL && call.toolCallId.isNotBlank() &&
                    (call.approvalState == ToolApprovalState.Auto || call.approvalState == ToolApprovalState.Approved)
                ) {
                    pendingActivations += call.toolCallId
                } else {
                    pendingActivations -= call.toolCallId
                }
            }
            MessageRole.TOOL -> message.parts.filterIsInstance<UIMessagePart.ToolResult>().forEach { result ->
                if (result.toolName != ACTIVATION_TOOL || !pendingActivations.remove(result.toolCallId)) return@forEach
                val content = result.content as? JsonObject ?: return@forEach
                if ("error" in content) return@forEach
                val activated = content["activated"] as? JsonArray ?: return@forEach
                activated.forEach { entry ->
                    val rawId = (((entry as? JsonObject)?.get("id")) as? JsonPrimitive)?.contentOrNull
                    val id = rawId?.let { runCatching { Uuid.parse(it) }.getOrNull() }
                    if (id != null && id in allowed) active += id
                }
            }
            else -> Unit // Text and user-supplied parts are never evidence of an activation.
        }
    }
    return active
}
