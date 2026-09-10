package me.rerere.ai.context

/**
 * Categorizes a model's context capacity into operational scale archetypes.
 *
 * Rather than applying fixed percentage tiers across all model sizes, context management
 * strategies adapt to the scale of the context window:
 * - [MICRO]: Windows <= 8,192 tokens. Fixed overhead dominates; early tool receiptification
 *   and tight history are mandatory.
 * - [COMPACT]: Windows between 8,193 and 65,536 tokens. Standard 4-tier gradual compaction.
 * - [VAST]: Windows > 65,536 tokens. Scarcity is negligible; actions are driven by
 *   cache-epoch boundaries and attention preservation.
 */
enum class ContextScaleArchetype {
    MICRO,
    COMPACT,
    VAST;

    companion object {
        const val MICRO_MAX_TOKENS = 8_192
        const val COMPACT_MAX_TOKENS = 65_536

        fun fromCapacity(capacityTokens: Int): ContextScaleArchetype = when {
            capacityTokens <= MICRO_MAX_TOKENS -> MICRO
            capacityTokens <= COMPACT_MAX_TOKENS -> COMPACT
            else -> VAST
        }
    }
}
