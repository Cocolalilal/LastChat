package me.rerere.rikkahub.data.ai

import kotlinx.datetime.toJavaLocalDateTime
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.isEmptyUIMessage
import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.roundToLong

private const val SIGNIFICANT_GAP_MINUTES = 30L
private const val DATE_BASED_GAP_HOURS = 20L
private const val LONG_SPAN_HOURS = 6L
private const val MAX_TIMELINE_NOTES = 3

private val promptTimestampFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm z", Locale.ENGLISH)
private val weekdayFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("EEEE", Locale.ENGLISH)
private val monthFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("MMMM", Locale.ENGLISH)

private enum class GapUnit {
    MINUTE,
    HOUR,
    DAY,
    WEEK,
    MONTH,
    YEAR,
}

private data class HumanizedGap(
    val label: String,
    val sentence: String,
    val unit: GapUnit,
)

internal fun buildTimeAwarenessBlock(
    enabled: Boolean,
    fullMessages: List<UIMessage>,
    retainedMessages: List<UIMessage>,
    now: ZonedDateTime = ZonedDateTime.now(),
): String? {
    if (!enabled) return null

    val zoneId = now.zone
    val conversationalMessages = fullMessages.filter(UIMessage::isConversationalMessage)
    val currentMessageTime = conversationalMessages.lastOrNull().toZonedDateTime(zoneId)
    val previousMessageTime = conversationalMessages.dropLast(1).lastOrNull().toZonedDateTime(zoneId)
    val lines = mutableListOf<String>()
    lines += "[Time Awareness]"
    lines += "Local timestamp: ${promptTimestampFormatter.format(now)}"
    lines += "Weekday: ${weekdayFormatter.format(now)}"
    lines += "Month: ${monthFormatter.format(now)}"
    lines += "Year: ${now.year}"
    lines += "Timezone: ${zoneId.id} (${now.zone.getDisplayName(java.time.format.TextStyle.SHORT, Locale.ENGLISH)})"

    if (currentMessageTime == null || previousMessageTime == null) {
        lines += "Conversation state: This appears to be the first visible message in this conversation."
    } else {
        val previousGap = humanizeGap(previousMessageTime, currentMessageTime)
        if (safePositiveDuration(previousMessageTime, currentMessageTime) != null) {
            if (previousGap != null) {
                lines += "${previousGap.sentence} since the last message."
            }
            if (previousMessageTime.toLocalDate() != currentMessageTime.toLocalDate() && previousGap?.unit !in setOf(
                    GapUnit.DAY,
                    GapUnit.WEEK,
                    GapUnit.MONTH,
                    GapUnit.YEAR
                )
            ) {
                lines += "Local day changed since the previous message."
            }
            if (previousMessageTime.month != currentMessageTime.month && previousGap?.unit !in setOf(
                    GapUnit.MONTH,
                    GapUnit.YEAR
                )
            ) {
                lines += "Local month changed since the previous message."
            }
            if (previousMessageTime.year != currentMessageTime.year && previousGap?.unit != GapUnit.YEAR) {
                lines += "Local year changed since the previous message."
            }
        }
    }

    val retainedTimes = retainedMessages
        .filter(UIMessage::isConversationalMessage)
        .mapNotNull { it.toZonedDateTime(zoneId) }
    if (retainedTimes.size >= 2) {
        val retainedSpanGap = humanizeGap(retainedTimes.first(), now)
        safePositiveDuration(retainedTimes.first(), now)?.let { span ->
            if ((span.toHours() >= LONG_SPAN_HOURS || retainedTimes.first().toLocalDate() != now.toLocalDate()) && retainedSpanGap != null) {
                lines += "Retained context span: ${retainedSpanGap.label}."
            }
        }

        val timelineNotes = buildTimelineNotes(retainedTimes)
        if (timelineNotes.isNotEmpty()) {
            lines += "Recent retained timeline notes:"
            lines += timelineNotes
        }
    }

    return lines.joinToString("\n")
}

private fun UIMessage.isConversationalMessage(): Boolean {
    return !parts.isEmptyUIMessage()
}

private fun buildTimelineNotes(retainedTimes: List<ZonedDateTime>): List<String> {
    if (retainedTimes.size < 3) return emptyList()

    val notes = mutableListOf<String>()
    for (index in 1 until retainedTimes.lastIndex) {
        val previous = retainedTimes[index - 1]
        val current = retainedTimes[index]
        if (safePositiveDuration(previous, current) == null) continue
        val humanizedGap = humanizeGap(previous, current)

        val markers = mutableListOf<String>()
        if (humanizedGap != null) {
            markers += humanizedGap.sentence.replaceFirstChar { it.lowercase(Locale.ENGLISH) }
        }
        if (previous.toLocalDate() != current.toLocalDate() && humanizedGap?.unit !in setOf(
                GapUnit.DAY,
                GapUnit.WEEK,
                GapUnit.MONTH,
                GapUnit.YEAR
            )
        ) {
            markers += "day changed"
        }
        if (previous.month != current.month && humanizedGap?.unit !in setOf(
                GapUnit.MONTH,
                GapUnit.YEAR
            )
        ) {
            markers += "month changed"
        }
        if (previous.year != current.year && humanizedGap?.unit != GapUnit.YEAR) {
            markers += "year changed"
        }
        if (markers.isEmpty()) continue

        notes += "- Before ${promptTimestampFormatter.format(current)}: ${markers.joinToString(", ")}."
    }

    return notes.takeLast(MAX_TIMELINE_NOTES).reversed()
}

private fun UIMessage?.toZonedDateTime(zoneId: ZoneId): ZonedDateTime? {
    if (this == null) return null
    return runCatching {
        createdAt.toJavaLocalDateTime().atZone(zoneId)
    }.getOrNull()
}

private fun safePositiveDuration(
    start: ZonedDateTime,
    end: ZonedDateTime,
): Duration? {
    val duration = runCatching { Duration.between(start, end) }.getOrNull() ?: return null
    return duration.takeIf { !it.isNegative }
}

private fun humanizeGap(
    start: ZonedDateTime,
    end: ZonedDateTime,
): HumanizedGap? {
    val duration = safePositiveDuration(start, end) ?: return null
    val totalMinutes = duration.toMinutes()
    if (totalMinutes < SIGNIFICANT_GAP_MINUTES) return null

    if (totalMinutes < 60) {
        val roundedMinutes = roundShortGapMinutes(totalMinutes)
        return humanizedGap(
            label = formatUnit(roundedMinutes, "minute"),
            unit = GapUnit.MINUTE
        )
    }
    if (totalMinutes < 90) {
        return humanizedGap(
            label = "1 hour",
            unit = GapUnit.HOUR
        )
    }
    if (totalMinutes < DATE_BASED_GAP_HOURS * 60) {
        val roundedMinutes = roundToNearestTen(totalMinutes)
        val hours = roundedMinutes / 60
        val minutes = roundedMinutes % 60
        val label = if (minutes > 0) {
            "${formatUnit(hours, "hour")} and ${formatUnit(minutes, "minute")}"
        } else {
            formatUnit(hours, "hour")
        }
        return humanizedGap(
            label = label,
            unit = GapUnit.HOUR
        )
    }

    val daysBetween = ChronoUnit.DAYS.between(start.toLocalDate(), end.toLocalDate()).coerceAtLeast(1)
    if (daysBetween < 14) {
        return humanizedGap(
            label = formatUnit(daysBetween, "day"),
            unit = GapUnit.DAY
        )
    }
    if (daysBetween < 60) {
        val roundedWeeks = (daysBetween / 7.0).roundToLong().coerceAtLeast(2)
        return humanizedGap(
            label = formatUnit(roundedWeeks, "week"),
            unit = GapUnit.WEEK
        )
    }
    if (daysBetween < 365) {
        val roundedMonths = (daysBetween / 30.0).roundToLong().coerceAtLeast(2)
        return humanizedGap(
            label = formatUnit(roundedMonths, "month"),
            unit = GapUnit.MONTH
        )
    }
    val roundedYears = (daysBetween / 365.0).roundToLong().coerceAtLeast(1)
    return humanizedGap(
        label = formatUnit(roundedYears, "year"),
        unit = GapUnit.YEAR
    )
}

private fun humanizedGap(
    label: String,
    unit: GapUnit,
): HumanizedGap {
    val verb = if (label.startsWith("1 ") && !label.contains(" and ")) "has" else "have"
    return HumanizedGap(
        label = "about $label",
        sentence = "About $label $verb passed",
        unit = unit
    )
}

private fun roundShortGapMinutes(totalMinutes: Long): Long {
    return roundToNearestTen(totalMinutes).coerceIn(SIGNIFICANT_GAP_MINUTES, 50L)
}

private fun roundToNearestTen(totalMinutes: Long): Long {
    return ((totalMinutes / 10.0).roundToLong() * 10).coerceAtLeast(10L)
}

private fun formatUnit(
    value: Long,
    unit: String,
): String {
    return if (value == 1L) {
        "1 $unit"
    } else {
        "$value ${unit}s"
    }
}
