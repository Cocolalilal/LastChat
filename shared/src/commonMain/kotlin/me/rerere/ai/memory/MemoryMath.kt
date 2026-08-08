package me.rerere.ai.memory

import kotlin.math.sqrt

object MemoryVectorMath {
    private val lexicalStopWords = setOf(
        "a", "an", "and", "are", "as", "at", "be", "but", "by", "for", "from", "had",
        "has", "have", "he", "her", "his", "i", "in", "is", "it", "me", "my", "of", "on",
        "or", "our", "she", "that", "the", "their", "them", "they", "this", "to", "was",
        "we", "were", "what", "when", "where", "who", "with", "you", "your",
    )

    fun cosineSimilarity(first: List<Float>, second: List<Float>): Float {
        if (first.size != second.size || first.isEmpty()) return 0f
        var dot = 0.0
        var firstNorm = 0.0
        var secondNorm = 0.0
        for (index in first.indices) {
            val a = first[index]
            val b = second[index]
            dot += a * b
            firstNorm += a * a
            secondNorm += b * b
        }
        return if (firstNorm == 0.0 || secondNorm == 0.0) 0f
        else (dot / (sqrt(firstNorm) * sqrt(secondNorm))).toFloat()
    }

    fun keywordScore(query: String, content: String): Float {
        val terms = query.lowercase()
            .split(Regex("\\W+"))
            .filter { it.isNotBlank() && it !in lexicalStopWords }
            .distinct()
        if (terms.isEmpty()) return 0f
        val normalizedContent = content.lowercase()
        return terms.count(normalizedContent::contains).toFloat() / terms.size
    }
}

object PortableMemoryChunker {
    const val MAX_CHUNK_SIZE = 500
    const val MIN_CHUNK_SIZE = 100

    fun chunkText(text: String): List<String> {
        if (text.length <= MAX_CHUNK_SIZE) return listOf(text)
        val sentences = text.split(Regex("(?<=[.!?\n])\\s+"))
        val chunks = mutableListOf<String>()
        var current = StringBuilder()
        for (sentence in sentences) {
            if (current.isEmpty()) current.append(sentence)
            else if (current.length + sentence.length + 1 <= MAX_CHUNK_SIZE) current.append(' ').append(sentence)
            else {
                chunks += current.toString()
                current = StringBuilder(sentence)
            }
        }
        if (current.isNotEmpty()) {
            val tail = current.toString()
            if (tail.length < MIN_CHUNK_SIZE && chunks.isNotEmpty()) {
                chunks[chunks.lastIndex] = chunks.last() + " " + tail
            } else chunks += tail
        }
        return chunks
    }
}
