package me.rerere.rikkahub.ui.pages.menu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class HeatmapLayoutTest {
    @Test
    fun `layout should start and end month on actual weekday rows`() {
        val windowStart = LocalDate.of(2024, 5, 1)
        val windowEnd = LocalDate.of(2024, 5, 31)

        val layout = buildHeatmapLayout(
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
    fun `layout should preserve adjacent month boundaries inside the same week`() {
        val windowStart = LocalDate.of(2024, 1, 1)
        val windowEnd = LocalDate.of(2024, 2, 29)

        val layout = buildHeatmapLayout(
            heatmapData = buildWindowData(windowStart, windowEnd) { it.dayOfMonth },
            windowStart = windowStart,
            windowEnd = windowEnd
        )

        val january = layout.months.first { it.month == YearMonth.of(2024, 1) }
        val february = layout.months.first { it.month == YearMonth.of(2024, 2) }
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
    fun `layout should handle leap year february`() {
        val windowStart = LocalDate.of(2024, 2, 1)
        val windowEnd = LocalDate.of(2024, 2, 29)

        val layout = buildHeatmapLayout(
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
    fun `layout should handle non leap february`() {
        val windowStart = LocalDate.of(2025, 2, 1)
        val windowEnd = LocalDate.of(2025, 2, 28)

        val layout = buildHeatmapLayout(
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
    fun `layout should keep trailing days outside an in progress month transparent`() {
        val windowStart = LocalDate.of(2024, 7, 1)
        val windowEnd = LocalDate.of(2024, 7, 10)

        val layout = buildHeatmapLayout(
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
    fun `layout should map each visible date exactly once`() {
        val windowStart = LocalDate.of(2024, 1, 1)
        val windowEnd = LocalDate.of(2024, 3, 10)

        val layout = buildHeatmapLayout(
            heatmapData = buildWindowData(windowStart, windowEnd) { 1 },
            windowStart = windowStart,
            windowEnd = windowEnd
        )

        val visibleDates = layout.weeks
            .flatMap { it.cells }
            .filter { it.isInWindow }
            .map { it.date }

        val expectedDates = generateSequence(windowStart) { current ->
            current.plusDays(1).takeUnless { it.isAfter(windowEnd) }
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
    ): List<HeatmapDay> = generateSequence(windowStart) { current ->
        current.plusDays(1).takeUnless { it.isAfter(windowEnd) }
    }.map { date ->
        HeatmapDay(
            date = date,
            count = countForDate(date)
        )
    }.toList()
}
