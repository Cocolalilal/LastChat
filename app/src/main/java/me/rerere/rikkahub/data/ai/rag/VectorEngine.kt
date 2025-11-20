package me.rerere.rikkahub.data.ai.rag

import kotlin.math.sqrt

object VectorEngine {
    fun cosineSimilarity(v1: List<Float>, v2: List<Float>): Float {
        if (v1.size != v2.size) return 0f
        var dotProduct = 0.0
        var normA = 0.0
        var normB = 0.0
        for (i in v1.indices) {
            dotProduct += v1[i] * v2[i]
            normA += v1[i] * v1[i]
            normB += v2[i] * v2[i]
        }
        return if (normA == 0.0 || normB == 0.0) 0f else (dotProduct / (sqrt(normA) * sqrt(normB))).toFloat()
    }
}
