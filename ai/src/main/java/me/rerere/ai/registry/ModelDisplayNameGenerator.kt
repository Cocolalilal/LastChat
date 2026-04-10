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

    fun generate(modelId: String, canonicalHint: String? = null): String {
        val canonical = ModelIdNormalizer.canonicalize(modelId = modelId, canonicalHint = canonicalHint)
        if (canonical.isBlank()) return modelId.trim()

        val mergedTokens = mergeSpecialTokens(canonical.split('-').filter { it.isNotBlank() })
        val formattedTokens = mergedTokens.map(::formatToken)
        if (formattedTokens.isEmpty()) return modelId.trim()

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

    private fun formatToken(token: String): String {
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

        return token.replaceFirstChar { it.titlecase(Locale.US) }
    }
}
