package me.rerere.ai.generation

/**
 * Pebble cannot run on Kotlin/Native. This subset covers the assistant
 * `messageTemplate` variables Android already documents (`message`, `role`,
 * `time`, `date`) plus simple `{% if %}` wrappers. Android still injects a
 * Pebble-backed [PortableTemplateRuntime] for full template syntax.
 */
fun renderSimpleMessageTemplate(
    template: String,
    context: Map<String, String>,
): String {
    val values = context
    if (template.isBlank() || template.trim() == "{{ message }}") {
        return values["message"].orEmpty()
    }
    val withConditionals = expandIfBlocks(template, values)
    return TOKEN_REGEX.replace(withConditionals) { match ->
        val key = match.groupValues[1].trim()
        values[key].orEmpty()
    }
}

fun interface PortableTemplateRuntime {
    fun render(template: String, context: Map<String, String>): String
}

private val TOKEN_REGEX = Regex("""\{\{\s*([a-zA-Z_][a-zA-Z0-9_]*)(?:\s*\|[^}]+)?\s*\}\}""")
private val IF_BLOCK_REGEX = Regex(
    """\{%\s*if\s+([a-zA-Z_][a-zA-Z0-9_]*)(?:\s*==\s*['"]([^'"]*)['"])?\s*%\}(.*?)(?:\{%\s*else\s*%\}(.*?))?\{%\s*endif\s*%\}""",
    setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE),
)

private fun expandIfBlocks(template: String, values: Map<String, String>): String {
    var current = template
    var guard = 0
    while (guard++ < 32) {
        val match = IF_BLOCK_REGEX.find(current) ?: break
        val key = match.groupValues[1]
        val expected = match.groupValues[2]
        val truthy = match.groupValues[3]
        val falsy = match.groupValues[4]
        val actual = values[key].orEmpty()
        val pass = if (expected.isEmpty()) actual.isNotBlank() else actual == expected
        current = current.replaceRange(match.range, if (pass) truthy else falsy)
    }
    return current
}
