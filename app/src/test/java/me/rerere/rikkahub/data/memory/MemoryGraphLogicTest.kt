package me.rerere.rikkahub.data.memory

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-logic tests for the memory store's deterministic core — the parts that must be correct even
 * when the model budget is zero: text normalization, the dedup gate, and budget presets.
 */
class MemoryGraphLogicTest {

    // ---------------- MemoryText ----------------

    @Test
    fun normalizeLowercasesAndStripsPunctuation() {
        assertEquals("user studies cs at tu wien", MemoryText.normalize("  User studies CS, at TU Wien! "))
        assertEquals("", MemoryText.normalize("!!! ??? ..."))
    }

    @Test
    fun contentTokensDropsStopwordsAndShortTokens() {
        assertEquals(
            setOf("user", "studies", "cs", "tu", "wien"),
            MemoryText.contentTokens("User is at the TU Wien, studies CS"),
        )
    }

    @Test
    fun isRestatementDetectsExactAndSubset() {
        assertTrue(MemoryText.isRestatement("studies CS at TU Wien", "User studies CS at TU Wien"))
        assertTrue(MemoryText.isRestatement("User studies CS at TU Wien", "user studies cs at tu wien"))
        // Adding information is NOT a restatement.
        assertFalse(MemoryText.isRestatement("User studies math at TU Wien", "User studies CS at TU Wien"))
        // A superset (candidate adds tokens) is not a restatement of the shorter existing node.
        assertFalse(MemoryText.isRestatement("User studies CS at TU Wien", "studies CS"))
    }

    @Test
    fun overlapIsCoefficientOverSmallerSet() {
        assertEquals(1.0f, MemoryText.overlap("studies CS", "User studies CS at TU Wien"), 0.001f)
        assertEquals(0.0f, MemoryText.overlap("likes coffee", "drives a car"), 0.001f)
    }

    @Test
    fun ftsOrQueryEscapesAndFiltersTerms() {
        assertEquals("\"tu wien\" OR \"vienna\"", MemoryText.ftsOrQuery(listOf("tu wien", "vienna", "a")))
        assertEquals("\"say \"\"hi\"\"\"", MemoryText.ftsOrQuery(listOf("say \"hi\"")))
        assertNull(MemoryText.ftsOrQuery(listOf("a", "!", "")))
    }

    // ---------------- MemoryDedupGate ----------------

    private fun neighbor(id: String, content: String) = MemoryDedupGate.NeighborView(id, content)

    @Test
    fun emptyNeighborhoodInserts() {
        assertEquals(MemoryDedupGate.Decision.Insert, MemoryDedupGate.decide("User has a cat", emptyList()))
    }

    @Test
    fun restatementReinforces() {
        val neighbors = listOf(neighbor("n1", "User studies CS at TU Wien"))
        val decision = MemoryDedupGate.decide("studies CS at TU Wien", neighbors)
        assertEquals(MemoryDedupGate.Decision.Reinforce("n1"), decision)
    }

    @Test
    fun similarButDifferentIsFlaggedNotReinforced() {
        // The classic failure mode: "math" arriving over "CS" must never silently reinforce.
        val neighbors = listOf(neighbor("n1", "User studies CS at TU Wien"))
        val decision = MemoryDedupGate.decide("User studies math at TU Wien", neighbors)
        assertTrue(decision is MemoryDedupGate.Decision.Flag)
        assertEquals("n1", (decision as MemoryDedupGate.Decision.Flag).relatedId)
    }

    @Test
    fun novelStatementInserts() {
        val neighbors = listOf(neighbor("n1", "User studies CS at TU Wien"))
        assertEquals(MemoryDedupGate.Decision.Insert, MemoryDedupGate.decide("User has a dog named Rex", neighbors))
    }

    @Test
    fun exactMatchIsPreferredOverOverlapFlag() {
        val neighbors = listOf(
            neighbor("similar", "User studies math at TU Wien"),
            neighbor("exact", "User studies CS at TU Wien"),
        )
        assertEquals(
            MemoryDedupGate.Decision.Reinforce("exact"),
            MemoryDedupGate.decide("User studies CS at TU Wien", neighbors),
        )
    }

    // ---------------- MemoryBudgetCaps ----------------

    @Test
    fun retiredOffPresetNameFallsBackToBalanced() {
        // "OFF" is no longer a preset (off is Assistant.enableMemory); persisted "OFF" → Balanced.
        assertEquals(MemoryPreset.BALANCED, MemoryPreset.fromNameOrDefault("OFF"))
    }

    @Test
    fun allPresetsEnableExtraction() {
        MemoryPreset.entries.forEach { preset ->
            assertTrue(preset.name, MemoryBudgetCaps.of(preset).extractionEnabled)
        }
    }

    @Test
    fun balancedIsTheDefaultCadence() {
        assertEquals(MemoryPreset.BALANCED, MemoryPreset.fromNameOrDefault(null))
        assertEquals(MemoryPreset.BALANCED, MemoryPreset.fromNameOrDefault("nonsense"))
        assertEquals(10, MemoryBudgetCaps.of(MemoryPreset.BALANCED).extractionCadenceMessages)
        assertTrue(MemoryBudgetCaps.of(MemoryPreset.BALANCED).extractionEnabled)
    }
}
