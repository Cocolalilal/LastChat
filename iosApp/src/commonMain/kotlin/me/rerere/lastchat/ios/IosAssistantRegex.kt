package me.rerere.lastchat.ios

import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.model.PortableAffectScope
import me.rerere.rikkahub.data.model.PortableAssistantRegex
import me.rerere.rikkahub.data.model.replacePortableRegexes

internal fun String.applyIosRegexes(
    regexes: List<PortableAssistantRegex>,
    outgoing: Boolean,
    visual: Boolean,
): String {
    val scope = if (outgoing) PortableAffectScope.USER else PortableAffectScope.ASSISTANT
    return replacePortableRegexes(regexes, scope, visual)
}

internal fun List<UIMessage>.applyIosRegexes(
    regexes: List<PortableAssistantRegex>,
    visual: Boolean,
): List<UIMessage> {
    if (regexes.isEmpty()) return this
    return map { message ->
        // Match Android RegexOutputTransformer: generation/list transforms only rewrite assistant parts.
        // USER generation regexes are applied at send/edit time, not here.
        val scope = when (message.role) {
            MessageRole.ASSISTANT -> PortableAffectScope.ASSISTANT
            else -> return@map message
        }
        message.copy(
            parts = message.parts.map { part ->
                when (part) {
                    is UIMessagePart.Text -> part.copy(
                        text = part.text.replacePortableRegexes(regexes, scope, visual),
                    )
                    is UIMessagePart.Reasoning -> part.copy(
                        reasoning = part.reasoning.replacePortableRegexes(regexes, scope, visual),
                    )
                    else -> part
                }
            },
        )
    }
}
