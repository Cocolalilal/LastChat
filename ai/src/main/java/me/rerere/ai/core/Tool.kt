package me.rerere.ai.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import me.rerere.ai.provider.Model
import me.rerere.ai.ui.UIMessage

@Serializable
data class Tool(
    val name: String,
    val description: String,
    val parameters: () -> InputSchema? = { null },
    val systemPrompt: (model: Model, messages: List<UIMessage>) -> String = { _, _ -> "" },
    val approvalMode: ToolApprovalMode = ToolApprovalMode.Auto,
    val execute: suspend (JsonElement) -> JsonElement
)

@Serializable
enum class ToolApprovalMode {
    Auto,
    RequiresApproval,
}
