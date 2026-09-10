package me.rerere.ai.memory

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MemoryMathTest {
    @Test
    fun cosineSimilarityHandlesMatchingOrthogonalAndMismatchedVectors() {
        assertEquals(1f, MemoryVectorMath.cosineSimilarity(listOf(1f, 0f), listOf(1f, 0f)))
        assertEquals(0f, MemoryVectorMath.cosineSimilarity(listOf(1f, 0f), listOf(0f, 1f)))
        assertEquals(0f, MemoryVectorMath.cosineSimilarity(listOf(1f), listOf(1f, 0f)))

        // Test FloatArray overloads and bound clamping
        val v1 = floatArrayOf(1f, 0f)
        val v2 = floatArrayOf(1f, 0f)
        val v3 = floatArrayOf(0f, 1f)
        assertEquals(1f, MemoryVectorMath.cosineSimilarity(v1, v2))
        assertEquals(0f, MemoryVectorMath.cosineSimilarity(v1, v3))
        assertEquals(0f, MemoryVectorMath.cosineSimilarity(floatArrayOf(1f), v1))
        assertTrue(MemoryVectorMath.cosineSimilarity(v1, v2) <= 1.0f)
        assertTrue(MemoryVectorMath.cosineSimilarity(v1, v2) >= -1.0f)
    }

    @Test
    fun keywordScoreUsesTheSameTermCoverageSignalAsAndroidRecall() {
        assertEquals(2f / 3f, MemoryVectorMath.keywordScore("blue bicycle garden", "The blue bicycle is ready"))
        assertEquals(1f, MemoryVectorMath.keywordScore("what was the blue bicycle", "The bicycle was blue"))
        assertEquals(0f, MemoryVectorMath.keywordScore("", "anything"))
    }

    @Test
    fun keywordScoreHandlesNonAsciiAndAccentedTerms() {
        val scoreCjk = MemoryVectorMath.keywordScore("喜欢", "用户喜欢喝茶")
        assertEquals(1f, scoreCjk)
        val scoreAccented = MemoryVectorMath.keywordScore("München", "Julian lebt in München")
        assertEquals(1f, scoreAccented)
    }

    @Test
    fun lexicalEvidenceSurvivesAWeakButAvailableVector() {
        assertTrue(MemoryVectorMath.passesRecallThreshold(score = 0.18f, keywordScore = 1f, threshold = 0.45f))
        assertTrue(!MemoryVectorMath.passesRecallThreshold(score = 0.18f, keywordScore = 0.2f, threshold = 0.45f))
        assertTrue(!MemoryVectorMath.passesRecallThreshold(score = 0.18f, keywordScore = 0f, threshold = 0.45f))
    }

    @Test
    fun chunkerKeepsSmallTailAttachedToPreviousChunk() {
        val first = "A".repeat(450) + "."
        val second = "B".repeat(450) + "."
        val tail = "C".repeat(40)
        val chunks = PortableMemoryChunker.chunkText("$first $second $tail")

        assertEquals(2, chunks.size)
        assertTrue(chunks.last().endsWith(tail))
    }
}
