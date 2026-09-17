package me.rerere.rikkahub.ui.components.avatar

import me.rerere.ai.ui.ToolApprovalState
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.ui.components.chat.ActivityState

enum class BlobLifecycle {
    Idle,
    Thinking,
    Working,
    Waiting,
    Blocked,
    Done,
}

internal fun BlobLifecycle.isActiveWork(): Boolean = when (this) {
    BlobLifecycle.Thinking,
    BlobLifecycle.Working,
    BlobLifecycle.Waiting,
    BlobLifecycle.Blocked -> true
    BlobLifecycle.Idle,
    BlobLifecycle.Done -> false
}

/**
 * Map chat activity onto the face lifecycle.
 *
 * Completed turns become Idle — the runtime inserts a short Done hold when
 * leaving active work, so historical messages do not stay "happy" forever.
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

fun List<UIMessagePart>.hasPendingToolApproval(): Boolean {
    return any { part ->
        part is UIMessagePart.ToolCall && part.approvalState is ToolApprovalState.Pending
    }
}
