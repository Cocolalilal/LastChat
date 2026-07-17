package me.rerere.ai.memory

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json

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
        val entities = NativeEntityExtractor.extract(
            "Julian studies Mathematics at TU Wien, uses module.Class.method, and reads \"The Left Hand of Darkness\".",
        )
        assertTrue(entities.any { it.text.contains("TU Wien") })
        assertTrue(entities.any { it.text == "module.Class.method" && it.type == "IDENTIFIER" })
        assertTrue(entities.any { it.text == "The Left Hand of Darkness" })
        assertTrue(entities.none { it.text.equals("User", ignoreCase = true) })
    }

    @Test
    fun extractionParserRecoversFencedAndSurroundedJson() {
        val json = Json { ignoreUnknownKeys = true }
        val fenced = Mem0ExtractionParser.parseOrNull(
            """```json
                {"memory":[{"id":"0","text":"User likes braces such as {this}.","attributed_to":"user"}]}
            ```""".trimIndent(),
            json,
        )
        assertEquals("User likes braces such as {this}.", fenced?.memories?.single()?.text)

        val surrounded = Mem0ExtractionParser.parseOrNull(
            "Result: {\"memory\":[{\"id\":\"0\",\"text\":\"A valid memory\"}]} trailing {noise}",
            json,
        )
        assertEquals("A valid memory", surrounded?.memories?.single()?.text)
        assertNull(Mem0ExtractionParser.parseOrNull("{\"memory\":[", json))
    }

    @Test
    fun ingestPolicyHonorsForgetTombstonesAndWhitelistsLinks() {
        val forgotten = "User strongly prefers tea"
        val selection = Mem0IngestPolicy.selectNewMemories(
            extracted = listOf(
                ExtractedMemory("0", forgotten),
                ExtractedMemory("1", " User adopted a cat ", linkedMemoryIds = listOf("0", "invented", "0")),
                ExtractedMemory("2", "User adopted a cat"),
            ),
            existingHashes = emptySet(),
            suppressedHashes = setOf(Mem0Hash.md5(forgotten)),
            memoryIdByPromptId = mapOf("0" to "real-existing-memory-id"),
        )

        assertEquals(2, selection.skipped)
        assertEquals(listOf("User adopted a cat"), selection.memories.map { it.text })
        assertEquals(listOf("real-existing-memory-id"), selection.memories.single().linkedMemoryIds)
    }

    @Test
    fun pinnedPromptMatchesThePythonSnapshotShape() {
        assertEquals(33_653, Mem0Prompt.ADDITIVE_EXTRACTION_PROMPT.length)
        assertTrue(Mem0Prompt.ADDITIVE_EXTRACTION_PROMPT.contains("Your sole operation is ADD"))
        assertTrue(Mem0Prompt.ADDITIVE_EXTRACTION_PROMPT.contains("Memory Extractor — a precise"))
        assertEquals("ad19187a37813ef77ee156e714c0650e6ec749e0264bdc07d499bc9b24115155", Mem0Prompt.PROMPT_SHA256)
    }
}
