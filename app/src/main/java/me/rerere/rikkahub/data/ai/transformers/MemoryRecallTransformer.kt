package me.rerere.rikkahub.data.ai.transformers

import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.common.platform.PlatformLog
import me.rerere.rikkahub.data.memory.MemoryRecall

/**
 * Injects the deterministic graph-memory recall section into the system prompt (§5.5), mirroring
 * [WorkspaceReminderTransformer]. Built per request by ChatService when the graph memory system is
 * active; the legacy `memories` injection is disabled in that case, so this is the single memory
 * section. Extraction failures never surface here — a recall failure just injects nothing.
 */
class MemoryRecallTransformer(
    private val recall: MemoryRecall,
    private val activeConversationId: String?,
    private val timeAwareness: Boolean,
    private val curiosityEnabled: Boolean = false,
) : InputMessageTransformer {

    override suspend fun transform(
        ctx: TransformerContext,
        messages: List<UIMessage>,
    ): List<UIMessage> {
        val section = try {
            recall.buildInjectionSection(
                assistantId = ctx.assistant.id.toString(),
                activeConversationId = activeConversationId,
                messages = messages,
                timeAwareness = timeAwareness,
                curiosityEnabled = curiosityEnabled,
            )
        } catch (e: Exception) {
            PlatformLog.e("MemoryRecall", "recall failed: ${e.message}")
            ""
        }
        if (section.isBlank()) return messages

        val systemIndex = messages.indexOfFirst { it.role == MessageRole.SYSTEM }
        return if (systemIndex >= 0) {
            messages.toMutableList().apply {
                this[systemIndex] = this[systemIndex].appendMemoryText("\n\n$section")
            }
        } else {
            listOf(UIMessage.system(section)) + messages
        }
    }

    private fun UIMessage.appendMemoryText(extra: String): UIMessage {
        val updatedParts = parts.toMutableList()
        val firstTextIndex = updatedParts.indexOfFirst { it is UIMessagePart.Text }
        if (firstTextIndex >= 0) {
            val text = updatedParts[firstTextIndex] as UIMessagePart.Text
            updatedParts[firstTextIndex] = text.copy(text = text.text + extra)
        } else {
            updatedParts.add(UIMessagePart.Text(extra))
        }
        return copy(parts = updatedParts)
    }
}
