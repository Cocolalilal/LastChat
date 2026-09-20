package me.rerere.ai.generation

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.random.Random

const val PORTABLE_SPONTANEOUS_WORK_INTERVAL_MINUTES = 30L
const val PORTABLE_SPONTANEOUS_GLOBAL_JITTER_MINUTES = 15L

enum class PortableSpontaneousRelation(val wireValue: String) {
    RECENT_CHAT("recent_chat"),
    UNRELATED("unrelated");

    companion object {
        fun fromWireValue(value: String?): PortableSpontaneousRelation? {
            return entries.firstOrNull { it.wireValue == value }
        }
    }
}

data class PortableSpontaneousCandidate(
    val assistantId: String,
    val lastNotificationTime: Long,
)

data class PortableSpontaneousResponse(
    val shouldSend: Boolean,
    val reason: String,
    val title: String?,
    val content: String?,
    val relation: PortableSpontaneousRelation?,
)

object PortableSpontaneousMessaging {
    const val WORK_INTERVAL_MS = PORTABLE_SPONTANEOUS_WORK_INTERVAL_MINUTES * 60_000L
    const val GLOBAL_JITTER_MS = PORTABLE_SPONTANEOUS_GLOBAL_JITTER_MINUTES * 60_000L

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
        candidates: List<PortableSpontaneousCandidate>,
        lastSenderAssistantId: String?,
        random: Random,
    ): PortableSpontaneousCandidate? {
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
        val jitterMillis = random.nextLong(0, GLOBAL_JITTER_MS + 1L)
        return nowMillis + WORK_INTERVAL_MS + jitterMillis
    }

    fun parseResponse(text: String): PortableSpontaneousResponse? {
        val jsonPayload = extractJsonObject(text) ?: return null
        val json = runCatching {
            Json.parseToJsonElement(jsonPayload).jsonObject
        }.getOrNull() ?: return null
        return PortableSpontaneousResponse(
            shouldSend = json.boolean("send") ?: false,
            reason = json.string("reason").orEmpty(),
            title = json.string("title"),
            content = json.string("content"),
            relation = PortableSpontaneousRelation.fromWireValue(json.string("relation")),
        )
    }

    private fun extractJsonObject(text: String): String? {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start == -1 || end == -1 || end <= start) return null
        return text.substring(start, end + 1)
    }

    private fun formatElapsedUnit(value: Long, unit: String): String {
        val safeValue = value.coerceAtLeast(1L)
        return if (safeValue == 1L) "1 $unit" else "$safeValue ${unit}s"
    }

    private fun JsonObject.string(key: String): String? {
        return this[key]?.jsonPrimitive?.contentOrNull
    }

    private fun JsonObject.boolean(key: String): Boolean? {
        val primitive = this[key]?.jsonPrimitive ?: return null
        return primitive.booleanOrNull ?: primitive.contentOrNull?.toBooleanStrictOrNull()
    }
}
