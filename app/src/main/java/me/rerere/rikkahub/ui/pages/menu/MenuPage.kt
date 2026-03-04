package me.rerere.rikkahub.ui.pages.menu

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.automirrored.rounded.Input
import androidx.compose.material.icons.automirrored.rounded.Message
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Output
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.hooks.HapticPattern
import me.rerere.rikkahub.ui.hooks.rememberPremiumHaptics
import me.rerere.rikkahub.ui.theme.LocalDarkMode
import me.rerere.rikkahub.utils.plus
import org.koin.androidx.compose.koinViewModel
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale

@Composable
fun MenuPage() {
    val vm: MenuVM = koinViewModel()
    val stats by vm.stats.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    BackButton()
                },
                title = {
                    Text(
                        text = "Statistics",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
            )
        },
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = it + PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                ChatHeatmapCard(
                    heatmapData = stats.heatmapData,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatCard(
                        title = "Daily Streak",
                        value = "${stats.dailyChatStreak}",
                        subtitle = "days",
                        icon = Icons.Rounded.LocalFireDepartment,
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                    StatCard(
                        title = "Conversations",
                        value = formatCount(stats.usageStats.totalConversations),
                        icon = Icons.AutoMirrored.Rounded.Chat,
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatCard(
                        title = "Messages",
                        value = formatCount(stats.usageStats.totalMessages),
                        icon = Icons.AutoMirrored.Rounded.Message,
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                    StatCard(
                        title = "Input Tokens",
                        value = formatTokenCount(stats.usageStats.inputTokens),
                        icon = Icons.AutoMirrored.Rounded.Input,
                        containerColor = if (LocalDarkMode.current) {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHighest
                        },
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatCard(
                        title = "Output Tokens",
                        value = formatTokenCount(stats.usageStats.outputTokens),
                        icon = Icons.Rounded.Output,
                        containerColor = if (LocalDarkMode.current) {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHighest
                        },
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                    StatCard(
                        title = "Cached Tokens",
                        value = formatTokenCount(stats.usageStats.cachedTokens),
                        icon = Icons.Rounded.Savings,
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun ChatHeatmapCard(
    heatmapData: List<HeatmapDay>,
    modifier: Modifier = Modifier
) {
    data class MonthGroup(
        val month: YearMonth,
        val weeks: List<List<Pair<LocalDate, Int>>>,
        val messageCount: Int
    )

    val containerColor = if (LocalDarkMode.current) {
        MaterialTheme.colorScheme.surfaceContainerLow
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val contentColor = MaterialTheme.colorScheme.onSurface
    val heatmapBaseColor = MaterialTheme.colorScheme.primary
    val haptics = rememberPremiumHaptics()

    Card(
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = contentColor
        ),
        shape = me.rerere.rikkahub.ui.theme.AppShapes.CardMedium,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Activity",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            if (heatmapData.isEmpty()) {
                Text(
                    text = "No activity yet",
                    style = MaterialTheme.typography.bodyMedium,
                    color = contentColor.copy(alpha = 0.7f)
                )
                return@Column
            }

            val today = LocalDate.now()
            val startDate = remember(heatmapData) {
                heatmapData.firstOrNull()?.date?.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                    ?: today.minusMonths(11).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            }
            val dataMap = remember(heatmapData) { heatmapData.associate { it.date to it.count } }
            val weeks = remember(startDate, today, dataMap) {
                val list = mutableListOf<List<Pair<LocalDate, Int>>>()
                var currentMonday = startDate
                while (!currentMonday.isAfter(today)) {
                    list.add(
                        (0..6).map { dayOffset ->
                            val date = currentMonday.plusDays(dayOffset.toLong())
                            date to (if (date.isAfter(today)) -1 else (dataMap[date] ?: 0))
                        }
                    )
                    currentMonday = currentMonday.plusWeeks(1)
                }
                list
            }

            val maxCount = remember(heatmapData) {
                heatmapData.maxOfOrNull { it.count }?.coerceAtLeast(1) ?: 1
            }
            val cellSize = 12.dp
            val cellSpacing = 4.dp
            val headerHeight = 16.dp
            val monthSpacing = 12.dp
            val emptyColor = if (LocalDarkMode.current) {
                contentColor.copy(alpha = 0.10f)
            } else {
                contentColor.copy(alpha = 0.08f)
            }
            val todayOutlineColor = contentColor.copy(alpha = 0.45f)

            val monthGroups = remember(weeks, dataMap) {
                val weekAssignments = weeks.mapNotNull { week ->
                    val validDates = week.filter { (_, count) -> count >= 0 }.map { it.first }
                    if (validDates.isEmpty()) return@mapNotNull null

                    val primaryMonth = validDates
                        .groupingBy { YearMonth.from(it) }
                        .eachCount()
                        .maxByOrNull { it.value }
                        ?.key ?: YearMonth.from(validDates.first())

                    primaryMonth to week
                }

                val groupedWeeks = mutableListOf<Pair<YearMonth, MutableList<List<Pair<LocalDate, Int>>>>>()
                weekAssignments.forEach { (month, week) ->
                    val lastGroup = groupedWeeks.lastOrNull()
                    if (lastGroup != null && lastGroup.first == month) {
                        lastGroup.second.add(week)
                    } else {
                        groupedWeeks.add(month to mutableListOf(week))
                    }
                }

                groupedWeeks.map { (month, monthWeeks) ->
                    val monthMessageCount = dataMap.entries.sumOf { (date, count) ->
                        if (YearMonth.from(date) == month) count else 0
                    }
                    MonthGroup(
                        month = month,
                        weeks = monthWeeks.toList(),
                        messageCount = monthMessageCount
                    )
                }
            }

            val currentMonth = YearMonth.from(today)
            val currentMonthGroupIndex = remember(monthGroups, currentMonth) {
                monthGroups.indexOfFirst { it.month == currentMonth }.let { index ->
                    if (index >= 0) index else 0
                }
            }
            val monthListState = rememberLazyListState(
                initialFirstVisibleItemIndex = currentMonthGroupIndex
            )
            val selectedMonthIndexState = remember(monthGroups, currentMonthGroupIndex) {
                mutableStateOf(currentMonthGroupIndex)
            }
            val safeSelectedMonthIndex = if (monthGroups.isEmpty()) {
                -1
            } else {
                selectedMonthIndexState.value.coerceIn(0, monthGroups.lastIndex)
            }
            val selectedMonthGroup = monthGroups.getOrNull(safeSelectedMonthIndex)
            val selectedMonthLabel = selectedMonthGroup?.let {
                "${it.month.month.getDisplayName(TextStyle.FULL, Locale.getDefault())} ${it.month.year}"
            } ?: "Activity Timeline"
            val selectedMonthCount = selectedMonthGroup?.messageCount?.toLong() ?: 0L

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = selectedMonthLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = contentColor.copy(alpha = 0.7f)
                )
                Text(
                    text = "${formatCount(selectedMonthCount)} messages",
                    style = MaterialTheme.typography.labelSmall,
                    color = contentColor.copy(alpha = 0.6f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(top = headerHeight + cellSpacing),
                    verticalArrangement = Arrangement.spacedBy(cellSpacing)
                ) {
                    listOf("Mon", "", "Wed", "", "Fri", "", "Sun").forEach { label ->
                        Box(
                            modifier = Modifier.height(cellSize),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall,
                                color = contentColor.copy(alpha = 0.45f)
                            )
                        }
                    }
                }

                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    state = monthListState,
                    horizontalArrangement = Arrangement.spacedBy(monthSpacing)
                ) {
                    items(monthGroups.size) { groupIndex ->
                        val group = monthGroups[groupIndex]
                        val isSelected = groupIndex == safeSelectedMonthIndex
                        val groupWidth = if (group.weeks.isEmpty()) {
                            cellSize
                        } else {
                            (cellSize * group.weeks.size) + (cellSpacing * (group.weeks.size - 1))
                        }

                        Column(
                            modifier = Modifier.clickable {
                                haptics.perform(HapticPattern.Pop)
                                selectedMonthIndexState.value = groupIndex
                            },
                            verticalArrangement = Arrangement.spacedBy(cellSpacing)
                        ) {
                            Box(
                                modifier = Modifier
                                    .height(headerHeight)
                                    .width(groupWidth),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Text(
                                    text = group.month.month.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (isSelected) contentColor else contentColor.copy(alpha = 0.55f)
                                )
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(cellSpacing)) {
                                group.weeks.forEach { week ->
                                    Column(
                                        modifier = Modifier.width(cellSize),
                                        verticalArrangement = Arrangement.spacedBy(cellSpacing)
                                    ) {
                                        week.forEach { (date, count) ->
                                            val color = when {
                                                count < 0 -> Color.Transparent
                                                count == 0 -> emptyColor
                                                else -> {
                                                    val intensity = (count.toFloat() / maxCount).coerceIn(0.2f, 1f)
                                                    heatmapBaseColor.copy(alpha = intensity)
                                                }
                                            }
                                            Box(
                                                modifier = Modifier
                                                    .size(cellSize)
                                                    .clip(RoundedCornerShape(3.dp))
                                                    .background(color)
                                                    .then(
                                                        if (date == today && count >= 0) {
                                                            Modifier.border(1.dp, todayOutlineColor, RoundedCornerShape(3.dp))
                                                        } else {
                                                            Modifier
                                                        }
                                                    )
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
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Less",
                    style = MaterialTheme.typography.labelSmall,
                    color = contentColor.copy(alpha = 0.5f)
                )
                Spacer(modifier = Modifier.width(4.dp))
                listOf(0f, 0.25f, 0.5f, 0.75f, 1f).forEach { level ->
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(
                                if (level == 0f) emptyColor
                                else heatmapBaseColor.copy(alpha = level.coerceAtLeast(0.2f))
                            )
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                }
                Text(
                    text = "More",
                    style = MaterialTheme.typography.labelSmall,
                    color = contentColor.copy(alpha = 0.5f)
                )
            }
        }
    }
}

@Composable
private fun StatCard(
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
    subtitle: String? = null
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = contentColor
        ),
        shape = me.rerere.rikkahub.ui.theme.AppShapes.CardMedium
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(me.rerere.rikkahub.ui.theme.AppShapes.Chip)
                    .background(contentColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, modifier = Modifier.size(18.dp))
            }
            Spacer(modifier = Modifier.height(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = value,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    if (subtitle != null) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.labelMedium,
                            color = contentColor.copy(alpha = 0.7f),
                            modifier = Modifier.padding(bottom = 2.dp)
                        )
                    }
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = contentColor.copy(alpha = 0.7f)
                )
            }
        }
    }
}

private fun formatCount(count: Long): String {
    return when {
        count >= 1_000_000 -> String.format("%.1fM", count / 1_000_000.0)
        count >= 10_000 -> String.format("%.1fK", count / 1_000.0)
        count >= 1_000 -> String.format("%.1fK", count / 1_000.0)
        else -> count.toString()
    }
}

private fun formatTokenCount(count: Long): String {
    return when {
        count >= 1_000_000_000 -> String.format("%.1fB", count / 1_000_000_000.0)
        count >= 1_000_000 -> String.format("%.1fM", count / 1_000_000.0)
        count >= 1_000 -> String.format("%.1fK", count / 1_000.0)
        else -> count.toString()
    }
}
