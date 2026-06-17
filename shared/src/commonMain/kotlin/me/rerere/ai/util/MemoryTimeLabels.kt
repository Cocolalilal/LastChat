package me.rerere.ai.util

import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
fun fuzzyMemoryAgeLabel(
    timestampMillis: Long,
    nowMillis: Long,
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
): String {
    if (timestampMillis <= 0L) return "some time ago"
    val now = Instant.fromEpochMilliseconds(nowMillis).toLocalDateTime(timeZone).date
    val then = Instant.fromEpochMilliseconds(timestampMillis.coerceAtMost(nowMillis)).toLocalDateTime(timeZone).date
    val days = then.daysUntil(now)
    val months = (now.year - then.year) * 12 + (now.month.ordinal - then.month.ordinal)

    return when {
        days <= 0 -> "earlier today"
        days == 1 -> "yesterday"
        days <= 3 -> "a few days ago"
        days <= 10 -> "about a week ago"
        days <= 21 -> "a couple weeks ago"
        months <= 1 -> "about a month ago"
        months <= 3 -> "a couple months ago"
        months <= 11 -> "months ago"
        else -> "a long time ago"
    }
}

@OptIn(ExperimentalTime::class)
fun fuzzyMemoryAgeLabel(
    timestampMillis: Long,
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
): String {
    return fuzzyMemoryAgeLabel(
        timestampMillis = timestampMillis,
        nowMillis = Clock.System.now().toEpochMilliseconds(),
        timeZone = timeZone,
    )
}
