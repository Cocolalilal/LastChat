package me.rerere.rikkahub.service

internal const val MIN_CONSOLIDATION_MESSAGES = 4
internal const val IMMEDIATE_CONSOLIDATION_MESSAGES = 8

internal sealed interface MemoryConsolidationDecision {
    data object NotWorthwhile : MemoryConsolidationDecision

    data class Schedule(
        val delayMillis: Long,
    ) : MemoryConsolidationDecision
}

/**
 * Small conversations are acknowledged without spending a model call. Medium conversations are
 * debounced until they have been idle for the configured interval, while substantial conversations
 * can be consolidated immediately. This keeps consolidation automatic without firing after every
 * trivial exchange.
 */
internal fun decideMemoryConsolidation(
    meaningfulMessageCount: Int,
    idleMillis: Long,
    configuredDelayMinutes: Int,
): MemoryConsolidationDecision {
    if (meaningfulMessageCount < MIN_CONSOLIDATION_MESSAGES) {
        return MemoryConsolidationDecision.NotWorthwhile
    }
    if (meaningfulMessageCount >= IMMEDIATE_CONSOLIDATION_MESSAGES) {
        return MemoryConsolidationDecision.Schedule(delayMillis = 0L)
    }

    val requiredIdleMillis = configuredDelayMinutes
        .coerceIn(0, 24 * 60)
        .toLong() * 60_000L
    return MemoryConsolidationDecision.Schedule(
        delayMillis = (requiredIdleMillis - idleMillis.coerceAtLeast(0L)).coerceAtLeast(0L),
    )
}
