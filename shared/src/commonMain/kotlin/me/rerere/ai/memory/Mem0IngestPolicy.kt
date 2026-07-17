package me.rerere.ai.memory

data class Mem0IngestSelection(
    val memories: List<ExtractedMemory>,
    val skipped: Int,
)

/** Pure ingest rules shared by automatic extraction and tests. */
object Mem0IngestPolicy {
    fun selectNewMemories(
        extracted: List<ExtractedMemory>,
        existingHashes: Set<String>,
        suppressedHashes: Set<String>,
        memoryIdByPromptId: Map<String, String>,
    ): Mem0IngestSelection {
        val distinct = LinkedHashMap<String, ExtractedMemory>()
        var skipped = 0
        extracted.forEach { candidate ->
            val cleaned = candidate.text.trim()
            val hash = Mem0Hash.md5(cleaned)
            if (
                cleaned.isBlank() || hash in existingHashes || hash in suppressedHashes ||
                distinct.containsKey(hash)
            ) {
                skipped++
            } else {
                distinct[hash] = candidate.copy(
                    text = cleaned,
                    linkedMemoryIds = candidate.linkedMemoryIds
                        .mapNotNull(memoryIdByPromptId::get)
                        .distinct(),
                )
            }
        }
        return Mem0IngestSelection(distinct.values.toList(), skipped)
    }
}
