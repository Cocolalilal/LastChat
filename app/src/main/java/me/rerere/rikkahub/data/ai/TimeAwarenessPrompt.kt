package me.rerere.rikkahub.data.ai

import kotlinx.datetime.TimeZone
import kotlinx.datetime.toKotlinLocalDateTime
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.isEmptyUIMessage
import me.rerere.ai.util.buildTimeAwarenessPromptBlock
import java.time.ZonedDateTime
import java.time.format.TextStyle
import java.util.Locale

internal fun buildTimeAwarenessBlock(
    enabled: Boolean,
    fullMessages: List<UIMessage>,
    retainedMessages: List<UIMessage>,
    now: ZonedDateTime = ZonedDateTime.now(),
): String? {
    val zoneId = now.zone
    return buildTimeAwarenessPromptBlock(
        enabled = enabled,
        fullMessageTimes = fullMessages
            .filter(UIMessage::isConversationalMessage)
            .map { it.createdAt },
        retainedMessageTimes = retainedMessages
            .filter(UIMessage::isConversationalMessage)
            .map { it.createdAt },
        now = now.toLocalDateTime().toKotlinLocalDateTime(),
        timeZone = TimeZone.of(zoneId.id),
        timeZoneId = zoneId.id,
        timeZoneShortName = zoneId.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)
    )
}

private fun UIMessage.isConversationalMessage(): Boolean {
    return !parts.isEmptyUIMessage()
}
