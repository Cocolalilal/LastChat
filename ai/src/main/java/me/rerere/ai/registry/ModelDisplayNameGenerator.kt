package me.rerere.ai.registry

import java.util.Locale

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

    /**
     * Generate a display name for a single model ID.
     * This is the original per-model approach — no sibling awareness.
     */
    fun generate(modelId: String, canonicalHint: String? = null): String {
        val canonical = ModelIdNormalizer.canonicalize(modelId = modelId, canonicalHint = canonicalHint)
        if (canonical.isBlank()) return modelId.trim()
        return formatCanonical(canonical)
    }

    /**
     * Generate display names for a batch of model IDs that live on the same provider.
     * This enables context-aware disambiguation: if two models canonicalize to the
     * same base name, their differentiating tokens (preview, beta, parameter size, etc.)
     * are selectively restored to avoid collisions.
     *
     * @param entries List of pairs: (modelId, canonicalHint?)
     * @return List of display names in the same order as the input
     */
    fun generateBatch(entries: List<Pair<String, String?>>): List<String> {
        if (entries.isEmpty()) return emptyList()

        // Phase 1: Compute canonical IDs, provisional display names, and stripped tokens
        val canonicalIds = entries.map { (modelId, hint) ->
            ModelIdNormalizer.canonicalize(modelId, hint)
        }
        val provisionalNames = canonicalIds.mapIndexed { i, canonical ->
            if (canonical.isBlank()) entries[i].first.trim() else formatCanonical(canonical)
        }
        val strippedTokens = entries.map { (modelId, hint) ->
            ModelIdNormalizer.extractStrippedTokens(modelId, hint)
        }

        // Phase 2: Group by canonical ID to find collisions
        // Map from canonical ID -> list of indices that share it
        val collisionGroups = mutableMapOf<String, MutableList<Int>>()
        canonicalIds.forEachIndexed { index, canonical ->
            collisionGroups.getOrPut(canonical) { mutableListOf() }.add(index)
        }

        // Phase 3: Disambiguate collisions
        val results = provisionalNames.toMutableList()

        for ((_, indices) in collisionGroups) {
            if (indices.size <= 1) continue // No collision

            // Check if provisional names already differ (e.g. param sizes weren't stripped)
            val provisionalSet = indices.map { provisionalNames[it] }.toSet()
            if (provisionalSet.size == indices.size) continue // Already unique

            // Find which stripped tokens disambiguate each model
            val groupTokens = indices.map { strippedTokens[it] }

            for ((groupIdx, originalIdx) in indices.withIndex()) {
                val myTokens = groupTokens[groupIdx]
                val otherTokenSets = groupTokens.filterIndexed { i, _ -> i != groupIdx }
                    .map { it.toSet() }

                // Find tokens this model has that at least one sibling doesn't
                val distinguishing = myTokens.filter { token ->
                    otherTokenSets.any { otherTokens -> token !in otherTokens }
                }

                if (distinguishing.isNotEmpty()) {
                    val suffix = distinguishing.joinToString(" ") { formatToken(it) }
                    results[originalIdx] = "${provisionalNames[originalIdx]} $suffix"
                } else if (myTokens.isNotEmpty()) {
                    // All have the same stripped tokens — show them all (rare edge case)
                    val suffix = myTokens.joinToString(" ") { formatToken(it) }
                    results[originalIdx] = "${provisionalNames[originalIdx]} $suffix"
                }
                // else: no stripped tokens at all, names stay the same (truly identical models)
            }

            // Final dedup check: if names still collide after token restoration, append
            // the differing portion of the preprocessed model ID as a last resort
            val resultSet = indices.map { results[it] }
            if (resultSet.toSet().size < indices.size) {
                for (originalIdx in indices) {
                    val preprocessed = ModelIdNormalizer.preprocess(
                        entries[originalIdx].first,
                        entries[originalIdx].second
                    )
                    results[originalIdx] = formatCanonical(preprocessed)
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
            return token.dropLast(1) + token.takeLast(1).uppercase(Locale.US)
        }

        if (token.matches(Regex("a\\d+[bmkt]?"))) {
            return token.uppercase(Locale.US)
        }

        if (token.matches(Regex("r\\d+"))) {
            return token.uppercase(Locale.US)
        }

        if (token.matches(Regex("[a-z]+\\d+(?:\\.\\d+)?"))) {
            val letters = token.takeWhile { it.isLetter() }
            val numbers = token.dropWhile { it.isLetter() }
            val prefix = brandCasing[letters] ?: letters.replaceFirstChar { it.titlecase(Locale.US) }
            return prefix + numbers
        }

        if (token.matches(Regex("\\d+(?:\\.\\d+)?"))) {
            return token
        }

        // Date tokens — format nicely
        if (token.matches(Regex("20\\d{6}"))) {
            return formatDateToken(token)
        }

        return token.replaceFirstChar { it.titlecase(Locale.US) }
    }

    private fun formatDateToken(token: String): String {
        // 20250417 -> "04-17"
        if (token.length == 8) {
            val month = token.substring(4, 6)
            val day = token.substring(6, 8)
            return "$month-$day"
        }
        return token
    }
}
