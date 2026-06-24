package me.rerere.rikkahub.data.ai.rag

object MemoryChunker {
    const val MAX_CHUNK_SIZE = 500
    const val MIN_CHUNK_SIZE = 100 // if a chunk is smaller than this, merge it with previous

    fun chunkText(text: String): List<String> {
        if (text.length <= MAX_CHUNK_SIZE) {
            return listOf(text)
        }

        // Try to split by sentence boundaries
        val sentences = text.split(Regex("(?<=[.!?\n])\\s+"))
        val chunks = mutableListOf<String>()
        var currentChunk = StringBuilder()

        for (sentence in sentences) {
            if (currentChunk.isEmpty()) {
                currentChunk.append(sentence)
            } else if (currentChunk.length + sentence.length + 1 <= MAX_CHUNK_SIZE) {
                currentChunk.append(" ").append(sentence)
            } else {
                chunks.add(currentChunk.toString())
                currentChunk = StringBuilder(sentence)
            }
        }

        if (currentChunk.isNotEmpty()) {
            val finalChunk = currentChunk.toString()
            if (finalChunk.length < MIN_CHUNK_SIZE && chunks.isNotEmpty()) {
                // Merge with previous
                val lastIdx = chunks.lastIndex
                chunks[lastIdx] = chunks[lastIdx] + " " + finalChunk
            } else {
                chunks.add(finalChunk)
            }
        }

        // Failsafe: if a single sentence is still larger than MAX_CHUNK_SIZE, we just return it as is
        // or we could split by words. For now, returning as is since embeddings can handle up to 8k tokens.
        return chunks
    }
}
