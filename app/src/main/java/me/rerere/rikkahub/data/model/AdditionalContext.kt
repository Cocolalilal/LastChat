package me.rerere.rikkahub.data.model

import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

/** An always-on prompt snippet, independently assigned to characters. */
@Serializable
data class AdditionalContext(
    val id: Uuid = Uuid.random(),
    val name: String = "",
    val prompt: String = "",
    val enabled: Boolean = true,
    val assistantIds: Set<Uuid> = emptySet(),
    val allAssistants: Boolean = false,
    // Preserve placement and media when migrating legacy always-enabled skills.
    val injectionPosition: InjectionPosition = InjectionPosition.AFTER_SYSTEM,
    val depth: Int = 0,
    val attachments: List<ModeAttachment> = emptyList(),
) {
    fun appliesTo(assistantId: Uuid): Boolean =
        enabled && prompt.isNotBlank() && (allAssistants || assistantId in assistantIds)

    internal fun asPromptInjection(): Skill = Skill(
        id = id,
        name = name,
        instructions = prompt,
        injectionPosition = injectionPosition,
        depth = depth,
        attachments = attachments,
    )
}
