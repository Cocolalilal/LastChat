package me.rerere.rikkahub.data.ai.rag

import me.rerere.ai.memory.MemoryVectorMath

object VectorEngine {
    fun cosineSimilarity(v1: FloatArray, v2: FloatArray): Float {
        return MemoryVectorMath.cosineSimilarity(v1.asList(), v2.asList())
    }
}
