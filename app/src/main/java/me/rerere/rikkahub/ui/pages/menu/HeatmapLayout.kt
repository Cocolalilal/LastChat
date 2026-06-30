package me.rerere.rikkahub.ui.pages.menu

import me.rerere.common.calendar.CalendarHeatmapCell
import me.rerere.common.calendar.CalendarHeatmapDay
import me.rerere.common.calendar.CalendarHeatmapLayout
import me.rerere.common.calendar.CalendarHeatmapMonthMetadata
import me.rerere.common.calendar.CalendarHeatmapWeekColumn
import me.rerere.common.calendar.CalendarMonth
import me.rerere.common.calendar.buildCalendarHeatmapLayout
import java.time.LocalDate
import java.time.YearMonth

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
    return buildCalendarHeatmapLayout(
        heatmapData = heatmapData.map { CalendarHeatmapDay(it.date.toKotlinLocalDate(), it.count) },
        windowStart = windowStart.toKotlinLocalDate(),
        windowEnd = windowEnd.toKotlinLocalDate()
    ).toAndroidHeatmapLayout()
}

private fun CalendarHeatmapLayout.toAndroidHeatmapLayout(): HeatmapLayout {
    return HeatmapLayout(
        windowStart = windowStart.toJavaLocalDate(),
        windowEnd = windowEnd.toJavaLocalDate(),
        gridStart = gridStart.toJavaLocalDate(),
        gridEnd = gridEnd.toJavaLocalDate(),
        weeks = weeks.map { it.toAndroidHeatmapWeekColumn() },
        months = months.map { it.toAndroidHeatmapMonthMetadata() }
    )
}

private fun CalendarHeatmapWeekColumn.toAndroidHeatmapWeekColumn(): HeatmapWeekColumn {
    return HeatmapWeekColumn(
        index = index,
        startDate = startDate.toJavaLocalDate(),
        cells = cells.map { it.toAndroidHeatmapCell() }
    )
}

private fun CalendarHeatmapCell.toAndroidHeatmapCell(): HeatmapCell {
    return HeatmapCell(
        date = date.toJavaLocalDate(),
        count = count,
        weekIndex = weekIndex,
        dayIndex = dayIndex,
        isInWindow = isInWindow,
        month = month?.toJavaYearMonth(),
        boundary = HeatmapCellBoundary(
            top = boundary.top,
            right = boundary.right,
            bottom = boundary.bottom,
            left = boundary.left
        )
    )
}

private fun CalendarHeatmapMonthMetadata.toAndroidHeatmapMonthMetadata(): HeatmapMonthMetadata {
    return HeatmapMonthMetadata(
        month = month.toJavaYearMonth(),
        startDate = startDate.toJavaLocalDate(),
        endDate = endDate.toJavaLocalDate(),
        totalMessageCount = totalMessageCount,
        startWeekIndex = startWeekIndex,
        endWeekIndex = endWeekIndex,
        startRow = startRow,
        endRow = endRow,
        weekSpan = weekSpan
    )
}

private fun LocalDate.toKotlinLocalDate(): kotlinx.datetime.LocalDate {
    return kotlinx.datetime.LocalDate(year, monthValue, dayOfMonth)
}

private fun kotlinx.datetime.LocalDate.toJavaLocalDate(): LocalDate {
    return LocalDate.of(year, month.ordinal + 1, day)
}

private fun CalendarMonth.toJavaYearMonth(): YearMonth {
    return YearMonth.of(year, monthNumber)
}
