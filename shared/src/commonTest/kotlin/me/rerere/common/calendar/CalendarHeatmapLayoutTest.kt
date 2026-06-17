package me.rerere.common.calendar

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

class CalendarHeatmapLayoutTest {
    @Test
    fun layoutShouldStartAndEndMonthOnActualWeekdayRows() {
        val windowStart = LocalDate(2024, 5, 1)
        val windowEnd = LocalDate(2024, 5, 31)

        val layout = buildCalendarHeatmapLayout(
            heatmapData = buildWindowData(windowStart, windowEnd) { 1 },
            windowStart = windowStart,
            windowEnd = windowEnd
        )

        val may = layout.months.single()
        assertEquals(2, may.startRow)
        assertEquals(4, may.endRow)
        assertFalse(layout.weeks[0].cells[0].isInWindow)
        assertFalse(layout.weeks[0].cells[1].isInWindow)
        assertTrue(layout.weeks[0].cells[2].isInWindow)
    }

    @Test
    fun layoutShouldPreserveAdjacentMonthBoundariesInsideTheSameWeek() {
        val windowStart = LocalDate(2024, 1, 1)
        val windowEnd = LocalDate(2024, 2, 29)

        val layout = buildCalendarHeatmapLayout(
            heatmapData = buildWindowData(windowStart, windowEnd) { it.day },
            windowStart = windowStart,
            windowEnd = windowEnd
        )

        val january = layout.months.first { it.month == CalendarMonth(2024, 1) }
        val february = layout.months.first { it.month == CalendarMonth(2024, 2) }
        assertEquals(january.endWeekIndex, february.startWeekIndex)
        assertEquals(2, january.endRow)
        assertEquals(3, february.startRow)

        val januaryLast = layout.weeks[january.endWeekIndex].cells[january.endRow]
        val februaryFirst = layout.weeks[february.startWeekIndex].cells[february.startRow]
        assertTrue(januaryLast.boundary.bottom)
        assertTrue(januaryLast.boundary.right)
        assertTrue(februaryFirst.boundary.top)
        assertTrue(februaryFirst.boundary.left)

        assertEquals((1..31).sum(), january.totalMessageCount)
        assertEquals((1..29).sum(), february.totalMessageCount)
    }

    @Test
    fun layoutShouldHandleLeapYearFebruary() {
        val windowStart = LocalDate(2024, 2, 1)
        val windowEnd = LocalDate(2024, 2, 29)

        val layout = buildCalendarHeatmapLayout(
            heatmapData = buildWindowData(windowStart, windowEnd) { 2 },
            windowStart = windowStart,
            windowEnd = windowEnd
        )

        val february = layout.months.single()
        assertEquals(3, february.startRow)
        assertEquals(3, february.endRow)
        assertEquals(29 * 2, february.totalMessageCount)
        assertEquals(5, february.weekSpan)
    }

    @Test
    fun layoutShouldHandleNonLeapFebruary() {
        val windowStart = LocalDate(2025, 2, 1)
        val windowEnd = LocalDate(2025, 2, 28)

        val layout = buildCalendarHeatmapLayout(
            heatmapData = buildWindowData(windowStart, windowEnd) { 3 },
            windowStart = windowStart,
            windowEnd = windowEnd
        )

        val february = layout.months.single()
        assertEquals(5, february.startRow)
        assertEquals(4, february.endRow)
        assertEquals(28 * 3, february.totalMessageCount)
        assertEquals(5, february.weekSpan)
    }

    @Test
    fun layoutShouldKeepTrailingDaysOutsideAnInProgressMonthTransparent() {
        val windowStart = LocalDate(2024, 7, 1)
        val windowEnd = LocalDate(2024, 7, 10)

        val layout = buildCalendarHeatmapLayout(
            heatmapData = buildWindowData(windowStart, windowEnd) { 1 },
            windowStart = windowStart,
            windowEnd = windowEnd
        )

        val july = layout.months.single()
        assertEquals(2, july.endRow)

        val trailingWeek = layout.weeks.last()
        assertTrue(trailingWeek.cells[2].isInWindow)
        assertFalse(trailingWeek.cells[3].isInWindow)
        assertFalse(trailingWeek.cells[6].isInWindow)
    }

    @Test
    fun layoutShouldMapEachVisibleDateExactlyOnce() {
        val windowStart = LocalDate(2024, 1, 1)
        val windowEnd = LocalDate(2024, 3, 10)

        val layout = buildCalendarHeatmapLayout(
            heatmapData = buildWindowData(windowStart, windowEnd) { 1 },
            windowStart = windowStart,
            windowEnd = windowEnd
        )

        val visibleDates = layout.weeks
            .flatMap { it.cells }
            .filter { it.isInWindow }
            .map { it.date }

        val expectedDates = generateSequence(windowStart) { current ->
            (current + DatePeriod(days = 1)).takeUnless { it > windowEnd }
        }.toList()

        assertEquals(expectedDates.size, visibleDates.size)
        assertEquals(expectedDates.size, visibleDates.distinct().size)
        assertEquals(expectedDates, visibleDates)

        expectedDates.forEach { date ->
            val cell = layout.weeks
                .flatMap { it.cells }
                .firstOrNull { it.date == date && it.isInWindow }
            assertNotNull(cell)
        }
    }

    private fun buildWindowData(
        windowStart: LocalDate,
        windowEnd: LocalDate,
        countForDate: (LocalDate) -> Int
    ): List<CalendarHeatmapDay> = generateSequence(windowStart) { current ->
        (current + DatePeriod(days = 1)).takeUnless { it > windowEnd }
    }.map { date ->
        CalendarHeatmapDay(
            date = date,
            count = countForDate(date)
        )
    }.toList()
}
