package me.rerere.ai.registry

object ModelDisplayNameGenerator {
    private val brandCasing = mapOf(
        "gpt" to "GPT",
        "claude" to "Claude",
        "gemini" to "Gemini",
        "llama" to "Llama",
        "deepseek" to "DeepSeek",
        "qwen" to "Qwen",
        "mistral" to "Mistral",
        "mixtral" to "Mixtral",
        "phi" to "Phi",
        "grok" to "Grok",
        "glm" to "GLM",
        "doubao" to "Doubao",
        "kimi" to "Kimi",
        "minimax" to "MiniMax",
        "yi" to "Yi",
        "codestral" to "Codestral",
        "pixtral" to "Pixtral",
        "command" to "Command",
        "nova" to "Nova",
        "jamba" to "Jamba",
        "dall-e" to "DALL·E",
    )

    fun generate(modelId: String, canonicalHint: String? = null): String {
        val canonical = ModelIdNormalizer.canonicalize(modelId = modelId, canonicalHint = canonicalHint)
        if (canonical.isBlank()) return modelId.trim()
        return formatCanonical(canonical)
    }

    fun generateBatch(entries: List<Pair<String, String?>>): List<String> {
        if (entries.isEmpty()) return emptyList()

        val canonicalIds = entries.map { (modelId, hint) ->
            ModelIdNormalizer.canonicalize(modelId, hint)
        }
        val provisionalNames = canonicalIds.mapIndexed { index, canonical ->
            if (canonical.isBlank()) entries[index].first.trim() else formatCanonical(canonical)
        }
        val strippedTokens = entries.map { (modelId, hint) ->
            ModelIdNormalizer.extractStrippedTokens(modelId, hint)
        }

        val collisionGroups = mutableMapOf<String, MutableList<Int>>()
        canonicalIds.forEachIndexed { index, canonical ->
            collisionGroups.getOrPut(canonical) { mutableListOf() }.add(index)
        }

        val results = provisionalNames.toMutableList()

        for ((_, indices) in collisionGroups) {
            if (indices.size <= 1) continue

            val provisionalSet = indices.map { provisionalNames[it] }.toSet()
            if (provisionalSet.size == indices.size) continue

            val groupTokens = indices.map { strippedTokens[it] }

            for ((groupIndex, originalIndex) in indices.withIndex()) {
                val myTokens = groupTokens[groupIndex]
                val otherTokenSets = groupTokens.filterIndexed { index, _ -> index != groupIndex }
                    .map { it.toSet() }

                val distinguishing = myTokens.filter { token ->
                    otherTokenSets.any { otherTokens -> token !in otherTokens }
                }

                if (distinguishing.isNotEmpty()) {
                    val suffix = distinguishing.joinToString(" ") { formatToken(it) }
                    results[originalIndex] = "${provisionalNames[originalIndex]} $suffix"
                } else if (myTokens.isNotEmpty()) {
                    val suffix = myTokens.joinToString(" ") { formatToken(it) }
                    results[originalIndex] = "${provisionalNames[originalIndex]} $suffix"
                }
            }

            val resultSet = indices.map { results[it] }
            if (resultSet.toSet().size < indices.size) {
                for (originalIndex in indices) {
                    val preprocessed = ModelIdNormalizer.preprocess(
                        entries[originalIndex].first,
                        entries[originalIndex].second
                    )
                    results[originalIndex] = formatCanonical(preprocessed)
                }
            }
        }

        return results
    }

    private fun formatCanonical(canonical: String): String {
        val mergedTokens = mergeSpecialTokens(canonical.split('-').filter { it.isNotBlank() })
        val formattedTokens = mergedTokens.map(::formatToken)
        if (formattedTokens.isEmpty()) return canonical

        return if (formattedTokens.firstOrNull() == "GPT" && formattedTokens.size >= 2) {
            buildString {
                append("GPT-")
                append(formattedTokens[1])
                if (formattedTokens.size > 2) {
                    append(' ')
                    append(formattedTokens.drop(2).joinToString(" "))
                }
            }
        } else {
            formattedTokens.joinToString(" ")
        }
    }

    private fun mergeSpecialTokens(tokens: List<String>): List<String> {
        val merged = mutableListOf<String>()
        var index = 0
        while (index < tokens.size) {
            val token = tokens[index]
            if (token == "dall" && tokens.getOrNull(index + 1) == "e") {
                merged += "dall-e"
                index += 2
                continue
            }
            merged += token
            index++
        }
        return merged
    }

    internal fun formatToken(token: String): String {
        brandCasing[token]?.let { return it }

        if (token.matches(Regex("\\d+[bmkt]"))) {
            return token.dropLast(1) + token.takeLast(1).uppercase()
        }

        if (token.matches(Regex("a\\d+[bmkt]?"))) {
            return token.uppercase()
        }

        if (token.matches(Regex("r\\d+"))) {
            return token.uppercase()
        }

        if (token.matches(Regex("[a-z]+\\d+(?:\\.\\d+)?"))) {
            val letters = token.takeWhile { it.isLetter() }
            val numbers = token.dropWhile { it.isLetter() }
            val prefix = brandCasing[letters] ?: letters.replaceFirstChar { it.titlecase() }
            return prefix + numbers
        }

        if (token.matches(Regex("\\d+(?:\\.\\d+)?"))) {
            return token
        }

        if (token.matches(Regex("20\\d{6}"))) {
            return formatDateToken(token)
        }

        return token.replaceFirstChar { it.titlecase() }
    }

    private fun formatDateToken(token: String): String {
        if (token.length == 8) {
            val month = token.substring(4, 6)
            val day = token.substring(6, 8)
            return "$month-$day"
        }
        return token
    }
}

