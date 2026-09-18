package me.rerere.rikkahub.ui.components.avatar

import me.rerere.ai.ui.ToolApprovalState
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.ui.components.chat.ActivityState

/**
 * Map chat activity onto the avatar's face lifecycle.
 *
 * Completed turns resolve to Idle; the engine inserts a brief Done hold when
 * leaving active work, so a finished turn doesn't stay "happy" forever.
 */
fun blobLifecycleFromChat(
    activityState: ActivityState,
    loading: Boolean,
    blocked: Boolean = false,
): BlobLifecycle {
    if (blocked) return BlobLifecycle.Blocked
    return when (activityState) {
        is ActivityState.Waiting -> BlobLifecycle.Waiting
        is ActivityState.Reasoning,
        is ActivityState.LoadingModel -> BlobLifecycle.Thinking
        is ActivityState.ToolUse,
        is ActivityState.Ocr,
        is ActivityState.Replying -> BlobLifecycle.Working
        is ActivityState.CompletedSingle,
        is ActivityState.CompletedMultiple,
        is ActivityState.Hidden -> if (loading) BlobLifecycle.Working else BlobLifecycle.Idle
    }
}

fun List<UIMessagePart>.hasPendingToolApproval(): Boolean = any { part ->
    part is UIMessagePart.ToolCall && part.approvalState is ToolApprovalState.Pending
}
