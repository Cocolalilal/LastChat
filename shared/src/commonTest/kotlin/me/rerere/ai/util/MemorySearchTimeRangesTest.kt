package me.rerere.ai.util

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
class MemorySearchTimeRangesTest {
    private val utc = TimeZone.UTC

    @Test
    fun parseMemorySearchTimeRangeUnderstandsRoughSpans() {
        val now = millisAt(2026, 5, 26, 12, 0)

        val lastMonth = parseMemorySearchTimeRange("last month", now, utc)
        val fourMonthsAgo = parseMemorySearchTimeRange("4 months ago", now, utc)

        assertEquals("last month", lastMonth?.label)
        assertTrue(lastMonth?.contains(millisAt(2026, 4, 15, 12, 0)) == true)
        assertFalse(lastMonth?.contains(millisAt(2026, 5, 15, 12, 0)) == true)
        assertEquals("4 months ago", fourMonthsAgo?.label)
    }

    @Test
    fun parseMemorySearchTimeRangeCarriesLastDayIntoEarlyMorning() {
        val now = millisAt(2026, 5, 26, 12, 0)

        val lastDay = parseMemorySearchTimeRange("last day", now, utc)

        assertEquals("last day", lastDay?.label)
        assertTrue(lastDay?.contains(millisAt(2026, 5, 25, 23, 45)) == true)
        assertTrue(lastDay?.contains(millisAt(2026, 5, 26, 6, 59)) == true)
        assertFalse(lastDay?.contains(millisAt(2026, 5, 26, 7, 1)) == true)
    }

    private fun millisAt(
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int,
    ): Long {
        return LocalDateTime(
            date = LocalDate(year, month, day),
            time = LocalTime(hour, minute)
        ).toInstant(utc).toEpochMilliseconds()
    }
}
