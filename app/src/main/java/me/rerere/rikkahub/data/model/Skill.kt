package me.rerere.rikkahub.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

/**
 * A Claude Skill following the Agent Skills standard.
 * Skills are structured prompts with YAML frontmatter metadata and markdown instructions.
 * They can be imported/exported as SKILL.md files or app-native JSON.
 */
@Serializable
data class Skill(
    val id: Uuid = Uuid.random(),
    val name: String = "",               // Required: unique identifier (max 64 chars, lowercase/numbers/hyphens)
    val description: String = "",        // Required: concise explanation (max 1024 chars)
    val icon: String? = null,            // Material icon name, e.g. "code"
    val instructions: String = "",       // Markdown body of SKILL.md
    val attachments: List<ModeAttachment> = emptyList(), // Optional multimedia context attachments
    val enabled: Boolean = true,
    @SerialName("always_enabled")
    val alwaysEnabled: Boolean = false,              // When true, skill is enabled by default for available assistants
    @SerialName("available_for_all_assistants")
    val availableForAllAssistants: Boolean = true,
    val availableAssistantIds: Set<Uuid> = emptySet(),
    @SerialName("autonomous_for_all_assistants")
    val autonomousForAllAssistants: Boolean = false,
    @SerialName("autonomous_assistant_ids")
    val autonomousAssistantIds: Set<Uuid> = emptySet(),
    val injectionPosition: InjectionPosition = InjectionPosition.AFTER_SYSTEM,
    val depth: Int = 0,                  // Only used when injectionPosition is AT_DEPTH
    // Optional Claude Skills spec fields
    @SerialName("disable_model_invocation")
    val disableModelInvocation: Boolean = false,  // Legacy field retained for backward compatibility
    @SerialName("user_invocable")
    val userInvocable: Boolean = true,            // Legacy field retained for backward compatibility
    @SerialName("argument_hint")
    val argumentHint: String? = null,             // Slash command hint e.g. "/skill-name"
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun isAvailableForAssistant(assistantId: Uuid): Boolean {
        return availableForAllAssistants || availableAssistantIds.contains(assistantId)
    }

    fun canAssistantAutonomouslyToggle(assistantId: Uuid): Boolean {
        return isAvailableForAssistant(assistantId)
    }
}

val SKILL_SELECTION_OVERRIDE_ID: Uuid = Uuid.parse("00000000-0000-0000-0000-000000000001")

fun Set<Uuid>.hasManualSkillSelectionOverride(): Boolean {
    return contains(SKILL_SELECTION_OVERRIDE_ID)
}

fun Set<Uuid>.withoutSkillSelectionOverride(): Set<Uuid> {
    return this - SKILL_SELECTION_OVERRIDE_ID
}

/**
 * Export format for skills to share between LastChat users.
 */
@Serializable
data class SkillExport(
    val version: Int = 1,
    val format: String = "lastchat_skill",
    val skill: Skill
)
