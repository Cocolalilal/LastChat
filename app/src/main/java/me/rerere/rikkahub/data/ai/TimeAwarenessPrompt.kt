package me.rerere.rikkahub.data.ai

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.isEmptyUIMessage
import me.rerere.ai.util.buildTimeAwarenessPromptBlock

internal data class TimeAwarenessRuntimeInfo(
    val now: LocalDateTime,
    val timeZone: TimeZone,
    val timeZoneId: String,
    val timeZoneShortName: String,
)

internal fun buildTimeAwarenessBlock(
    enabled: Boolean,
    fullMessages: List<UIMessage>,
    retainedMessages: List<UIMessage>,
    runtimeInfo: TimeAwarenessRuntimeInfo = AndroidTimeAwarenessRuntimeInfo.now(),
): String? {
    return buildTimeAwarenessPromptBlock(
        enabled = enabled,
        fullMessageTimes = fullMessages
            .filter(UIMessage::isConversationalMessage)
            .map { it.createdAt },
        retainedMessageTimes = retainedMessages
            .filter(UIMessage::isConversationalMessage)
            .map { it.createdAt },
        now = runtimeInfo.now,
        timeZone = runtimeInfo.timeZone,
        timeZoneId = runtimeInfo.timeZoneId,
        timeZoneShortName = runtimeInfo.timeZoneShortName,
    )
}

private fun UIMessage.isConversationalMessage(): Boolean {
    return !parts.isEmptyUIMessage()
}
