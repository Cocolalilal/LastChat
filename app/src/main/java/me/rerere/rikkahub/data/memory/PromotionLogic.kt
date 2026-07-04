package me.rerere.rikkahub.data.memory

import me.rerere.rikkahub.data.db.entity.MemNodeType
import me.rerere.rikkahub.data.db.entity.MemScope
import me.rerere.rikkahub.data.db.entity.MemSensitivity
import me.rerere.rikkahub.data.db.entity.MemStatus
import me.rerere.rikkahub.data.model.MemoryFactCategory

/**
 * Pure, deterministic scope-promotion policy (§6.5) — the CHARACTER→GLOBAL_USER decision that must
 * never leak a secret. It rests ultimately on a cheap model's `category` label, so the rule is
 * conservative by construction and lives here (testable, model-free) rather than in a prompt:
 *
 *  - **AUTO** only for an ACTIVE, NORMAL-sensitivity user FACT whose category is on the closed
 *    identity-level whitelist (name/pronouns/language/timezone/occupation-study). One misclassified
 *    secret is exactly the leak §6.5 forbids, so nothing outside the whitelist auto-promotes.
 *  - **SUGGEST** for other ACTIVE, NORMAL user facts → an opt-in confirmation chip; non-action stays
 *    private (the safe default).
 *  - **SKIP** otherwise. SENSITIVE never promotes and never chips. Already-global nodes are done.
 */
object PromotionLogic {

    enum class Action { AUTO, SUGGEST, SKIP }

    fun classify(
        scope: Int,
        status: Int,
        sensitivity: Int,
        type: Int,
        isAboutUser: Boolean,
        category: String?,
    ): Action {
        // Only private, live user facts are ever eligible; SENSITIVE is categorically excluded.
        if (scope != MemScope.CHARACTER) return Action.SKIP
        if (status != MemStatus.ACTIVE) return Action.SKIP
        if (sensitivity != MemSensitivity.NORMAL) return Action.SKIP
        if (type != MemNodeType.FACT) return Action.SKIP
        if (!isAboutUser) return Action.SKIP

        val normalized = category?.trim()?.lowercase()
        return if (normalized != null && normalized in MemoryFactCategory.PROMOTION_WHITELIST) {
            Action.AUTO
        } else {
            Action.SUGGEST
        }
    }
}
