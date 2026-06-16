package me.rerere.common.http

import kotlin.math.roundToInt

/**
 * Builds an Accept-Language header from already-normalized BCP-47 language tags.
 */
class AcceptLanguageBuilder private constructor(
    private val languageTagsInPreference: List<String>,
    private val options: Options
) {
    data class Options(
        val maxLanguages: Int = 6,
        val qStep: Double = 0.1,
        val minQ: Double = 0.1,
        val includeGenericLanguage: Boolean = true,
        val deduplicate: Boolean = true
    )

    companion object {
        fun withLanguageTags(
            languageTags: List<String>,
            options: Options = Options()
        ): AcceptLanguageBuilder {
            return AcceptLanguageBuilder(languageTags, options)
        }
    }

    fun build(): String {
        val tags = mutableListOf<String>()
        for (tag in languageTagsInPreference) {
            val normalized = tag.trim()
            if (normalized.isBlank()) continue
            tags += normalized

            if (options.includeGenericLanguage) {
                val generic = genericLanguageOf(normalized)
                if (generic != null) tags += generic
            }
        }

        val distinct = if (options.deduplicate) tags.distinct() else tags
        val limited = distinct.take(options.maxLanguages.coerceAtLeast(1))

        return limited.mapIndexed { index, tag ->
            if (index == 0) {
                tag
            } else {
                val q = (1.0 - index * options.qStep).coerceAtLeast(options.minQ)
                "$tag;q=${formatQ(q)}"
            }
        }.joinToString(separator = ", ")
    }

    private fun genericLanguageOf(tag: String): String? {
        val idx = tag.indexOf('-')
        if (idx <= 0) return null
        val head = tag.substring(0, idx)
        return head.takeIf { it.isNotBlank() }
    }

    private fun formatQ(value: Double): String {
        val clamped = (value * 1000.0).roundToInt()
        val whole = clamped / 1000
        val fractional = (clamped % 1000).toString().padStart(3, '0').trimEnd('0')
        return if (fractional.isBlank()) whole.toString() else "$whole.$fractional"
    }
}
