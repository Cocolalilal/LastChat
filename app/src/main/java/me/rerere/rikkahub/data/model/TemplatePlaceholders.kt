package me.rerere.rikkahub.data.model

// Android uses ICU's regex parser, which rejects unescaped closing braces that the JVM accepts.
private val templatePlaceholder = Regex(
    """\{\{([a-z_][a-z0-9_]*)\}\}|\{([a-z_][a-z0-9_]*)\}""",
    RegexOption.IGNORE_CASE,
)

/** Expand a configured template once. Replacement values and unknown tags remain literal. */
internal fun String.resolveTemplatePlaceholders(resolve: (String) -> String?): String =
    templatePlaceholder.replace(this) { match ->
        val key = match.groupValues[1].ifEmpty { match.groupValues[2] }.lowercase()
        resolve(key) ?: match.value
    }
