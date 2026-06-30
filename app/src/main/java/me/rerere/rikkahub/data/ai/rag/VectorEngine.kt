package me.rerere.rikkahub.data.ai.rag

import kotlin.math.sqrt

object VectorEngine {
    fun cosineSimilarity(v1: FloatArray, v2: FloatArray): Float {
        if (v1.size != v2.size) return 0f
        var dotProduct = 0.0
        var normA = 0.0
        var normB = 0.0
        for (i in v1.indices) {
            val a = v1[i]
            val b = v2[i]
            dotProduct += a * b
            normA += a * a
            normB += b * b
        }
        return if (normA == 0.0 || normB == 0.0) 0f else (dotProduct / (sqrt(normA) * sqrt(normB))).toFloat()
    }
}
