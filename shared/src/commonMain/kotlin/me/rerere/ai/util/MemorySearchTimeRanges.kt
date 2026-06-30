package me.rerere.ai.util

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

private const val MEMORY_SEARCH_EARLY_MORNING_CUTOFF_HOUR = 7
private const val MEMORY_SEARCH_WEEK_EDGE_GRACE_HOURS = 12L
private const val MEMORY_SEARCH_MONTH_EDGE_GRACE_DAYS = 2
private const val HOUR_MILLIS = 60L * 60L * 1000L

data class MemorySearchTimeRange(
    val startMillis: Long,
    val endMillis: Long,
    val label: String,
) {
    fun contains(timestampMillis: Long): Boolean {
        return timestampMillis in startMillis until endMillis
    }
}

@OptIn(ExperimentalTime::class)
fun parseMemorySearchTimeRange(
    raw: String?,
    nowMillis: Long,
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
): MemorySearchTimeRange? {
    val text = raw?.lowercase()?.trim().orEmpty()
    if (text.isBlank()) return null

    val nowDateTime = Instant.fromEpochMilliseconds(nowMillis).toLocalDateTime(timeZone)
    val nowDate = nowDateTime.date

    fun range(startMillis: Long, endMillis: Long, label: String): MemorySearchTimeRange {
        return MemorySearchTimeRange(
            startMillis = startMillis,
            endMillis = endMillis,
            label = label,
        )
    }

    fun millisAt(date: LocalDate, hour: Int = 0): Long {
        return LocalDateTime(date, LocalTime(hour, 0)).toInstant(timeZone).toEpochMilliseconds()
    }

    fun startOfDay(value: LocalDate): Long = millisAt(value)

    fun startOfMonth(value: LocalDate): LocalDate = LocalDate(value.year, value.month, 1)

    fun morningAfter(value: LocalDate): Long {
        return millisAt(value.plus(1, DateTimeUnit.DAY), MEMORY_SEARCH_EARLY_MORNING_CUTOFF_HOUR)
    }

    fun startOfWeek(value: LocalDate): LocalDate {
        val delta = value.dayOfWeek.ordinal.floorMod(7)
        return value.minus(delta, DateTimeUnit.DAY)
    }

    Regex("""(?:last|past|previous)\s+(\d+)\s+hours?""").find(text)?.groupValues?.getOrNull(1)?.toLongOrNull()?.let { hours ->
        return range(nowMillis - hours * HOUR_MILLIS, nowMillis, "last $hours hours")
    }
    Regex("""(?:last|past|previous)\s+(\d+)\s+days?""").find(text)?.groupValues?.getOrNull(1)?.toLongOrNull()?.let { days ->
        return range(startOfDay(nowDate.minus(days.toInt(), DateTimeUnit.DAY)), nowMillis, "last $days days")
    }
    Regex("""(?:last|past|previous)\s+(\d+)\s+weeks?""").find(text)?.groupValues?.getOrNull(1)?.toLongOrNull()?.let { weeks ->
        val start = startOfWeek(nowDate).minus(weeks.toInt(), DateTimeUnit.WEEK)
        return range(startOfDay(start) - MEMORY_SEARCH_WEEK_EDGE_GRACE_HOURS * HOUR_MILLIS, nowMillis, "last $weeks weeks")
    }
    Regex("""(?:last|past|previous)\s+(\d+)\s+months?""").find(text)?.groupValues?.getOrNull(1)?.toLongOrNull()?.let { months ->
        val start = startOfMonth(nowDate).minus(months.toInt(), DateTimeUnit.MONTH)
        return range(
            startOfDay(start.minus(MEMORY_SEARCH_MONTH_EDGE_GRACE_DAYS, DateTimeUnit.DAY)),
            nowMillis,
            "last $months months"
        )
    }
    Regex("""(\d+)\s+months?\s+ago""").find(text)?.groupValues?.getOrNull(1)?.toLongOrNull()?.let { months ->
        val start = startOfMonth(nowDate.minus(months.toInt(), DateTimeUnit.MONTH))
        return range(
            startOfDay(start.minus(MEMORY_SEARCH_MONTH_EDGE_GRACE_DAYS, DateTimeUnit.DAY)),
            startOfDay(start.plus(1, DateTimeUnit.MONTH).plus(MEMORY_SEARCH_MONTH_EDGE_GRACE_DAYS, DateTimeUnit.DAY)),
            "$months months ago"
        )
    }
    Regex("""(\d+)\s+weeks?\s+ago""").find(text)?.groupValues?.getOrNull(1)?.toLongOrNull()?.let { weeks ->
        val start = startOfWeek(nowDate).minus(weeks.toInt(), DateTimeUnit.WEEK)
        return range(
            startOfDay(start) - MEMORY_SEARCH_WEEK_EDGE_GRACE_HOURS * HOUR_MILLIS,
            startOfDay(start.plus(1, DateTimeUnit.WEEK)) + MEMORY_SEARCH_WEEK_EDGE_GRACE_HOURS * HOUR_MILLIS,
            "$weeks weeks ago"
        )
    }
    Regex("""(\d+)\s+days?\s+ago""").find(text)?.groupValues?.getOrNull(1)?.toLongOrNull()?.let { days ->
        val start = nowDate.minus(days.toInt(), DateTimeUnit.DAY)
        return range(startOfDay(start), morningAfter(start), "$days days ago")
    }

    return when {
        "last day" in text || "past day" in text || "previous day" in text -> {
            val start = nowDate.minus(1, DateTimeUnit.DAY)
            range(startOfDay(start), morningAfter(start).coerceAtMost(nowMillis), "last day")
        }

        "last week" in text -> {
            val start = startOfWeek(nowDate).minus(1, DateTimeUnit.WEEK)
            range(
                startOfDay(start) - MEMORY_SEARCH_WEEK_EDGE_GRACE_HOURS * HOUR_MILLIS,
                startOfDay(start.plus(1, DateTimeUnit.WEEK)) + MEMORY_SEARCH_WEEK_EDGE_GRACE_HOURS * HOUR_MILLIS,
                "last week"
            )
        }

        "this week" in text -> {
            val start = startOfWeek(nowDate)
            range(
                startOfDay(start) - MEMORY_SEARCH_WEEK_EDGE_GRACE_HOURS * HOUR_MILLIS,
                startOfDay(start.plus(1, DateTimeUnit.WEEK)),
                "this week"
            )
        }

        "last month" in text -> {
            val start = startOfMonth(nowDate).minus(1, DateTimeUnit.MONTH)
            range(
                startOfDay(start.minus(MEMORY_SEARCH_MONTH_EDGE_GRACE_DAYS, DateTimeUnit.DAY)),
                startOfDay(start.plus(1, DateTimeUnit.MONTH).plus(MEMORY_SEARCH_MONTH_EDGE_GRACE_DAYS, DateTimeUnit.DAY)),
                "last month"
            )
        }

        "this month" in text -> {
            val start = startOfMonth(nowDate)
            range(
                startOfDay(start.minus(MEMORY_SEARCH_MONTH_EDGE_GRACE_DAYS, DateTimeUnit.DAY)),
                startOfDay(start.plus(1, DateTimeUnit.MONTH)),
                "this month"
            )
        }

        "yesterday" in text -> {
            val start = nowDate.minus(1, DateTimeUnit.DAY)
            range(startOfDay(start), morningAfter(start).coerceAtMost(nowMillis), "yesterday")
        }

        "today" in text || "earlier today" in text -> {
            range(startOfDay(nowDate), morningAfter(nowDate), "today")
        }

        else -> null
    }
}

@OptIn(ExperimentalTime::class)
fun parseMemorySearchTimeRange(
    raw: String?,
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
): MemorySearchTimeRange? {
    return parseMemorySearchTimeRange(
        raw = raw,
        nowMillis = kotlin.time.Clock.System.now().toEpochMilliseconds(),
        timeZone = timeZone,
    )
}

private fun Int.floorMod(other: Int): Int {
    val remainder = this % other
    return if (remainder >= 0) remainder else remainder + other
}
