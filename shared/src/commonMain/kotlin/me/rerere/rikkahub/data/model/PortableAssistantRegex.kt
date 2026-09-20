package me.rerere.rikkahub.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class PortableAffectScope {
    @SerialName("user")
    USER,

    @SerialName("assistant")
    ASSISTANT,
}

@Serializable
data class PortableAssistantRegex(
    val id: String,
    val name: String = "",
    val enabled: Boolean = true,
    val findRegex: String = "",
    val replaceString: String = "",
    val affectingScope: Set<PortableAffectScope> = emptySet(),
    val visualOnly: Boolean = false,
)

fun String.replacePortableRegexes(
    regexes: List<PortableAssistantRegex>,
    scope: PortableAffectScope,
    visual: Boolean = false,
): String {
    if (regexes.isEmpty()) return this
    return regexes.fold(this) { acc, rule ->
        if (rule.enabled && rule.visualOnly == visual && rule.affectingScope.contains(scope)) {
            val compiled = compilePortableRegex(rule.findRegex) ?: return@fold acc
            runCatching { acc.replace(compiled, rule.replaceString) }.getOrDefault(acc)
        } else {
            acc
        }
    }
}

private fun compilePortableRegex(pattern: String): Regex? {
    if (pattern.isBlank()) return null
    return runCatching { Regex(pattern) }.getOrNull()
}
