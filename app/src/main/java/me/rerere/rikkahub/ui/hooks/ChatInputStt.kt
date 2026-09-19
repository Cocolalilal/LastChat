package me.rerere.rikkahub.ui.hooks

/**
 * STT stop/send helpers shared by the main composer and the Android assistant overlay.
 *
 * The overlay auto-send path and [MinimalChatInput]'s delayed STT commit both write the
 * same [ChatInputState]. After a successful send, [ChatInputState.clearInput] bumps
 * [ChatInputState.sttCommitEpoch] so a late final transcript cannot refill the composer.
 */
internal fun shouldApplyPendingSttTranscript(
    accept: Boolean,
    epochAtCapture: Int,
    epochNow: Int,
): Boolean {
    return accept && epochAtCapture == epochNow
}

internal fun mergeCommittedSttTranscript(existing: String, transcript: String): String {
    val prefix = existing.trim()
    val spoken = transcript.trim()
    if (spoken.isBlank()) return existing
    if (prefix.isBlank()) return spoken
    if (prefix == spoken || prefix.endsWith(spoken)) return prefix
    return "$prefix $spoken"
}
