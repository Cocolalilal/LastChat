package me.rerere.rikkahub.data.model

import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

/** An always-on prompt snippet, independently assigned to characters. */
@Serializable
data class AdditionalContext(
    val id: Uuid = Uuid.random(),
    val name: String = "",
    val prompt: String = "",
    val enabled: Boolean = true, // Legacy metadata; assigned contexts are always enabled.
    val assistantIds: Set<Uuid> = emptySet(),
    val allAssistants: Boolean = false,
    val excludedAssistantIds: Set<Uuid> = emptySet(),
    // Preserve placement and media when migrating legacy always-enabled skills.
    val injectionPosition: InjectionPosition = InjectionPosition.AFTER_SYSTEM,
    val depth: Int = 0,
    val attachments: List<ModeAttachment> = emptyList(),
) {
    fun appliesTo(assistantId: Uuid): Boolean =
        prompt.isNotBlank() && isCharacterAssigned(allAssistants, assistantIds, excludedAssistantIds, assistantId)

    internal fun asPromptInjection(): Skill = Skill(
        id = id,
        name = name,
        instructions = prompt,
        injectionPosition = injectionPosition,
        depth = depth,
        attachments = attachments,
    )
}

/** Resolve once for the picker, token estimate, and request assembly; retain Add-ons ordering. */
fun resolveAdditionalContexts(
    contexts: List<AdditionalContext>,
    assistantId: Uuid,
    conversationEnabledIds: Set<Uuid>? = null,
): List<AdditionalContext> = contexts.filter { context ->
    context.prompt.isNotBlank() &&
        (conversationEnabledIds?.contains(context.id) ?: context.appliesTo(assistantId))
}
