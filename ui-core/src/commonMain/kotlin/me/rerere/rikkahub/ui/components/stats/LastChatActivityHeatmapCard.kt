package me.rerere.rikkahub.ui.components.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.datetime.LocalDate
import me.rerere.common.calendar.CalendarHeatmapDay
import me.rerere.common.calendar.CalendarHeatmapMonthMetadata
import me.rerere.common.calendar.CalendarMonth
import me.rerere.common.calendar.buildCalendarHeatmapLayout
import me.rerere.rikkahub.ui.theme.AppShapes

private val HeatmapCardMinHeight = 264.dp

/**
 * The production LastChat activity card shared by Android and iOS.
 *
 * All user-facing strings and month names are supplied by the host so Android keeps its existing
 * resource localization while iOS can use its native locale layer without forking the visual tree.
 */
@Composable
fun LastChatActivityHeatmapCard(
    heatmapData: List<CalendarHeatmapDay>,
    today: LocalDate,
    activityTitle: String,
    emptyText: String,
    weekdayLabels: List<String>,
    lessLabel: String,
    moreLabel: String,
    fallbackMonthLabel: String,
    monthName: (month: CalendarMonth, abbreviated: Boolean) -> String,
    messageCountText: @Composable (count: Long) -> String,
    showEmptyState: Boolean,
    darkTheme: Boolean,
    onMonthSelected: () -> Unit,
    modifier: Modifier = Modifier,
) {
    require(weekdayLabels.size == 7) { "weekdayLabels must contain seven entries" }
    val containerColor = if (darkTheme) {
        MaterialTheme.colorScheme.surfaceContainerLow
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val contentColor = MaterialTheme.colorScheme.onSurface
    val heatmapBaseColor = MaterialTheme.colorScheme.primary

    Card(
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = contentColor,
        ),
        shape = AppShapes.CardMedium,
        modifier = modifier.heightIn(min = HeatmapCardMinHeight),
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
                .heightIn(min = HeatmapCardMinHeight - 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = activityTitle,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )

            if (showEmptyState || heatmapData.isEmpty()) {
                Text(
                    text = emptyText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = contentColor.copy(alpha = 0.7f),
                )
                return@Column
            }

            val windowStart = remember(today) {
                CalendarMonth.from(today).plusMonths(-11).atDay(1)
            }
            val layout = remember(heatmapData, windowStart, today) {
                buildCalendarHeatmapLayout(
                    heatmapData = heatmapData,
                    windowStart = windowStart,
                    windowEnd = today,
                )
            }
            val maxCount = remember(heatmapData) {
                heatmapData.maxOfOrNull { it.count }?.coerceAtLeast(1) ?: 1
            }
            val cellSize = 12.dp
            val cellSpacing = 4.dp
            val headerHeight = 16.dp
            val monthSpacing = 12.dp
            val emptyColor = if (darkTheme) {
                contentColor.copy(alpha = 0.10f)
            } else {
                contentColor.copy(alpha = 0.08f)
            }
            val todayOutlineColor = contentColor.copy(alpha = 0.45f)
            val currentMonth = CalendarMonth.from(today)
            var selectedMonth by remember(layout.months, currentMonth) {
                mutableStateOf(
                    layout.months.firstOrNull { it.month == currentMonth }?.month
                        ?: layout.months.lastOrNull()?.month,
                )
            }
            LaunchedEffect(layout.months, currentMonth) {
                if (selectedMonth == null || layout.months.none { it.month == selectedMonth }) {
                    selectedMonth = layout.months.firstOrNull { it.month == currentMonth }?.month
                        ?: layout.months.lastOrNull()?.month
                }
            }
            val selectedMonthMetadata = remember(layout.months, selectedMonth) {
                layout.months.firstOrNull { it.month == selectedMonth }
            }
            val selectedMonthLabel = selectedMonthMetadata?.let {
                "${monthName(it.month, false)} ${it.month.year}"
            } ?: fallbackMonthLabel
            val selectedMonthCount = selectedMonthMetadata?.totalMessageCount?.toLong() ?: 0L
            val scrollState = rememberScrollState()
            val density = LocalDensity.current
            LaunchedEffect(layout.months, scrollState.maxValue, cellSize, cellSpacing, monthSpacing) {
                val targetMonthIndex = layout.months.indexOfFirst { it.month == currentMonth }
                if (targetMonthIndex < 0) return@LaunchedEffect
                val targetOffset = with(density) {
                    monthSectionOffset(
                        months = layout.months,
                        targetIndex = targetMonthIndex,
                        cellSize = cellSize,
                        cellSpacing = cellSpacing,
                        monthSpacing = monthSpacing,
                    ).roundToPx()
                }
                scrollState.scrollTo(targetOffset.coerceAtMost(scrollState.maxValue))
            }
            val selectMonth: (CalendarMonth) -> Unit = remember(layout.months, selectedMonth) {
                { month ->
                    if (layout.months.any { it.month == month } && selectedMonth != month) {
                        onMonthSelected()
                        selectedMonth = month
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = selectedMonthLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = contentColor.copy(alpha = 0.7f),
                )
                Text(
                    text = messageCountText(selectedMonthCount),
                    style = MaterialTheme.typography.labelSmall,
                    color = contentColor.copy(alpha = 0.6f),
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(
                    modifier = Modifier.padding(top = headerHeight + cellSpacing),
                    verticalArrangement = Arrangement.spacedBy(cellSpacing),
                ) {
                    weekdayLabels.forEach { label ->
                        Box(
                            modifier = Modifier.height(cellSize),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall,
                                color = contentColor.copy(alpha = 0.45f),
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(scrollState)
                        .padding(end = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(cellSpacing),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(monthSpacing)) {
                        layout.months.forEach { month ->
                            val isSelected = month.month == selectedMonth
                            Box(
                                modifier = Modifier
                                    .width(
                                        heatmapWidthForWeeks(
                                            weekCount = month.weekSpan,
                                            cellSize = cellSize,
                                            cellSpacing = cellSpacing,
                                        ),
                                    )
                                    .height(headerHeight)
                                    .clickable { selectMonth(month.month) }
                                    .padding(horizontal = 2.dp),
                                contentAlignment = Alignment.CenterStart,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .wrapContentWidth(unbounded = true, align = Alignment.Start)
                                        .clip(RoundedCornerShape(999.dp))
                                        .background(
                                            if (isSelected) contentColor.copy(alpha = 0.12f) else Color.Transparent,
                                        )
                                        .padding(horizontal = 8.dp, vertical = 2.dp),
                                ) {
                                    Text(
                                        text = monthName(month.month, true),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                        color = if (isSelected) contentColor else contentColor.copy(alpha = 0.55f),
                                    )
                                }
                            }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(monthSpacing)) {
                        layout.months.forEach { month ->
                            val monthWeeks = layout.weeks.subList(month.startWeekIndex, month.endWeekIndex + 1)
                            Row(
                                modifier = Modifier.width(
                                    heatmapWidthForWeeks(
                                        weekCount = month.weekSpan,
                                        cellSize = cellSize,
                                        cellSpacing = cellSpacing,
                                    ),
                                ),
                                horizontalArrangement = Arrangement.spacedBy(cellSpacing),
                            ) {
                                monthWeeks.forEach { week ->
                                    Column(
                                        modifier = Modifier.width(cellSize),
                                        verticalArrangement = Arrangement.spacedBy(cellSpacing),
                                    ) {
                                        week.cells.forEach { cell ->
                                            val isMonthCell = cell.isInWindow && cell.month == month.month
                                            val color = when {
                                                !isMonthCell -> Color.Transparent
                                                cell.count == 0 -> emptyColor
                                                else -> {
                                                    val intensity = (cell.count.toFloat() / maxCount).coerceIn(0.2f, 1f)
                                                    heatmapBaseColor.copy(alpha = intensity)
                                                }
                                            }
                                            val boundaryColor = if (isMonthCell) {
                                                contentColor.copy(alpha = 0.12f)
                                            } else {
                                                Color.Transparent
                                            }

                                            Box(
                                                modifier = Modifier
                                                    .size(cellSize)
                                                    .clip(RoundedCornerShape(3.dp))
                                                    .background(color)
                                                    .drawBehind {
                                                        if (!isMonthCell || boundaryColor.alpha <= 0f) return@drawBehind
                                                        val strokeWidth = 1.dp.toPx()
                                                        if (cell.boundary.top) {
                                                            drawLine(boundaryColor, Offset.Zero, Offset(size.width, 0f), strokeWidth)
                                                        }
                                                        if (cell.boundary.right) {
                                                            drawLine(
                                                                boundaryColor,
                                                                Offset(size.width, 0f),
                                                                Offset(size.width, size.height),
                                                                strokeWidth,
                                                            )
                                                        }
                                                        if (cell.boundary.bottom) {
                                                            drawLine(
                                                                boundaryColor,
                                                                Offset(0f, size.height),
                                                                Offset(size.width, size.height),
                                                                strokeWidth,
                                                            )
                                                        }
                                                        if (cell.boundary.left) {
                                                            drawLine(boundaryColor, Offset.Zero, Offset(0f, size.height), strokeWidth)
                                                        }
                                                    }
                                                    .then(
                                                        if (isMonthCell) {
                                                            Modifier.clickable { cell.month?.let(selectMonth) }
                                                        } else {
                                                            Modifier
                                                        },
                                                    )
                                                    .then(
                                                        if (cell.date == today && isMonthCell) {
                                                            Modifier.border(
                                                                1.dp,
                                                                todayOutlineColor,
                                                                RoundedCornerShape(3.dp),
                                                            )
                                                        } else {
                                                            Modifier
                                                        },
                                                    ),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = lessLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = contentColor.copy(alpha = 0.5f),
                )
                Spacer(modifier = Modifier.width(4.dp))
                listOf(0f, 0.25f, 0.5f, 0.75f, 1f).forEach { level ->
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(
                                if (level == 0f) emptyColor
                                else heatmapBaseColor.copy(alpha = level.coerceAtLeast(0.2f)),
                            ),
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                }
                Text(
                    text = moreLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = contentColor.copy(alpha = 0.5f),
                )
            }
        }
    }
}

private fun heatmapWidthForWeeks(
    weekCount: Int,
    cellSize: Dp,
    cellSpacing: Dp,
): Dp {
    if (weekCount <= 0) return 0.dp
    return (cellSize * weekCount) + (cellSpacing * (weekCount - 1))
}

private fun monthSectionOffset(
    months: List<CalendarHeatmapMonthMetadata>,
    targetIndex: Int,
    cellSize: Dp,
    cellSpacing: Dp,
    monthSpacing: Dp,
): Dp {
    if (targetIndex <= 0) return 0.dp
    return months.take(targetIndex).fold(0.dp) { acc, month ->
        acc + heatmapWidthForWeeks(month.weekSpan, cellSize, cellSpacing) + monthSpacing
    }
}
