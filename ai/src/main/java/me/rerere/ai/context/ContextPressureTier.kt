package me.rerere.ai.context

/**
 * Pressure levels calculated relative to the usable input token budget.
 *
 * Tiers indicate how aggressively the context manager should compact or prune content:
 * - [LIGHT]: Zero mutation; 100% warm prefix cache.
 * - [NORMAL]: Light housekeeping (drop stale thinking blocks, cap media slots).
 * - [MODERATE]: Pre-emptive preparation (trigger background summarization, trim memory candidates).
 * - [HIGH]: Active compaction (convert older tool outputs to receipts, slice to recent turns + summary).
 * - [CRITICAL]: Emergency last resort (distill tool schemas, progressive text bisection).
 */
enum class ContextPressureTier {
    LIGHT,
    NORMAL,
    MODERATE,
    HIGH,
    CRITICAL;

    val isCompactionNeeded: Boolean get() = this >= MODERATE
    val isEmergency: Boolean get() = this == CRITICAL

    companion object {
        fun fromUsage(
            usedTokens: Int,
            usableTokens: Int,
            archetype: ContextScaleArchetype = ContextScaleArchetype.COMPACT,
        ): ContextPressureTier {
            val budget = usableTokens.coerceAtLeast(1)
            val fraction = (usedTokens.toDouble() / budget).coerceAtLeast(0.0)

            return when (archetype) {
                ContextScaleArchetype.MICRO -> {
                    val headroom = budget - usedTokens
                    when {
                        headroom > 1_500 && fraction < 0.70 -> LIGHT
                        headroom > 400 && fraction < 0.92 -> HIGH
                        else -> CRITICAL
                    }
                }
                ContextScaleArchetype.COMPACT -> when {
                    fraction < 0.50 -> LIGHT
                    fraction < 0.70 -> NORMAL
                    fraction < 0.85 -> MODERATE
                    fraction < 0.95 -> HIGH
                    else -> CRITICAL
                }
                ContextScaleArchetype.VAST -> when {
                    fraction < 0.65 -> LIGHT
                    fraction < 0.85 -> NORMAL
                    fraction < 0.95 -> HIGH
                    else -> CRITICAL
                }
            }
        }
    }
}
