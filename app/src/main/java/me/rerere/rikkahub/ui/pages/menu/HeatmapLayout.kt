package me.rerere.rikkahub.ui.pages.menu

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters

internal data class HeatmapCellBoundary(
    val top: Boolean = false,
    val right: Boolean = false,
    val bottom: Boolean = false,
    val left: Boolean = false
)

internal data class HeatmapCell(
    val date: LocalDate,
    val count: Int,
    val weekIndex: Int,
    val dayIndex: Int,
    val isInWindow: Boolean,
    val month: YearMonth?,
    val boundary: HeatmapCellBoundary = HeatmapCellBoundary()
)

internal data class HeatmapWeekColumn(
    val index: Int,
    val startDate: LocalDate,
    val cells: List<HeatmapCell>
)

internal data class HeatmapMonthMetadata(
    val month: YearMonth,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val totalMessageCount: Int,
    val startWeekIndex: Int,
    val endWeekIndex: Int,
    val startRow: Int,
    val endRow: Int,
    val weekSpan: Int
)

internal data class HeatmapLayout(
    val windowStart: LocalDate,
    val windowEnd: LocalDate,
    val gridStart: LocalDate,
    val gridEnd: LocalDate,
    val weeks: List<HeatmapWeekColumn>,
    val months: List<HeatmapMonthMetadata>
)

internal fun buildHeatmapLayout(
    heatmapData: List<HeatmapDay>,
    windowStart: LocalDate,
    windowEnd: LocalDate
): HeatmapLayout {
    if (windowEnd.isBefore(windowStart)) {
        return HeatmapLayout(
            windowStart = windowStart,
            windowEnd = windowEnd,
            gridStart = windowStart,
            gridEnd = windowEnd,
            weeks = emptyList(),
            months = emptyList()
        )
    }

    val gridStart = windowStart.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val gridEnd = windowEnd.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
    val countsByDate = heatmapData.associate { it.date to it.count }

    val weekColumns = mutableListOf<HeatmapWeekColumn>()
    var currentWeekStart = gridStart
    var weekIndex = 0
    while (!currentWeekStart.isAfter(gridEnd)) {
        val cells = (0..6).map { dayIndex ->
            val date = currentWeekStart.plusDays(dayIndex.toLong())
            val isInWindow = !date.isBefore(windowStart) && !date.isAfter(windowEnd)
            HeatmapCell(
                date = date,
                count = if (isInWindow) countsByDate[date] ?: 0 else 0,
                weekIndex = weekIndex,
                dayIndex = dayIndex,
                isInWindow = isInWindow,
                month = if (isInWindow) YearMonth.from(date) else null
            )
        }
        weekColumns += HeatmapWeekColumn(
            index = weekIndex,
            startDate = currentWeekStart,
            cells = cells
        )
        currentWeekStart = currentWeekStart.plusWeeks(1)
        weekIndex++
    }

    val weeksWithBoundaries = weekColumns.mapIndexed { currentWeekIndex, week ->
        week.copy(
            cells = week.cells.mapIndexed { dayIndex, cell ->
                if (!cell.isInWindow || cell.month == null) {
                    cell
                } else {
                    val boundary = HeatmapCellBoundary(
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
    val months = mutableListOf<HeatmapMonthMetadata>()
    var month = YearMonth.from(windowStart)
    val lastMonth = YearMonth.from(windowEnd)
    while (!month.isAfter(lastMonth)) {
        val monthStart = maxOf(windowStart, month.atDay(1))
        val monthEnd = minOf(windowEnd, month.atEndOfMonth())
        val startCell = cellsByDate.getValue(monthStart)
        val endCell = cellsByDate.getValue(monthEnd)
        val totalMessageCount = heatmapData.asSequence()
            .filter { !it.date.isBefore(monthStart) && !it.date.isAfter(monthEnd) }
            .sumOf { it.count }

        months += HeatmapMonthMetadata(
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

    return HeatmapLayout(
        windowStart = windowStart,
        windowEnd = windowEnd,
        gridStart = gridStart,
        gridEnd = gridEnd,
        weeks = weeksWithBoundaries,
        months = months
    )
}
