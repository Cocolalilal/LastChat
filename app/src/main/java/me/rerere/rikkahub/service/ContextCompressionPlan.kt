package me.rerere.rikkahub.service

import me.rerere.ai.context.effectiveHistoryForContext
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.context.ContextPlanner
import me.rerere.ai.context.ContextTokenEstimator
import me.rerere.ai.context.MilestoneSlicePlan
import me.rerere.ai.context.smartInputBudget
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.contextCapacityTokens
import me.rerere.ai.ui.toTurnGroups
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.contextCompressionPolicy

data class ContextCompressionPreview(
    val slice: MilestoneSlicePlan,
    val sourceTokens: Int,
    val recentExchanges: Int,
    val alreadyCoveredMessages: Int,
) {
    val messagesToCompress: Int get() = slice.lastIndexToSummarize - slice.startIndex + 1
}

/** Shared by the sheet and executor: previews must describe the exact same eligible prefix. */
fun previewContextCompression(
    conversation: Conversation,
    assistant: Assistant,
    chatModel: Model,
    automatic: Boolean = false,
): ContextCompressionPreview? {
    val messages = conversation.currentMessages
    val active = activeContextMessages(conversation)
    if (active.isEmpty()) return null
    val start = messages.size - active.size
    val policy = assistant.contextCompressionPolicy
    val usable = smartInputBudget(chatModel, assistant.maxTokens) ?: chatModel.contextCapacityTokens ?: 16_000
    val plan = ContextPlanner.plan(
        active, chatModel, usable,
        systemPromptTokens = ContextTokenEstimator.textTokens(
            assistant.systemPrompt + "\n" + conversation.contextSummary.orEmpty(), chatModel,
        ),
        policy = policy,
    )
    val fraction = policy.style.summaryFraction
    val target = if (automatic) {
        ((plan.lowWatermarkTargetTokens - ContextTokenEstimator.messagesTokens(active, chatModel) * fraction) /
            (1f - fraction)).toInt().coerceAtLeast(1)
    } else 0
    val slice = ContextPlanner.calculateMilestoneSlice(
        messages.toTurnGroups(), start, plan.archetype, plan.pressureTier, messages, chatModel, target, policy,
    ) ?: return null
    return ContextCompressionPreview(
        slice, ContextTokenEstimator.messagesTokens(messages.subList(start, slice.lastIndexToSummarize + 1), chatModel),
        messages.drop(slice.lastIndexToSummarize + 1).toTurnGroups().size, start,
    )
}

fun activeContextMessages(conversation: Conversation): List<UIMessage> {
    val messages = conversation.currentMessages
    if (conversation.truncateIndex >= messages.size && conversation.truncateIndex >= 0) return emptyList()
    return effectiveHistoryForContext(
        messages, smartManagement = true,
        summaryUpToIndex = if (conversation.contextSummary.isNullOrBlank()) -1 else conversation.contextSummaryUpToIndex,
        truncateIndex = conversation.truncateIndex,
    )
}
