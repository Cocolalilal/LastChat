package me.rerere.rikkahub.service

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import me.rerere.rikkahub.utils.jsonPrimitiveOrNull
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
    fun describeElapsedTime(elapsedMillis: Long): String {
        val clampedMillis = elapsedMillis.coerceAtLeast(0L)
        val seconds = clampedMillis / 1_000L
        return when {
            seconds < 60L -> "less than a minute"
            seconds < 60L * 60L -> formatElapsedUnit(seconds / 60L, "minute")
            seconds < 60L * 60L * 24L -> formatElapsedUnit(seconds / (60L * 60L), "hour")
            seconds < 60L * 60L * 24L * 7L -> formatElapsedUnit(seconds / (60L * 60L * 24L), "day")
            seconds < 60L * 60L * 24L * 30L -> formatElapsedUnit(seconds / (60L * 60L * 24L * 7L), "week")
            else -> formatElapsedUnit(seconds / (60L * 60L * 24L * 30L), "month")
        }
    }

    fun isWithinActiveHours(
        currentHour: Int,
        startHour: Int,
        endHour: Int,
    ): Boolean {
        val normalizedCurrent = currentHour.mod(24)
        val normalizedStart = startHour.mod(24)
        val normalizedEnd = endHour.mod(24)

        if (normalizedStart == normalizedEnd) return true
        return if (normalizedStart < normalizedEnd) {
            normalizedCurrent in normalizedStart until normalizedEnd
        } else {
            normalizedCurrent >= normalizedStart || normalizedCurrent < normalizedEnd
        }
    }

    fun pickCandidate(
        candidates: List<SpontaneousCandidate>,
        lastSenderAssistantId: Uuid?,
        random: Random,
    ): SpontaneousCandidate? {
        if (candidates.isEmpty()) return null
        val shuffled = candidates.shuffled(random)
        return shuffled.firstOrNull { candidate ->
            candidate.assistantId != lastSenderAssistantId
        } ?: shuffled.firstOrNull()
    }

    fun computeGlobalQuietUntil(
        nowMillis: Long,
        random: Random,
    ): Long {
        val jitterMillis = random.nextLong(0, SPONTANEOUS_GLOBAL_JITTER_MINUTES * 60_000L + 1L)
        return nowMillis + (SPONTANEOUS_WORK_INTERVAL_MINUTES * 60_000L) + jitterMillis
    }

    fun parseResponse(text: String): SpontaneousResponse? {
        val jsonPayload = extractJsonObject(text) ?: return null
        val json = runCatching {
            Json.parseToJsonElement(jsonPayload).jsonObject
        }.getOrNull() ?: return null

        return SpontaneousResponse(
            shouldSend = json["send"]?.jsonPrimitiveOrNull?.booleanOrNull ?: false,
            reason = json["reason"]?.jsonPrimitiveOrNull?.contentOrNull ?: "",
            title = json["title"]?.jsonPrimitiveOrNull?.contentOrNull,
            content = json["content"]?.jsonPrimitiveOrNull?.contentOrNull,
            relation = SpontaneousMessageRelation.fromWireValue(
                json["relation"]?.jsonPrimitiveOrNull?.contentOrNull
            ),
        )
    }

    private fun extractJsonObject(text: String): String? {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start == -1 || end == -1 || end <= start) return null
        return text.substring(start, end + 1)
    }

    private fun formatElapsedUnit(
        value: Long,
        unit: String,
    ): String {
        val safeValue = value.coerceAtLeast(1L)
        return if (safeValue == 1L) {
            "1 $unit"
        } else {
            "$safeValue ${unit}s"
        }
    }
}
