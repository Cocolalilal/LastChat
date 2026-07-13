package me.rerere.ai.memory

/**
 * Deterministic, dependency-free replacement for Mem0's optional spaCy pass.
 * It intentionally favors stable proper-name and noun-phrase extraction over language guessing.
 */
object NativeEntityExtractor {
    private val stopWords = setOf(
        "a", "an", "and", "are", "as", "at", "be", "been", "but", "by", "for", "from",
        "had", "has", "have", "he", "her", "hers", "him", "his", "i", "in", "is", "it",
        "its", "me", "my", "of", "on", "or", "our", "she", "that", "the", "their", "them",
        "they", "this", "to", "was", "we", "were", "with", "you", "your", "user", "assistant",
    )
    private val tokenRegex = Regex("[\\p{L}\\p{N}][\\p{L}\\p{N}'’._+-]*")
    private val properSequence = Regex(
        "(?<![.!?]\\s)(?:[\\p{Lu}][\\p{L}\\p{N}'’._+-]*)(?:\\s+(?:of|the|and|&)?\\s*[\\p{Lu}][\\p{L}\\p{N}'’._+-]*){0,5}",
    )

    fun normalize(text: String): String = text.trim().lowercase().replace(Regex("\\s+"), " ")

    fun lemmatizeForBm25(text: String): String = tokenRegex.findAll(text.lowercase())
        .map { stem(it.value) }
        .filter { it.length > 1 && it !in stopWords }
        .joinToString(" ")

    fun extract(text: String): List<ExtractedEntity> {
        val entities = LinkedHashMap<String, ExtractedEntity>()
        properSequence.findAll(text).forEach { match ->
            val value = match.value.trim().trim(',', '.', ':', ';', '!', '?', '(', ')', '[', ']', '"')
            val normalized = normalize(value)
            if (value.length >= 2 && normalized !in stopWords && value.any(Char::isLetter)) {
                if (normalized !in entities) {
                    entities[normalized] = ExtractedEntity(classify(value), value)
                }
            }
        }
        tokenRegex.findAll(text).forEach { match ->
            val value = match.value
            if ((value.any(Char::isDigit) && value.any(Char::isLetter)) || value.contains('.') || value.contains('-')) {
                val normalized = normalize(value)
                if (normalized !in stopWords && normalized !in entities) {
                    entities[normalized] = ExtractedEntity("IDENTIFIER", value)
                }
            }
        }
        return entities.values.take(32)
    }

    private fun classify(value: String): String = when {
        value.any(Char::isDigit) -> "DATE_OR_IDENTIFIER"
        value.endsWith(" University") || value.endsWith(" Inc") || value.endsWith(" Labs") -> "ORGANIZATION"
        value.split(' ').size in 2..4 -> "PROPER_NAME"
        else -> "ENTITY"
    }

    private fun stem(token: String): String {
        if (token.length <= 3) return token
        return when {
            token.endsWith("ies") && token.length > 4 -> token.dropLast(3) + "y"
            token.endsWith("ing") && token.length > 5 -> token.dropLast(3)
            token.endsWith("ed") && token.length > 4 -> token.dropLast(2)
            token.endsWith("es") && token.length > 4 -> token.dropLast(2)
            token.endsWith("s") && token.length > 3 -> token.dropLast(1)
            else -> token
        }
    }
}
