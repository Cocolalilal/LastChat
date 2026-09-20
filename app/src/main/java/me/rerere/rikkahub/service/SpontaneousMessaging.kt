package me.rerere.rikkahub.service

import me.rerere.ai.generation.PortableSpontaneousCandidate
import me.rerere.ai.generation.PortableSpontaneousMessaging
import me.rerere.ai.generation.PortableSpontaneousRelation
import me.rerere.ai.generation.PortableSpontaneousResponse
import kotlin.random.Random
import kotlin.uuid.Uuid

internal const val SPONTANEOUS_WORK_NAME = "spontaneous_notification"
internal const val SPONTANEOUS_NOTIFICATION_CHANNEL_ID = "assistant_spontaneous"
internal const val SPONTANEOUS_WORK_INTERVAL_MINUTES = 30L
internal const val SPONTANEOUS_GLOBAL_JITTER_MINUTES = 15L
internal const val EXTRA_IS_SPONTANEOUS_NOTIFICATION = "is_spontaneous_notification"
internal const val EXTRA_SPONTANEOUS_EVENT_ID = "spontaneous_event_id"
internal const val EXTRA_SPONTANEOUS_MESSAGE = "spontaneous_message"
internal const val EXTRA_SPONTANEOUS_RELATION = "spontaneous_relation"

enum class SpontaneousMessageRelation(
    val wireValue: String,
) {
    RECENT_CHAT("recent_chat"),
    UNRELATED("unrelated");

    companion object {
        fun fromWireValue(value: String?): SpontaneousMessageRelation? {
            return entries.firstOrNull { it.wireValue == value }
        }

        fun fromPortable(value: PortableSpontaneousRelation?): SpontaneousMessageRelation? {
            return fromWireValue(value?.wireValue)
        }
    }
}

data class SpontaneousCandidate(
    val assistantId: Uuid,
    val lastNotificationTime: Long,
)

data class SpontaneousResponse(
    val shouldSend: Boolean,
    val reason: String,
    val title: String?,
    val content: String?,
    val relation: SpontaneousMessageRelation?,
)

object SpontaneousMessaging {
    fun describeElapsedTime(elapsedMillis: Long): String =
        PortableSpontaneousMessaging.describeElapsedTime(elapsedMillis)

    fun isWithinActiveHours(
        currentHour: Int,
        startHour: Int,
        endHour: Int,
    ): Boolean = PortableSpontaneousMessaging.isWithinActiveHours(currentHour, startHour, endHour)

    fun pickCandidate(
        candidates: List<SpontaneousCandidate>,
        lastSenderAssistantId: Uuid?,
        random: Random,
    ): SpontaneousCandidate? {
        val selected = PortableSpontaneousMessaging.pickCandidate(
            candidates = candidates.map {
                PortableSpontaneousCandidate(it.assistantId.toString(), it.lastNotificationTime)
            },
            lastSenderAssistantId = lastSenderAssistantId?.toString(),
            random = random,
        ) ?: return null
        return candidates.firstOrNull { it.assistantId.toString() == selected.assistantId }
    }

    fun computeGlobalQuietUntil(
        nowMillis: Long,
        random: Random,
    ): Long = PortableSpontaneousMessaging.computeGlobalQuietUntil(nowMillis, random)

    fun parseResponse(text: String): SpontaneousResponse? {
        val parsed: PortableSpontaneousResponse = PortableSpontaneousMessaging.parseResponse(text) ?: return null
        return SpontaneousResponse(
            shouldSend = parsed.shouldSend,
            reason = parsed.reason,
            title = parsed.title,
            content = parsed.content,
            relation = SpontaneousMessageRelation.fromPortable(parsed.relation),
        )
    }
}
