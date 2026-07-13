package me.rerere.ai.memory

import kotlin.math.exp

/** Python Mem0 OSS 2.0.11 hybrid-scoring behavior. */
object Mem0Scoring {
    const val DEFAULT_THRESHOLD = 0.1f
    const val DEFAULT_TOP_K = 20
    const val ENTITY_BOOST_WEIGHT = 0.5f

    fun internalLimit(topK: Int): Int = maxOf(topK * 4, 60)

    fun bm25Parameters(lemmatizedQuery: String): Pair<Float, Float> {
        val terms = lemmatizedQuery.split(Regex("\\s+")).count { it.isNotBlank() }.coerceAtLeast(1)
        return when {
            terms <= 3 -> 5f to 0.7f
            terms <= 6 -> 7f to 0.6f
            terms <= 9 -> 9f to 0.5f
            terms <= 15 -> 10f to 0.5f
            else -> 12f to 0.5f
        }
    }

    fun normalizeBm25(rawScore: Float, midpoint: Float, steepness: Float): Float =
        (1.0 / (1.0 + exp((-steepness * (rawScore - midpoint)).toDouble()))).toFloat()

    fun entityBoost(similarity: Float, linkedMemoryCount: Int): Float {
        if (similarity < 0.5f) return 0f
        val n = linkedMemoryCount.coerceAtLeast(1) - 1
        val hubWeight = 1f / (1f + 0.001f * n * n)
        return similarity * ENTITY_BOOST_WEIGHT * hubWeight
    }

    fun scoreAndRank(
        semanticResults: List<MemorySearchCandidate>,
        bm25Scores: Map<String, Float>,
        entityBoosts: Map<String, Float>,
        threshold: Float = DEFAULT_THRESHOLD,
        topK: Int = DEFAULT_TOP_K,
    ): List<RankedMemory> {
        require(threshold in 0f..1f) { "threshold must be between 0 and 1" }
        require(topK > 0) { "topK must be greater than zero" }
        val maxPossible = 1f + (if (bm25Scores.isNotEmpty()) 1f else 0f) +
            (if (entityBoosts.isNotEmpty()) ENTITY_BOOST_WEIGHT else 0f)
        return semanticResults.asSequence()
            .filter { it.semanticScore >= threshold }
            .map { candidate ->
                val bm25 = bm25Scores[candidate.id] ?: 0f
                val entity = entityBoosts[candidate.id] ?: 0f
                val raw = candidate.semanticScore + bm25 + entity
                val final = (raw / maxPossible).coerceAtMost(1f)
                RankedMemory(
                    id = candidate.id,
                    score = final,
                    details = MemoryScoreDetails(
                        semanticScore = candidate.semanticScore,
                        bm25Score = bm25,
                        entityBoost = entity,
                        rawScore = raw,
                        maxPossibleScore = maxPossible,
                        finalScore = final,
                        threshold = threshold,
                    ),
                )
            }
            .sortedByDescending { it.score }
            .take(topK)
            .toList()
    }
}
