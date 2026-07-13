package me.rerere.ai.memory

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class Mem0CoreTest {
    @Test
    fun md5MatchesPythonHashlib() {
        assertEquals("d41d8cd98f00b204e9800998ecf8427e", Mem0Hash.md5(""))
        assertEquals("5eb63bbbe01eeed093cb22bb8f5acdc3", Mem0Hash.md5("hello world"))
    }

    @Test
    fun hybridScoreUsesAdaptiveDivisorAndSemanticGate() {
        val ranked = Mem0Scoring.scoreAndRank(
            semanticResults = listOf(
                MemorySearchCandidate("a", 0.8f),
                MemorySearchCandidate("b", 0.05f),
            ),
            bm25Scores = mapOf("a" to 0.7f, "b" to 1f),
            entityBoosts = mapOf("a" to 0.25f, "b" to 0.5f),
        )
        assertEquals(listOf("a"), ranked.map { it.id })
        assertTrue(kotlin.math.abs(ranked.single().score - 0.7f) < 0.0001f)
    }

    @Test
    fun deterministicEntitiesPreserveConcreteNamesAndIdentifiers() {
        val entities = NativeEntityExtractor.extract("Julian studies Mathematics at TU Wien and owns a Galaxy S24.")
        assertTrue(entities.any { it.text.contains("TU Wien") })
        assertTrue(entities.any { it.text.contains("Galaxy S24") })
    }

    @Test
    fun pinnedPromptMatchesThePythonSnapshotShape() {
        assertEquals(33_653, Mem0Prompt.ADDITIVE_EXTRACTION_PROMPT.length)
        assertTrue(Mem0Prompt.ADDITIVE_EXTRACTION_PROMPT.contains("Your sole operation is ADD"))
        assertTrue(Mem0Prompt.ADDITIVE_EXTRACTION_PROMPT.contains("Memory Extractor — a precise"))
        assertEquals("ad19187a37813ef77ee156e714c0650e6ec749e0264bdc07d499bc9b24115155", Mem0Prompt.PROMPT_SHA256)
    }
}
