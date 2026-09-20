package me.rerere.lastchat.ios

internal fun iosGreeting(hour: Int, name: String): String {
    val part = when (hour) {
        in 5..11 -> "Good morning"
        in 12..17 -> "Good afternoon"
        else -> "Good evening"
    }
    val trimmed = name.trim()
    return if (trimmed.isBlank()) "$part." else "$part, $trimmed"
}

internal sealed class IosChatTextSegment {
    data class Text(val value: String) : IosChatTextSegment()
    data class Code(val language: String, val value: String) : IosChatTextSegment()
}

internal fun splitIosChatText(text: String): List<IosChatTextSegment> {
    if (!text.contains("```")) return listOf(IosChatTextSegment.Text(text))
    val segments = mutableListOf<IosChatTextSegment>()
    val regex = Regex("```([A-Za-z0-9_+-]*)\\n([\\s\\S]*?)```")
    var cursor = 0
    regex.findAll(text).forEach { match ->
        if (match.range.first > cursor) {
            segments += IosChatTextSegment.Text(text.substring(cursor, match.range.first))
        }
        segments += IosChatTextSegment.Code(
            language = match.groupValues[1],
            value = match.groupValues[2].trimEnd(),
        )
        cursor = match.range.last + 1
    }
    if (cursor < text.length) {
        segments += IosChatTextSegment.Text(text.substring(cursor))
    }
    return segments.ifEmpty { listOf(IosChatTextSegment.Text(text)) }
}

internal data class IosContextStackSummary(
    val lore: Int,
    val modes: Int,
    val memories: Int,
) {
    val total: Int get() = lore + modes + memories
    val label: String
        get() = buildList {
            if (lore > 0) add("$lore lore")
            if (modes > 0) add("$modes skills")
            if (memories > 0) add("$memories mem")
        }.joinToString(" · ").ifBlank { "Context" }
}

internal fun iosTokenUsageLabel(promptTokens: Int, completionTokens: Int, totalTokens: Int): String {
    val total = if (totalTokens > 0) totalTokens else promptTokens + completionTokens
    return "$promptTokens → $completionTokens  ($total)"
}

internal fun iosReasoningPreview(text: String, maxChars: Int = 140): String {
    val collapsed = text.trim().replace(Regex("\\s+"), " ")
    if (collapsed.length <= maxChars) return collapsed
    return collapsed.take(maxChars).trimEnd() + "…"
}

internal const val IOS_NEW_CHAT_WRITE_PROMPT = "Help me write "
internal const val IOS_NEW_CHAT_CODE_PROMPT = "Help me code "
internal const val IOS_NEW_CHAT_BRAINSTORM_PROMPT = "Let's brainstorm about "
internal const val IOS_NEW_CHAT_LEARN_PROMPT = "Explain "

internal val IOS_AVATAR_EMOJI_PRESETS = listOf(
    "🙂", "😀", "😎", "🤓", "🥳", "🤖", "🐱", "🐶", "🦊", "🐼",
    "🌸", "🌟", "🔥", "💡", "🎵", "📚", "☕", "🌈", "🪄", "🧭",
)

internal fun iosAvatarLetter(name: String, fallback: String = "Y"): String =
    name.trim().firstOrNull()?.uppercase() ?: fallback

internal fun IosAppearancePreferences.withAssistantUi(
    ui: IosAssistantUiSettings,
): IosAppearancePreferences {
    val effectiveShowModelIcon = ui.showAssistantAvatar ?: showModelIcon
    return copy(
        showUserAvatar = ui.showUserAvatar ?: showUserAvatar,
        showModelIcon = effectiveShowModelIcon,
        showModelName = effectiveShowModelIcon && showModelName,
        showAssistantBubbles = ui.showAssistantBubbles ?: showAssistantBubbles,
        showTokenUsage = ui.showTokenUsage ?: showTokenUsage,
        autoCloseThinking = ui.autoCloseThinking ?: autoCloseThinking,
        showMessageJumper = ui.showMessageJumper ?: showMessageJumper,
        messageJumperOnLeft = ui.messageJumperOnLeft ?: messageJumperOnLeft,
        fontSizeRatio = ui.fontSizeRatio ?: fontSizeRatio,
        codeBlockAutoWrap = ui.codeBlockAutoWrap ?: codeBlockAutoWrap,
        codeBlockAutoCollapse = ui.codeBlockAutoCollapse ?: codeBlockAutoCollapse,
        showContextStacks = ui.showContextStacks ?: showContextStacks,
        newChatHeaderStyle = ui.newChatHeaderStyle ?: newChatHeaderStyle,
        newChatContentStyle = ui.newChatContentStyle ?: newChatContentStyle,
        newChatShowAvatar = ui.newChatShowAvatar ?: newChatShowAvatar,
    )
}

internal fun iosFontFamilyChoice(
    lastChatFamily: androidx.compose.ui.text.font.FontFamily,
    settings: IosFontSettings,
    usePhoneSystemFont: Boolean,
): androidx.compose.ui.text.font.FontFamily {
    val font = settings.normalize().headerFont
    return when {
        usePhoneSystemFont || settings.usePhoneSystemFont -> androidx.compose.ui.text.font.FontFamily.Default
        font.fontSource == IosFontSource.SYSTEM_CODE -> androidx.compose.ui.text.font.FontFamily.Monospace
        else -> lastChatFamily
    }
}

internal fun iosCodeFontFamily(settings: IosFontSettings): androidx.compose.ui.text.font.FontFamily {
    return when (settings.normalize().codeFont.fontSource) {
        IosFontSource.SYSTEM_CODE, IosFontSource.CUSTOM -> androidx.compose.ui.text.font.FontFamily.Monospace
        IosFontSource.SYSTEM -> androidx.compose.ui.text.font.FontFamily.Default
    }
}
