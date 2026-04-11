package me.rerere.ai.registry

import java.util.Locale

object ModelIdNormalizer {
    private val providerNamespaces = listOf(
        "anthropic.",
        "amazon.",
        "ai21.",
        "meta.",
        "cohere.",
        "mistral.",
        "stability.",
        "openai.",
        "google.",
        "bedrock.",
    )

    private val removableSuffixes = setOf(
        "latest",
        "preview",
        "previewing",
        "beta",
        "free",
        "extended",
        "exp",
        "experimental",
        "fastest",
        "cheapest",
        "preferred",
    )

    private val versionJoinBrands = setOf(
        "claude",
        "gemini",
        "gpt",
        "llama",
        "qwen",
        "phi",
        "deepseek",
        "glm",
        "grok",
        "mistral",
        "mixtral",
        "pixtral",
        "codestral",
        "kimi",
        "doubao",
        "intern",
        "step",
    )

    fun canonicalize(modelId: String, canonicalHint: String? = null): String {
        val raw = canonicalHint?.ifBlank { null } ?: modelId
        if (raw.isBlank()) return ""

        var working = raw.trim()
            .lowercase(Locale.US)
            .substringBefore('?')
            .substringBefore('#')
            .replace('_', '-')
            .trim('/')

        working = working.substringAfterLast("/")
        working = working.substringAfterLast("models/")
        working = stripProviderNamespace(working)
        working = working.replace(Regex(":(?:\\d+|[a-z0-9._-]+)$"), "")
        working = working.replace(Regex("-v\\d+(?::\\d+)?$"), "")
        working = working.replace(Regex("-(?:20\\d{2}-\\d{2}-\\d{2}|20\\d{6})$"), "")
        working = working.replace(Regex("-(?:preview|exp)-\\d{2}-\\d{2}$"), "")
        working = normalizeVersionTokens(working)
        working = stripTrailingNoise(working)
        working = stripTrailingDates(working)
        working = working.replace(Regex("-{2,}"), "-").trim('-')

        return working
    }

    /**
     * Prepares a model ID in the same way canonicalize does (lowercasing, stripping
     * provider namespace, etc.) but WITHOUT removing noise suffixes, dates, or versions.
     * This preserves all differentiating tokens for comparison.
     */
    fun preprocess(modelId: String, canonicalHint: String? = null): String {
        val raw = canonicalHint?.ifBlank { null } ?: modelId
        if (raw.isBlank()) return ""

        var working = raw.trim()
            .lowercase(Locale.US)
            .substringBefore('?')
            .substringBefore('#')
            .replace('_', '-')
            .trim('/')

        working = working.substringAfterLast("/")
        working = working.substringAfterLast("models/")
        working = stripProviderNamespace(working)
        working = working.replace(Regex(":(?:\\d+|[a-z0-9._-]+)$"), "")
        working = normalizeVersionTokens(working)
        working = working.replace(Regex("-{2,}"), "-").trim('-')

        return working
    }

    /**
     * Returns the set of tokens present in the preprocessed model ID but absent
     * from the canonical ID. These are the tokens that were stripped during
     * canonicalization and are candidates for restoring during disambiguation.
     */
    fun extractStrippedTokens(modelId: String, canonicalHint: String? = null): List<String> {
        val preprocessed = preprocess(modelId, canonicalHint)
        val canonical = canonicalize(modelId, canonicalHint)

        val preprocessedTokens = preprocessed.split('-').filter { it.isNotBlank() }
        val canonicalTokens = canonical.split('-').filter { it.isNotBlank() }.toMutableList()

        val stripped = mutableListOf<String>()
        for (token in preprocessedTokens) {
            val idx = canonicalTokens.indexOf(token)
            if (idx >= 0) {
                canonicalTokens.removeAt(idx)
            } else {
                stripped += token
            }
        }
        return stripped
    }

    private fun stripProviderNamespace(value: String): String {
        providerNamespaces.firstOrNull { value.startsWith(it) }?.let { prefix ->
            return value.removePrefix(prefix)
        }
        return value
    }

    private fun normalizeVersionTokens(value: String): String {
        val tokens = value.split('-')
            .filter { it.isNotBlank() }
            .toMutableList()
        if (tokens.isEmpty()) return value

        val normalized = mutableListOf<String>()
        var index = 0
        while (index < tokens.size) {
            val token = tokens[index]
            val previous = normalized.lastOrNull()

            val vMatch = Regex("^v(\\d+)p(\\d+)$").matchEntire(token)
            if (vMatch != null) {
                normalized += "${vMatch.groupValues[1]}.${vMatch.groupValues[2]}"
                index++
                continue
            }

            if (
                previous in versionJoinBrands &&
                index + 1 < tokens.size &&
                token.all(Char::isDigit) &&
                tokens[index + 1].all(Char::isDigit)
            ) {
                normalized += "$token.${tokens[index + 1]}"
                index += 2
                continue
            }

            normalized += token
            index++
        }

        return normalized.joinToString("-")
    }

    private fun stripTrailingNoise(value: String): String {
        var working = value
        while (true) {
            val token = working.substringAfterLast('-', "")
            if (token.isBlank() || token !in removableSuffixes) {
                return working
            }
            working = working.substringBeforeLast('-', "")
        }
    }

    private fun stripTrailingDates(value: String): String {
        val tokens = value.split('-').filter { it.isNotBlank() }.toMutableList()
        while (tokens.isNotEmpty()) {
            val last = tokens.last()
            if (last.matches(Regex("20\\d{6}"))) {
                tokens.removeLast()
                continue
            }
            if (
                tokens.size >= 3 &&
                tokens[tokens.lastIndex - 2].matches(Regex("20\\d{2}")) &&
                tokens[tokens.lastIndex - 1].matches(Regex("\\d{2}")) &&
                last.matches(Regex("\\d{2}"))
            ) {
                repeat(3) { tokens.removeLast() }
                continue
            }
            break
        }
        return tokens.joinToString("-")
    }
}
