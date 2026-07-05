package me.rerere.rikkahub.data.memory

import me.rerere.rikkahub.data.db.entity.MemActivityKind

/**
 * Decides whether a memory activity row is "interesting" enough to surface the in-chat activity ball
 * (§7.5, redesigned). The ball must appear only when something genuinely happened — new memories,
 * merges, promotions, contradictions resolved — and stay silent for routine reinforcement and
 * housekeeping (decay, expiry, eviction, embedding, branch demotion), which would otherwise make it
 * blink constantly and mean nothing.
 *
 * Pure + unit-tested. The EXTRACTED/IMPORTED/MANUAL summary parsing is locked to
 * [MemoryOpApplier]'s exact format ("N new, M reinforced, K updated, L closed").
 */
object MemoryActivityInterest {

    /** Kinds that are always worth surfacing regardless of summary. */
    private val ALWAYS_INTERESTING = setOf(
        MemActivityKind.MERGED,
        MemActivityKind.PROMOTED,
        MemActivityKind.PROMOTION_SUGGESTED,
        MemActivityKind.ADJUDICATED,
        MemActivityKind.HABIT_INDUCED,
        MemActivityKind.GOAL_RESOLVED,
    )

    /** Kinds whose interest depends on whether the pass actually added/changed anything. */
    private val SUMMARY_GATED = setOf(
        MemActivityKind.EXTRACTED,
        MemActivityKind.IMPORTED,
        MemActivityKind.MANUAL_ADDED,
        MemActivityKind.UPDATED,
    )

    // A "<count> new|updated|closed" part means real change; the empty-summary fallback
    // ("memory updated") and reinforce-only passes ("3 reinforced") do not match.
    private val INTERESTING_PART = Regex("""\d+\s+(new|updated|closed)""")

    fun isInteresting(kind: String, summary: String): Boolean {
        if (kind in ALWAYS_INTERESTING) return true
        if (kind !in SUMMARY_GATED) return false
        return INTERESTING_PART.containsMatchIn(summary.lowercase())
    }
}
