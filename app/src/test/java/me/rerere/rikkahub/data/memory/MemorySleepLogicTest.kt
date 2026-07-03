package me.rerere.rikkahub.data.memory

import me.rerere.rikkahub.data.db.entity.MemNodeType
import me.rerere.rikkahub.data.db.entity.MemSource
import me.rerere.rikkahub.data.db.entity.MemStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-logic tests for the deterministic sleep-pass stages (§5.4 stages 1/2/8, §9): retention
 * scoring, the decay/forget ladder, provisional promotion (incl. the adjudication-flag gate),
 * validity expiry, MANUAL protection, deterministic MERGE, and the eviction order. Mirrors
 * `MemoryReconcileTest` — the model-assisted stages are exercised separately.
 */
class MemorySleepLogicTest {

    private val DAY = 86_400_000L
    private val NOW = 1_800_000_000_000L

    private fun state(
        status: Int,
        type: Int = MemNodeType.FACT,
        pinned: Boolean = false,
        importance: Int = 3,
        reinforced: Int = 0,
        retrieved: Int = 0,
        lastAccessedAt: Long = NOW,
        recordedAt: Long = NOW,
        source: Int = MemSource.EXTRACTED,
    ) = MemorySleepLogic.NodeState(status, type, pinned, importance, reinforced, retrieved, lastAccessedAt, recordedAt, source)

    // ---------------- retention score ----------------

    @Test
    fun retentionRisesWithImportanceAndFallsWithAge() {
        val fresh = MemorySleepLogic.retentionScore(5, 0, 0, NOW, NOW)
        val stale = MemorySleepLogic.retentionScore(5, 0, 0, NOW - 60 * DAY, NOW)
        assertTrue(fresh > stale)
        val low = MemorySleepLogic.retentionScore(1, 0, 0, NOW, NOW)
        val high = MemorySleepLogic.retentionScore(5, 0, 0, NOW, NOW)
        assertTrue(high > low)
    }

    @Test
    fun retentionRewardsGenuineRecallAndReinforcement() {
        val base = MemorySleepLogic.retentionScore(3, 0, 0, NOW, NOW)
        val reinforced = MemorySleepLogic.retentionScore(3, 5, 0, NOW, NOW)
        val retrieved = MemorySleepLogic.retentionScore(3, 0, 5, NOW, NOW)
        assertTrue(reinforced > base)
        assertTrue(retrieved > base)
    }

    // ---------------- decay ladder (stage 1) ----------------

    @Test
    fun freshlyLearnedNodeIsNeverDemotedEvenIfLowImportance() {
        // Below MIN_DECAY_AGE_DAYS the node is protected regardless of its retention score.
        val s = state(MemStatus.ACTIVE, importance = 1, lastAccessedAt = NOW - 5 * DAY)
        assertEquals(MemorySleepLogic.DecayAction.KEEP, MemorySleepLogic.decayAction(s, NOW))
    }

    @Test
    fun oldLowImportanceActiveDemotesToDormant() {
        val s = state(MemStatus.ACTIVE, importance = 1, lastAccessedAt = NOW - 40 * DAY)
        assertEquals(MemorySleepLogic.DecayAction.DEMOTE_DORMANT, MemorySleepLogic.decayAction(s, NOW))
    }

    @Test
    fun recallStrengthenedActiveStaysActive() {
        // A recently-retrieved fact (last_accessed bumped) keeps a high score → not demoted.
        val s = state(MemStatus.ACTIVE, importance = 3, lastAccessedAt = NOW - 20 * DAY)
        assertEquals(MemorySleepLogic.DecayAction.KEEP, MemorySleepLogic.decayAction(s, NOW))
    }

    @Test
    fun deeplyStaleDormantIsForgotten() {
        val s = state(MemStatus.DORMANT, importance = 1, lastAccessedAt = NOW - 70 * DAY)
        assertEquals(MemorySleepLogic.DecayAction.FORGET, MemorySleepLogic.decayAction(s, NOW))
    }

    @Test
    fun dormantWithLivingRetentionIsKept() {
        val s = state(MemStatus.DORMANT, importance = 3, lastAccessedAt = NOW - 30 * DAY)
        assertEquals(MemorySleepLogic.DecayAction.KEEP, MemorySleepLogic.decayAction(s, NOW))
    }

    @Test
    fun forgottenIsDeletedOnlyAfterGrace() {
        val past = state(MemStatus.FORGOTTEN, lastAccessedAt = NOW - 31 * DAY)
        val within = state(MemStatus.FORGOTTEN, lastAccessedAt = NOW - 10 * DAY)
        assertEquals(MemorySleepLogic.DecayAction.DELETE, MemorySleepLogic.decayAction(past, NOW))
        assertEquals(MemorySleepLogic.DecayAction.KEEP, MemorySleepLogic.decayAction(within, NOW))
    }

    @Test
    fun pinnedAndEntityNodesAreExemptFromDecay() {
        val pinned = state(MemStatus.ACTIVE, pinned = true, importance = 1, lastAccessedAt = NOW - 400 * DAY)
        val entity = state(MemStatus.ACTIVE, type = MemNodeType.ENTITY, importance = 1, lastAccessedAt = NOW - 400 * DAY)
        assertEquals(MemorySleepLogic.DecayAction.KEEP, MemorySleepLogic.decayAction(pinned, NOW))
        assertEquals(MemorySleepLogic.DecayAction.KEEP, MemorySleepLogic.decayAction(entity, NOW))
    }

    // ---------------- promotion (stage 1) ----------------

    @Test
    fun provisionalPromotesAfterSevenQuietDays() {
        val s = state(MemStatus.PROVISIONAL, importance = 3, recordedAt = NOW - 8 * DAY)
        assertTrue(MemorySleepLogic.canAutoPromote(s, hasContradictsEdge = false, adjudicationPending = false, now = NOW))
    }

    @Test
    fun promotionRequiresImportanceAndAge() {
        val lowImportance = state(MemStatus.PROVISIONAL, importance = 2, recordedAt = NOW - 30 * DAY)
        val tooYoung = state(MemStatus.PROVISIONAL, importance = 4, recordedAt = NOW - 3 * DAY)
        assertFalse(MemorySleepLogic.canAutoPromote(lowImportance, false, false, NOW))
        assertFalse(MemorySleepLogic.canAutoPromote(tooYoung, false, false, NOW))
    }

    @Test
    fun contradictionOrAdjudicationFlagBlocksPromotion() {
        val s = state(MemStatus.PROVISIONAL, importance = 5, recordedAt = NOW - 30 * DAY)
        assertFalse(MemorySleepLogic.canAutoPromote(s, hasContradictsEdge = true, adjudicationPending = false, now = NOW))
        assertFalse(MemorySleepLogic.canAutoPromote(s, hasContradictsEdge = false, adjudicationPending = true, now = NOW))
        // The gate is exactly what makes both halves of an unadjudicated pair wait for stage 3.
        assertTrue(MemorySleepLogic.canAutoPromote(s, hasContradictsEdge = false, adjudicationPending = false, now = NOW))
    }

    @Test
    fun onlyProvisionalNodesPromote() {
        val active = state(MemStatus.ACTIVE, importance = 5, recordedAt = NOW - 30 * DAY)
        assertFalse(MemorySleepLogic.canAutoPromote(active, false, false, NOW))
    }

    // ---------------- expiry (stage 2) ----------------

    @Test
    fun pastValidUntilExpiresFact() {
        assertEquals(
            NOW - 1000L,
            MemorySleepLogic.expiryDueAt(MemStatus.ACTIVE, MemNodeType.FACT, NOW - 1000L, NOW, null, NOW),
        )
    }

    @Test
    fun pastHorizonExpiresFact() {
        // recorded 10 days ago with a 5-day horizon → due 5 days ago.
        val due = MemorySleepLogic.expiryDueAt(MemStatus.ACTIVE, MemNodeType.FACT, null, NOW - 10 * DAY, 5 * DAY, NOW)
        assertEquals(NOW - 5 * DAY, due)
    }

    @Test
    fun futureValidityDoesNotExpire() {
        assertNull(MemorySleepLogic.expiryDueAt(MemStatus.ACTIVE, MemNodeType.FACT, NOW + DAY, NOW, null, NOW))
        assertNull(MemorySleepLogic.expiryDueAt(MemStatus.ACTIVE, MemNodeType.FACT, null, NOW, 10 * DAY, NOW))
    }

    @Test
    fun onlyLiveFactsExpire() {
        assertNull(MemorySleepLogic.expiryDueAt(MemStatus.ACTIVE, MemNodeType.EPISODE, NOW - 1000L, NOW, null, NOW))
        assertNull(MemorySleepLogic.expiryDueAt(MemStatus.CLOSED, MemNodeType.FACT, NOW - 1000L, NOW, null, NOW))
        assertNull(MemorySleepLogic.expiryDueAt(MemStatus.ACTIVE, MemNodeType.FACT, null, NOW, null, NOW))
    }

    // ---------------- MANUAL protection (stages 3/4) ----------------

    @Test
    fun manualAndConfirmedNodesAreProtected() {
        assertTrue(MemorySleepLogic.isManualProtected(MemSource.MANUAL))
        assertTrue(MemorySleepLogic.isManualProtected(MemSource.CONFIRMED_BY_USER))
        assertFalse(MemorySleepLogic.isManualProtected(MemSource.EXTRACTED))
        assertFalse(MemorySleepLogic.isManualProtected(MemSource.IMPORTED))
    }

    // ---------------- deterministic MERGE (stage 3) ----------------

    @Test
    fun mergeFieldsCombineDeterministically() {
        val a = MemorySleepLogic.MergeInput(pinned = false, timesReinforced = 2, importance = 3, confidence = 0.6f, recordedAt = 100, lastConfirmedAt = 500)
        val b = MemorySleepLogic.MergeInput(pinned = true, timesReinforced = 3, importance = 5, confidence = 0.9f, recordedAt = 50, lastConfirmedAt = 400)
        val merged = MemorySleepLogic.mergeFields(a, b)
        assertTrue(merged.pinned)
        assertEquals(5, merged.timesReinforced)
        assertEquals(5, merged.importance)
        assertEquals(0.9f, merged.confidence, 0.0001f)
        assertEquals(50L, merged.recordedAt)
        assertEquals(500L, merged.lastConfirmedAt)
    }

    // ---------------- eviction order (stage 8 / §9) ----------------

    @Test
    fun evictionOrdersLowestRetentionFirst() {
        val strong = MemorySleepLogic.RetentionRef("strong", importance = 5, timesReinforced = 4, timesRetrieved = 3, lastAccessedAt = NOW - 2 * DAY)
        val weak = MemorySleepLogic.RetentionRef("weak", importance = 1, timesReinforced = 0, timesRetrieved = 0, lastAccessedAt = NOW - 90 * DAY)
        val middle = MemorySleepLogic.RetentionRef("middle", importance = 3, timesReinforced = 0, timesRetrieved = 0, lastAccessedAt = NOW - 20 * DAY)
        val order = MemorySleepLogic.evictionOrder(listOf(strong, middle, weak), NOW)
        assertEquals(listOf("weak", "middle", "strong"), order)
    }

    @Test
    fun evictionOrderIsDeterministicOnTies() {
        val a = MemorySleepLogic.RetentionRef("a", importance = 2, timesReinforced = 0, timesRetrieved = 0, lastAccessedAt = NOW - 10 * DAY)
        val b = MemorySleepLogic.RetentionRef("b", importance = 2, timesReinforced = 0, timesRetrieved = 0, lastAccessedAt = NOW - 10 * DAY)
        assertEquals(listOf("a", "b"), MemorySleepLogic.evictionOrder(listOf(b, a), NOW))
    }
}
