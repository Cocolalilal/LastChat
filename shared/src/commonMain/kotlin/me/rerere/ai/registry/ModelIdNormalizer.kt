package me.rerere.ai.registry

private val COLON_TAG_REGEX = Regex(":(?:\\d+|[a-z0-9._-]+)$")
private val VERSION_SUFFIX_REGEX = Regex("-v\\d+(?::\\d+)?$")
private val DATE_SUFFIX_REGEX = Regex("-(?:20\\d{2}-\\d{2}-\\d{2}|20\\d{6})$")
private val PREVIEW_SUFFIX_REGEX = Regex("-(?:preview|exp)-\\d{2}-\\d{2}$")
private val MULTI_DASH_REGEX = Regex("-{2,}")
private val DATE_YYYYMMDD_REGEX = Regex("20\\d{6}")
private val YEAR_REGEX = Regex("20\\d{2}")
private val TWO_DIGIT_REGEX = Regex("\\d{2}")

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

    val removableSuffixes = setOf(
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
            .lowercase()
            .substringBefore('?')
            .substringBefore('#')
            .replace('_', '-')
            .trim('/')

        working = working.substringAfterLast("/")
        working = working.substringAfterLast("models/")
        working = stripProviderNamespace(working)
        working = working.replace(COLON_TAG_REGEX, "")
        working = working.replace(VERSION_SUFFIX_REGEX, "")
        working = working.replace(DATE_SUFFIX_REGEX, "")
        working = working.replace(PREVIEW_SUFFIX_REGEX, "")
        working = normalizeVersionTokens(working)
        working = stripTrailingNoise(working)
        working = stripTrailingDates(working)
        working = working.replace(MULTI_DASH_REGEX, "-").trim('-')

        return working
    }

    fun preprocess(modelId: String, canonicalHint: String? = null): String {
        val raw = canonicalHint?.ifBlank { null } ?: modelId
        if (raw.isBlank()) return ""

        var working = raw.trim()
            .lowercase()
            .substringBefore('?')
            .substringBefore('#')
            .replace('_', '-')
            .trim('/')

        working = working.substringAfterLast("/")
        working = working.substringAfterLast("models/")
        working = stripProviderNamespace(working)
        working = working.replace(COLON_TAG_REGEX, "")
        working = normalizeVersionTokens(working)
        working = working.replace(MULTI_DASH_REGEX, "-").trim('-')

        return working
    }

    fun extractStrippedTokens(modelId: String, canonicalHint: String? = null): List<String> {
        val preprocessed = preprocess(modelId, canonicalHint)
        val canonical = canonicalize(modelId, canonicalHint)

        val preprocessedTokens = preprocessed.split('-').filter { it.isNotBlank() }
        val canonicalTokens = canonical.split('-').filter { it.isNotBlank() }.toMutableList()

        val stripped = mutableListOf<String>()
        for (token in preprocessedTokens) {
            val index = canonicalTokens.indexOf(token)
            if (index >= 0) {
                canonicalTokens.removeAt(index)
            } else {
                stripped += token
            }
        }
        return (stripped + extractModelTagTokens(modelId)).distinct()
    }

    fun extractModelTagTokens(modelId: String): List<String> {
        val withoutQuery = modelId.trim()
            .substringBefore('?')
            .substringBefore('#')
        val finalPathSegment = withoutQuery.substringAfterLast('/')
        val tag = finalPathSegment.substringAfterLast(':', missingDelimiterValue = "")
            .trim()
            .trim('(', ')')
        if (tag.isBlank()) return emptyList()

        return tag.lowercase()
            .split(Regex("[^a-z0-9.]+"))
            .filter { it.isNotBlank() }
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
            if (last.matches(DATE_YYYYMMDD_REGEX)) {
                tokens.removeAt(tokens.lastIndex)
                continue
            }
            if (
                tokens.size >= 3 &&
                tokens[tokens.lastIndex - 2].matches(YEAR_REGEX) &&
                tokens[tokens.lastIndex - 1].matches(TWO_DIGIT_REGEX) &&
                last.matches(TWO_DIGIT_REGEX)
            ) {
                repeat(3) { tokens.removeAt(tokens.lastIndex) }
                continue
            }
            break
        }
        return tokens.joinToString("-")
    }
}
