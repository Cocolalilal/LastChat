package me.rerere.ai.util

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.toInstant
import kotlin.math.roundToLong
import kotlin.time.ExperimentalTime

private const val SIGNIFICANT_GAP_MINUTES = 30L
private const val DATE_BASED_GAP_HOURS = 20L
private const val LONG_SPAN_HOURS = 6L
private const val MAX_TIMELINE_NOTES = 3

private val weekdayNames = listOf(
    "Monday",
    "Tuesday",
    "Wednesday",
    "Thursday",
    "Friday",
    "Saturday",
    "Sunday"
)

private val monthNames = listOf(
    "January",
    "February",
    "March",
    "April",
    "May",
    "June",
    "July",
    "August",
    "September",
    "October",
    "November",
    "December"
)

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

fun buildTimeAwarenessPromptBlock(
    enabled: Boolean,
    fullMessageTimes: List<LocalDateTime>,
    retainedMessageTimes: List<LocalDateTime>,
    now: LocalDateTime,
    timeZone: TimeZone,
    timeZoneId: String = timeZone.id,
    timeZoneShortName: String = timeZone.id,
): String? {
    if (!enabled) return null

    val currentMessageTime = fullMessageTimes.lastOrNull()
    val previousMessageTime = fullMessageTimes.dropLast(1).lastOrNull()
    val lines = mutableListOf<String>()
    lines += "[Time Awareness]"
    lines += "Local timestamp: ${formatPromptTimestamp(now, timeZoneShortName)}"
    lines += "Weekday: ${weekdayNames[now.date.dayOfWeek.ordinal]}"
    lines += "Month: ${monthNames[now.month.ordinal]}"
    lines += "Year: ${now.year}"
    lines += "Timezone: $timeZoneId ($timeZoneShortName)"

    if (currentMessageTime == null || previousMessageTime == null) {
        lines += "Conversation state: This appears to be the first visible message in this conversation."
    } else {
        val previousGap = humanizeGap(previousMessageTime, currentMessageTime, timeZone)
        if (safePositiveDurationMinutes(previousMessageTime, currentMessageTime, timeZone) != null) {
            if (previousGap != null) {
                lines += "${previousGap.sentence} since the last message."
            }
            if (previousMessageTime.date != currentMessageTime.date && previousGap?.unit !in setOf(
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

    if (retainedMessageTimes.size >= 2) {
        val retainedSpanGap = humanizeGap(retainedMessageTimes.first(), now, timeZone)
        safePositiveDurationMinutes(retainedMessageTimes.first(), now, timeZone)?.let { spanMinutes ->
            if ((spanMinutes >= LONG_SPAN_HOURS * 60 || retainedMessageTimes.first().date != now.date) && retainedSpanGap != null) {
                lines += "Retained context span: ${retainedSpanGap.label}."
            }
        }

        val timelineNotes = buildTimelineNotes(retainedMessageTimes, timeZone, timeZoneShortName)
        if (timelineNotes.isNotEmpty()) {
            lines += "Recent retained timeline notes:"
            lines += timelineNotes
        }
    }

    return lines.joinToString("\n")
}

private fun buildTimelineNotes(
    retainedTimes: List<LocalDateTime>,
    timeZone: TimeZone,
    timeZoneShortName: String,
): List<String> {
    if (retainedTimes.size < 3) return emptyList()

    val notes = mutableListOf<String>()
    for (index in 1 until retainedTimes.lastIndex) {
        val previous = retainedTimes[index - 1]
        val current = retainedTimes[index]
        if (safePositiveDurationMinutes(previous, current, timeZone) == null) continue
        val humanizedGap = humanizeGap(previous, current, timeZone)

        val markers = mutableListOf<String>()
        if (humanizedGap != null) {
            markers += humanizedGap.sentence.replaceFirstChar { it.lowercase() }
        }
        if (previous.date != current.date && humanizedGap?.unit !in setOf(
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

        notes += "- Before ${formatPromptTimestamp(current, timeZoneShortName)}: ${markers.joinToString(", ")}."
    }

    return notes.takeLast(MAX_TIMELINE_NOTES).reversed()
}

@OptIn(ExperimentalTime::class)
private fun safePositiveDurationMinutes(
    start: LocalDateTime,
    end: LocalDateTime,
    timeZone: TimeZone,
): Long? {
    val duration = runCatching { end.toInstant(timeZone) - start.toInstant(timeZone) }.getOrNull() ?: return null
    return duration.inWholeMinutes.takeIf { it >= 0L }
}

private fun humanizeGap(
    start: LocalDateTime,
    end: LocalDateTime,
    timeZone: TimeZone,
): HumanizedGap? {
    val totalMinutes = safePositiveDurationMinutes(start, end, timeZone) ?: return null
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

    val daysBetween = start.date.daysUntil(end.date).coerceAtLeast(1)
    if (daysBetween < 14) {
        return humanizedGap(
            label = formatUnit(daysBetween.toLong(), "day"),
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

private fun formatPromptTimestamp(
    value: LocalDateTime,
    timeZoneShortName: String,
): String {
    return "${formatDate(value.date)} ${value.hour.twoDigits()}:${value.minute.twoDigits()} $timeZoneShortName"
}

private fun formatDate(date: LocalDate): String {
    return "${date.year.toString().padStart(4, '0')}-${date.monthNumberCompat().twoDigits()}-${date.day.twoDigits()}"
}

private fun LocalDate.monthNumberCompat(): Int = month.ordinal + 1

private fun Int.twoDigits(): String = toString().padStart(2, '0')
