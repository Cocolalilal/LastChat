package me.rerere.common.calendar

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus

data class CalendarHeatmapDay(
    val date: LocalDate,
    val count: Int
)

data class CalendarMonth(
    val year: Int,
    val monthNumber: Int
) : Comparable<CalendarMonth> {
    init {
        require(monthNumber in 1..12) { "monthNumber must be in 1..12" }
    }

    override fun compareTo(other: CalendarMonth): Int {
        return compareValuesBy(this, other, CalendarMonth::year, CalendarMonth::monthNumber)
    }

    fun plusMonths(months: Int): CalendarMonth {
        val zeroBasedMonth = (year * 12) + (monthNumber - 1) + months
        val newYear = if (zeroBasedMonth >= 0) {
            zeroBasedMonth / 12
        } else {
            (zeroBasedMonth - 11) / 12
        }
        return CalendarMonth(
            year = newYear,
            monthNumber = zeroBasedMonth - (newYear * 12) + 1
        )
    }

    fun atDay(dayOfMonth: Int): LocalDate = LocalDate(year, monthNumber, dayOfMonth)

    fun atEndOfMonth(): LocalDate = plusMonths(1).atDay(1) - DatePeriod(days = 1)

    companion object {
        fun from(date: LocalDate): CalendarMonth = CalendarMonth(date.year, date.month.ordinal + 1)
    }
}

data class CalendarHeatmapCellBoundary(
    val top: Boolean = false,
    val right: Boolean = false,
    val bottom: Boolean = false,
    val left: Boolean = false
)

data class CalendarHeatmapCell(
    val date: LocalDate,
    val count: Int,
    val weekIndex: Int,
    val dayIndex: Int,
    val isInWindow: Boolean,
    val month: CalendarMonth?,
    val boundary: CalendarHeatmapCellBoundary = CalendarHeatmapCellBoundary()
)

data class CalendarHeatmapWeekColumn(
    val index: Int,
    val startDate: LocalDate,
    val cells: List<CalendarHeatmapCell>
)

data class CalendarHeatmapMonthMetadata(
    val month: CalendarMonth,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val totalMessageCount: Int,
    val startWeekIndex: Int,
    val endWeekIndex: Int,
    val startRow: Int,
    val endRow: Int,
    val weekSpan: Int
)

data class CalendarHeatmapLayout(
    val windowStart: LocalDate,
    val windowEnd: LocalDate,
    val gridStart: LocalDate,
    val gridEnd: LocalDate,
    val weeks: List<CalendarHeatmapWeekColumn>,
    val months: List<CalendarHeatmapMonthMetadata>
)

fun buildCalendarHeatmapLayout(
    heatmapData: List<CalendarHeatmapDay>,
    windowStart: LocalDate,
    windowEnd: LocalDate
): CalendarHeatmapLayout {
    if (windowEnd < windowStart) {
        return CalendarHeatmapLayout(
            windowStart = windowStart,
            windowEnd = windowEnd,
            gridStart = windowStart,
            gridEnd = windowEnd,
            weeks = emptyList(),
            months = emptyList()
        )
    }

    val gridStart = windowStart.previousOrSameMonday()
    val gridEnd = windowEnd.nextOrSameSunday()
    val countsByDate = heatmapData.associate { it.date to it.count }

    val weekColumns = mutableListOf<CalendarHeatmapWeekColumn>()
    var currentWeekStart = gridStart
    var weekIndex = 0
    while (currentWeekStart <= gridEnd) {
        val cells = (0..6).map { dayIndex ->
            val date = currentWeekStart + DatePeriod(days = dayIndex)
            val isInWindow = date >= windowStart && date <= windowEnd
            CalendarHeatmapCell(
                date = date,
                count = if (isInWindow) countsByDate[date] ?: 0 else 0,
                weekIndex = weekIndex,
                dayIndex = dayIndex,
                isInWindow = isInWindow,
                month = if (isInWindow) CalendarMonth.from(date) else null
            )
        }
        weekColumns += CalendarHeatmapWeekColumn(
            index = weekIndex,
            startDate = currentWeekStart,
            cells = cells
        )
        currentWeekStart += DatePeriod(days = 7)
        weekIndex++
    }

    val weeksWithBoundaries = weekColumns.mapIndexed { currentWeekIndex, week ->
        week.copy(
            cells = week.cells.mapIndexed { dayIndex, cell ->
                if (!cell.isInWindow || cell.month == null) {
                    cell
                } else {
                    val boundary = CalendarHeatmapCellBoundary(
                        top = dayIndex == 0 || weekColumns[currentWeekIndex].cells[dayIndex - 1].month != cell.month,
                        right = currentWeekIndex == weekColumns.lastIndex ||
                            weekColumns[currentWeekIndex + 1].cells[dayIndex].month != cell.month,
                        bottom = dayIndex == week.cells.lastIndex ||
                            weekColumns[currentWeekIndex].cells[dayIndex + 1].month != cell.month,
                        left = currentWeekIndex == 0 ||
                            weekColumns[currentWeekIndex - 1].cells[dayIndex].month != cell.month
                    )
                    cell.copy(boundary = boundary)
                }
            }
        )
    }

    val cellsByDate = weeksWithBoundaries
        .flatMap { it.cells }
        .associateBy { it.date }
    val months = mutableListOf<CalendarHeatmapMonthMetadata>()
    var month = CalendarMonth.from(windowStart)
    val lastMonth = CalendarMonth.from(windowEnd)
    while (month <= lastMonth) {
        val monthStart = maxOf(windowStart, month.atDay(1))
        val monthEnd = minOf(windowEnd, month.atEndOfMonth())
        val startCell = cellsByDate.getValue(monthStart)
        val endCell = cellsByDate.getValue(monthEnd)
        val totalMessageCount = heatmapData.asSequence()
            .filter { it.date >= monthStart && it.date <= monthEnd }
            .sumOf { it.count }

        months += CalendarHeatmapMonthMetadata(
            month = month,
            startDate = monthStart,
            endDate = monthEnd,
            totalMessageCount = totalMessageCount,
            startWeekIndex = startCell.weekIndex,
            endWeekIndex = endCell.weekIndex,
            startRow = startCell.dayIndex,
            endRow = endCell.dayIndex,
            weekSpan = endCell.weekIndex - startCell.weekIndex + 1
        )
        month = month.plusMonths(1)
    }

    return CalendarHeatmapLayout(
        windowStart = windowStart,
        windowEnd = windowEnd,
        gridStart = gridStart,
        gridEnd = gridEnd,
        weeks = weeksWithBoundaries,
        months = months
    )
}

private fun LocalDate.previousOrSameMonday(): LocalDate {
    return this - DatePeriod(days = dayOfWeek.ordinal)
}

private fun LocalDate.nextOrSameSunday(): LocalDate {
    return this + DatePeriod(days = 6 - dayOfWeek.ordinal)
}
