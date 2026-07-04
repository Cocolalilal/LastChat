package me.rerere.rikkahub.data.memory

import me.rerere.rikkahub.data.db.entity.MemSensitivity
import me.rerere.rikkahub.data.model.MemoryGoalState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-logic tests for the curiosity engine's guarantees (§6.4): the heavily rate-limited delivery
 * gate and the retry-free back-off. These are the correctness-critical halves of the feature and are
 * model-/Room-free by design (mirrors [MemorySleepLogicTest]).
 */
class CuriosityLogicTest {

    private fun deliver(
        enabled: Boolean = true,
        state: String? = MemoryGoalState.PRIMED,
        messages: Int = 10,
        midRoleplay: Boolean = false,
        hoursSinceLastAsk: Double = 1000.0,
        asksThisWeek: Int = 0,
    ) = CuriosityLogic.canDeliver(enabled, state, messages, midRoleplay, hoursSinceLastAsk, asksThisWeek)

    // ---------------- delivery gate ----------------

    @Test
    fun deliversWhenAllConditionsHold() {
        assertTrue(deliver())
    }

    @Test
    fun defaultOffBlocksDelivery() {
        assertFalse(deliver(enabled = false))
    }

    @Test
    fun onlyPrimedGoalsDeliver() {
        assertFalse(deliver(state = MemoryGoalState.OPEN))
        assertFalse(deliver(state = MemoryGoalState.ASKED))
        assertFalse(deliver(state = MemoryGoalState.IGNORED))
        assertFalse(deliver(state = MemoryGoalState.ABANDONED))
        assertFalse(deliver(state = MemoryGoalState.CONFIRMED))
        assertTrue(deliver(state = MemoryGoalState.PRIMED))
    }

    @Test
    fun coldConversationBlocksDelivery() {
        assertFalse(deliver(messages = CuriosityLogic.MIN_CONVERSATION_MESSAGES - 1))
        assertTrue(deliver(messages = CuriosityLogic.MIN_CONVERSATION_MESSAGES))
    }

    @Test
    fun midRoleplaySceneBlocksDelivery() {
        assertFalse(deliver(midRoleplay = true))
    }

    @Test
    fun asksTooCloseTogetherAreBlocked() {
        assertFalse(deliver(hoursSinceLastAsk = CuriosityLogic.MIN_HOURS_BETWEEN_ASKS - 1))
        assertTrue(deliver(hoursSinceLastAsk = CuriosityLogic.MIN_HOURS_BETWEEN_ASKS))
    }

    @Test
    fun weeklyAskCapIsEnforced() {
        assertFalse(deliver(asksThisWeek = CuriosityLogic.WEEKLY_ASK_CAP))
        assertFalse(deliver(asksThisWeek = CuriosityLogic.WEEKLY_ASK_CAP + 3))
        assertTrue(deliver(asksThisWeek = CuriosityLogic.WEEKLY_ASK_CAP - 1))
    }

    // ---------------- generation candidacy ----------------

    @Test
    fun onlyImportantNonSensitiveBeliefsSeedGoals() {
        assertTrue(CuriosityLogic.isGoalCandidate(3, MemSensitivity.NORMAL))
        assertTrue(CuriosityLogic.isGoalCandidate(5, MemSensitivity.NORMAL))
        assertFalse(CuriosityLogic.isGoalCandidate(2, MemSensitivity.NORMAL))
        assertFalse(CuriosityLogic.isGoalCandidate(5, MemSensitivity.SENSITIVE))
    }

    // ---------------- back-off, not retry ----------------

    @Test
    fun declineAbandonsForever() {
        assertEquals(MemoryGoalState.ABANDONED, CuriosityLogic.stateOnOutcome("DECLINED"))
        assertEquals(MemoryGoalState.ABANDONED, CuriosityLogic.stateOnOutcome("no"))
    }

    @Test
    fun confirmBanksTheAnswer() {
        assertEquals(MemoryGoalState.CONFIRMED, CuriosityLogic.stateOnOutcome("CONFIRMED"))
        assertEquals(MemoryGoalState.CONFIRMED, CuriosityLogic.stateOnOutcome("yes"))
    }

    @Test
    fun unrecognisedOutcomeStaysAskedForTheIgnoreSweep() {
        assertEquals(MemoryGoalState.ASKED, CuriosityLogic.stateOnOutcome("???"))
        assertEquals(MemoryGoalState.ASKED, CuriosityLogic.stateOnOutcome(null))
    }

    @Test
    fun ignoredEscalatesOnElapsedTimeWithoutReAsking() {
        // Fresh ask: nothing yet.
        assertNull(CuriosityLogic.escalateIgnored(MemoryGoalState.ASKED, hoursSinceAsked = 1.0))
        // First window → IGNORED (one miss).
        assertEquals(MemoryGoalState.IGNORED, CuriosityLogic.escalateIgnored(MemoryGoalState.ASKED, CuriosityLogic.MIN_HOURS_BETWEEN_ASKS))
        // IGNORED but not yet at the second window → still nothing.
        assertNull(CuriosityLogic.escalateIgnored(MemoryGoalState.IGNORED, CuriosityLogic.MIN_HOURS_BETWEEN_ASKS + 1))
        // Second window → ABANDONED (ignored twice).
        assertEquals(MemoryGoalState.ABANDONED, CuriosityLogic.escalateIgnored(MemoryGoalState.IGNORED, 2 * CuriosityLogic.MIN_HOURS_BETWEEN_ASKS))
    }

    @Test
    fun terminalGoalsNeverEscalateOrReprime() {
        assertNull(CuriosityLogic.escalateIgnored(MemoryGoalState.ABANDONED, 10_000.0))
        assertNull(CuriosityLogic.escalateIgnored(MemoryGoalState.CONFIRMED, 10_000.0))
        assertNull(CuriosityLogic.escalateIgnored(MemoryGoalState.PRIMED, 10_000.0))
        assertTrue(CuriosityLogic.isTerminal(MemoryGoalState.ABANDONED))
        assertTrue(CuriosityLogic.isTerminal(MemoryGoalState.CONFIRMED))
        assertFalse(CuriosityLogic.isTerminal(MemoryGoalState.PRIMED))
    }

    @Test
    fun activeGoalGuardPreventsPilingUp() {
        assertTrue(CuriosityLogic.isActiveGoal(MemoryGoalState.OPEN))
        assertTrue(CuriosityLogic.isActiveGoal(MemoryGoalState.PRIMED))
        assertTrue(CuriosityLogic.isActiveGoal(MemoryGoalState.ASKED))
        assertFalse(CuriosityLogic.isActiveGoal(MemoryGoalState.ABANDONED))
        assertFalse(CuriosityLogic.isActiveGoal(MemoryGoalState.CONFIRMED))
        assertFalse(CuriosityLogic.isActiveGoal(MemoryGoalState.IGNORED))
    }
}
