package me.rerere.rikkahub.data.memory

import me.rerere.rikkahub.data.db.dao.MemoryBudgetDao
import me.rerere.rikkahub.data.db.entity.MemBudgetCategory

/**
 * Cost presets for the memory system (§8). Everything the memory system does that costs a model
 * call is metered against these; the deterministic paths (retrieval, decay, expiry, size
 * enforcement) ignore the budget entirely and always run.
 */
enum class MemoryPreset {
    OFF, ECO, BALANCED, RICH;

    companion object {
        fun fromNameOrDefault(name: String?): MemoryPreset =
            entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: BALANCED
    }
}

/** Resolved numeric caps for a preset. Cadence is "extract after this many un-encoded messages". */
data class MemoryBudgetCaps(
    val preset: MemoryPreset,
    val extractionCadenceMessages: Int,
    val extractionDailyCap: Int,
    val sleepDailyCap: Int,
    val profileDailyCap: Int,
    val curiosityWeeklyCap: Int,
    /** Token cap applied to the *context* portions of a prompt only (never the extraction window). */
    val contextTokenCap: Int,
) {
    val extractionEnabled: Boolean get() = preset != MemoryPreset.OFF && extractionDailyCap > 0

    companion object {
        fun of(preset: MemoryPreset): MemoryBudgetCaps = when (preset) {
            MemoryPreset.OFF -> MemoryBudgetCaps(preset, Int.MAX_VALUE, 0, 0, 0, 0, 1500)
            MemoryPreset.ECO -> MemoryBudgetCaps(preset, 25, 6, 1, 1, 0, 1200)
            MemoryPreset.BALANCED -> MemoryBudgetCaps(preset, 10, 24, 4, 2, 2, 1500)
            MemoryPreset.RICH -> MemoryBudgetCaps(preset, 6, 48, 8, 3, 3, 2000)
        }
    }
}

/**
 * Admission control + ledger for background model calls. Best-effort (check-then-record is not
 * strictly atomic), which is acceptable: the plan tolerates approximate budgets, and going one call
 * over a daily cap is harmless while *losing* content is not — deferral never drops messages
 * (watermark integrity, §5.1), it only delays them.
 */
class MemoryBudget(
    private val budgetDao: MemoryBudgetDao,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    fun dayKey(now: Long = clock()): Long = now / DAY_MILLIS

    suspend fun callsToday(category: String, now: Long = clock()): Int =
        budgetDao.callsToday(dayKey(now), category)

    suspend fun callsInLastWeek(category: String, now: Long = clock()): Int {
        val today = dayKey(now)
        var total = 0
        for (d in 0 until 7) {
            total += budgetDao.callsToday(today - d, category)
        }
        return total
    }

    /** Returns true (and records the call) if today's [dailyCap] for [category] is not yet reached. */
    suspend fun tryConsumeDaily(
        category: String,
        dailyCap: Int,
        tokensIn: Long = 0,
        tokensOut: Long = 0,
        now: Long = clock(),
    ): Boolean {
        if (dailyCap <= 0) return false
        if (callsToday(category, now) >= dailyCap) return false
        record(category, tokensIn, tokensOut, now)
        return true
    }

    /** Returns true (and records) if the rolling 7-day count for [category] is under [weeklyCap]. */
    suspend fun tryConsumeWeekly(
        category: String,
        weeklyCap: Int,
        tokensIn: Long = 0,
        tokensOut: Long = 0,
        now: Long = clock(),
    ): Boolean {
        if (weeklyCap <= 0) return false
        if (callsInLastWeek(category, now) >= weeklyCap) return false
        record(category, tokensIn, tokensOut, now)
        return true
    }

    suspend fun record(category: String, tokensIn: Long, tokensOut: Long, now: Long = clock()) {
        budgetDao.record(dayKey(now), category, tokensIn, tokensOut)
    }

    /** Housekeeping: drop ledger rows older than ~60 days. */
    suspend fun pruneOld(now: Long = clock()) {
        budgetDao.pruneOlderThan(dayKey(now) - 60)
    }

    companion object {
        const val DAY_MILLIS = 86_400_000L

        val ALL_CATEGORIES = listOf(
            MemBudgetCategory.EXTRACTION,
            MemBudgetCategory.SLEEP,
            MemBudgetCategory.PROFILE,
            MemBudgetCategory.CURIOSITY,
        )
    }
}
