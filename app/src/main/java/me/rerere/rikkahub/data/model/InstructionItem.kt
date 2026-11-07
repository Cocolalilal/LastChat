package me.rerere.rikkahub.data.model

import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

/**
 * Custom instructions appended to the end of the system prompt as a paragraph.
 * Can be reordered, enabled/disabled globally, and toggled per conversation.
 */
@Serializable
data class InstructionItem(
    val id: Uuid = Uuid.random(),
    val name: String = "",
    val content: String = "",
    val enabled: Boolean = true,
)
