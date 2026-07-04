package me.rerere.rikkahub.data.memory

import me.rerere.rikkahub.data.db.entity.MemSensitivity
import me.rerere.rikkahub.data.model.MemoryGoalState

/**
 * Pure, deterministic rules for the curiosity engine (§6.4) — the half that must be correct and
 * testable without a model, Room, or Android. The [CuriosityEngine] does the DB reads/writes and the
 * (budgeted) model/search calls around these decisions.
 *
 * Two guarantees live here, both structural rather than prompt-based:
 *  - **heavy rate limiting** on delivery (a goal is asked at most once — only a PRIMED goal delivers;
 *    ≥72h between any two asks by the character; ≤2 asks per character per week; never mid-roleplay
 *    scene; only after the chat has warmed up);
 *  - **back-off, not retry**: a declined goal is ABANDONED forever, and an unanswered ask escalates
 *    ASKED→IGNORED→ABANDONED purely on elapsed time — the engine never re-asks (never re-primes) a
 *    goal it already delivered, and never regenerates a goal for an abandoned goal's entities.
 */
object CuriosityLogic {

    /** A goal is only delivered once the conversation has warmed past this many messages. */
    const val MIN_CONVERSATION_MESSAGES = 6

    /** No two asks by the same character may fall within this window. */
    const val MIN_HOURS_BETWEEN_ASKS = 72.0

    /** Hard weekly cap on asks per character, enforced in code (§6.4). */
    const val WEEKLY_ASK_CAP = 2

    /** Only importance ≥ 3, non-sensitive beliefs can seed a goal (§6.4). */
    const val GOAL_MIN_IMPORTANCE = 3

    /**
     * All-must-hold delivery gate (§6.4). Deliberately conservative: any doubt → no ask. Only a
     * PRIMED goal delivers, so a goal already asked (ASKED/IGNORED) or retired (ABANDONED/CONFIRMED)
     * can never be re-delivered. `midRoleplay` is computed by the engine (recent fiction frame).
     */
    fun canDeliver(
        curiosityEnabled: Boolean,
        goalState: String?,
        messageCount: Int,
        midRoleplayScene: Boolean,
        hoursSinceLastAsk: Double,
        asksInLastWeek: Int,
    ): Boolean {
        if (!curiosityEnabled) return false
        if (goalState != MemoryGoalState.PRIMED) return false
        if (messageCount < MIN_CONVERSATION_MESSAGES) return false
        if (midRoleplayScene) return false
        if (hoursSinceLastAsk < MIN_HOURS_BETWEEN_ASKS) return false
        if (asksInLastWeek >= WEEKLY_ASK_CAP) return false
        return true
    }

    /** Whether a belief may seed a curiosity goal (§6.4): importance-gated, never sensitive. */
    fun isGoalCandidate(importance: Int, sensitivity: Int): Boolean =
        importance >= GOAL_MIN_IMPORTANCE && sensitivity == MemSensitivity.NORMAL

    /**
     * Back-off transition after a goal was asked and extraction later read the conversation:
     *  - CONFIRMED: the user answered → learned facts flow in via the applier.
     *  - DECLINED: the user declined → ABANDONED forever (kept as a "do not ask" marker).
     *  - anything else: leave ASKED, so the time-based ignore sweep can escalate it.
     */
    fun stateOnOutcome(outcome: String?): String = when (outcome?.trim()?.uppercase()) {
        MemoryGoalState.CONFIRMED, "YES", "ANSWERED" -> MemoryGoalState.CONFIRMED
        MemoryGoalState.DECLINED, "NO", "REFUSED" -> MemoryGoalState.ABANDONED
        else -> MemoryGoalState.ASKED
    }

    /**
     * Deterministic IGNORED escalation (no model, no retry): an asked-but-unanswered goal ages out
     * purely on elapsed time. First window → IGNORED (one miss); second window → ABANDONED ("ignored
     * twice"). The goal is never re-delivered in between — this is back-off, not a re-ask. Returns the
     * new goal state, or null if nothing changes yet.
     */
    fun escalateIgnored(goalState: String?, hoursSinceAsked: Double): String? = when (goalState) {
        MemoryGoalState.ASKED ->
            if (hoursSinceAsked >= MIN_HOURS_BETWEEN_ASKS) MemoryGoalState.IGNORED else null

        MemoryGoalState.IGNORED ->
            if (hoursSinceAsked >= 2 * MIN_HOURS_BETWEEN_ASKS) MemoryGoalState.ABANDONED else null

        else -> null
    }

    /** A goal in a terminal state is never re-primed and its entities are excluded from generation. */
    fun isTerminal(goalState: String?): Boolean =
        goalState == MemoryGoalState.ABANDONED || goalState == MemoryGoalState.CONFIRMED

    /** True while a goal is still occupying the character's single ask slot (avoid piling up goals). */
    fun isActiveGoal(goalState: String?): Boolean =
        goalState == MemoryGoalState.OPEN || goalState == MemoryGoalState.PRIMED ||
            goalState == MemoryGoalState.ASKED
}
