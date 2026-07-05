package me.rerere.rikkahub.data.memory

import me.rerere.rikkahub.data.db.entity.MemActivityKind
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The in-chat memory ball only appears for genuinely interesting activity. Summary parsing is locked
 * to [MemoryOpApplier]'s exact format ("N new, M reinforced, K updated, L closed").
 */
class MemoryActivityInterestTest {

    @Test
    fun mergesPromotionsAdjudicationsAlwaysInteresting() {
        listOf(
            MemActivityKind.MERGED, MemActivityKind.PROMOTED, MemActivityKind.PROMOTION_SUGGESTED,
            MemActivityKind.ADJUDICATED, MemActivityKind.HABIT_INDUCED, MemActivityKind.GOAL_RESOLVED,
        ).forEach {
            assertTrue(it, MemoryActivityInterest.isInteresting(it, ""))
        }
    }

    @Test
    fun extractionWithNewOrUpdatedIsInteresting() {
        assertTrue(MemoryActivityInterest.isInteresting(MemActivityKind.EXTRACTED, "2 new, 3 reinforced"))
        assertTrue(MemoryActivityInterest.isInteresting(MemActivityKind.EXTRACTED, "1 updated"))
        assertTrue(MemoryActivityInterest.isInteresting(MemActivityKind.EXTRACTED, "1 closed"))
        assertTrue(MemoryActivityInterest.isInteresting(MemActivityKind.MANUAL_ADDED, "1 new"))
    }

    @Test
    fun reinforceOnlyExtractionIsNotInteresting() {
        assertFalse(MemoryActivityInterest.isInteresting(MemActivityKind.EXTRACTED, "3 reinforced"))
        assertFalse(MemoryActivityInterest.isInteresting(MemActivityKind.EXTRACTED, "memory updated"))
    }

    @Test
    fun housekeepingKindsNeverInteresting() {
        listOf(
            MemActivityKind.REINFORCED, MemActivityKind.DECAYED, MemActivityKind.DEMOTED_BRANCH,
            MemActivityKind.EXPIRED, MemActivityKind.FORGOTTEN, MemActivityKind.EVICTED,
            MemActivityKind.EMBEDDED, MemActivityKind.WIPED, MemActivityKind.COMPRESSED,
            MemActivityKind.CLOSED,
        ).forEach {
            assertFalse(it, MemoryActivityInterest.isInteresting(it, "5 new")) // even with a rich summary
        }
    }
}
