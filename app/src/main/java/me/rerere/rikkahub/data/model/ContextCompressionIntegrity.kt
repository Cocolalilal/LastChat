package me.rerere.rikkahub.data.model

fun canApplyContextCompression(source: Conversation, latest: Conversation, upToIndex: Int): Boolean =
    source.id == latest.id && source.contextSummary == latest.contextSummary &&
        source.contextSummaryUpToIndex == latest.contextSummaryUpToIndex &&
        source.lastRefreshTime == latest.lastRefreshTime && source.truncateIndex == latest.truncateIndex &&
        upToIndex in source.currentMessages.indices && upToIndex in latest.currentMessages.indices &&
        source.currentMessages.take(upToIndex + 1) == latest.currentMessages.take(upToIndex + 1)

/** Streaming snapshots may predate optimization. Preserve newer metadata only for the same source prefix. */
fun preserveNewerContextSummary(live: Conversation?, incoming: Conversation): Conversation {
    if (live == null || live.id != incoming.id || live.lastRefreshTime <= incoming.lastRefreshTime) return incoming
    val end = live.contextSummaryUpToIndex
    if (end >= 0 && (end !in incoming.currentMessages.indices ||
            live.currentMessages.take(end + 1) != incoming.currentMessages.take(end + 1))
    ) return incoming
    return incoming.copy(
        contextSummary = live.contextSummary,
        contextSummaryUpToIndex = live.contextSummaryUpToIndex,
        lastRefreshTime = live.lastRefreshTime,
    )
}

/** Older checkpoints remain frozen. Undo only changes what is sent, never the original messages. */
fun restorePreviousContextCheckpoint(conversation: Conversation): Conversation {
    val summary = conversation.contextSummary.orEmpty()
    val marker = Regex("""(?m)^### \[Milestone \d+\] <!-- lastchat:coverage=(\d+)\.\.(\d+) -->\n""")
        .findAll(summary).lastOrNull()
    val preceding = marker?.let { summary.take(it.range.first).trimEnd() }.orEmpty()
    val priorIndex = marker?.groupValues?.getOrNull(1)?.toIntOrNull()?.minus(1) ?: -1
    return conversation.copy(
        contextSummary = preceding.takeIf { it.isNotBlank() },
        contextSummaryUpToIndex = if (preceding.isBlank()) -1 else priorIndex,
        lastRefreshTime = maxOf(System.currentTimeMillis(), conversation.lastRefreshTime + 1),
    )
}
