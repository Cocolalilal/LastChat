package me.rerere.rikkahub.ui.pages.menu

import me.rerere.rikkahub.ui.theme.LocalDarkMode

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Chat
import androidx.compose.material.icons.rounded.Message
import androidx.compose.material.icons.rounded.Input
import androidx.compose.material.icons.rounded.Output
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.RocketLaunch
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.rikkahub.R
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.hooks.HapticPattern
import me.rerere.rikkahub.ui.hooks.rememberPremiumHaptics
import me.rerere.rikkahub.utils.plus
import org.koin.androidx.compose.koinViewModel
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun MenuPage() {
    val vm: MenuVM = koinViewModel()
    val stats by vm.stats.collectAsStateWithLifecycle()
    val currentAssistant by vm.currentAssistant.collectAsStateWithLifecycle()

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
            // Chat Heatmap
            item {
                ChatHeatmapCard(
                    heatmapData = stats.heatmapData,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Streak + Chat Style Row
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
                    val (timeLabelText, timeLabelIcon) = when (stats.timeLabel) {
                        TimeLabel.EARLY_BIRD -> "Early Bird" to Icons.Rounded.WbSunny
                        TimeLabel.DAYTIME_CHATTER -> "Day Chatter" to Icons.Rounded.WbSunny
                        TimeLabel.NIGHT_OWL -> "Night Owl" to Icons.Rounded.NightsStay
                    }
                    StatCard(
                        title = "Chat Style",
                        value = timeLabelText,
                        icon = timeLabelIcon,
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                }
            }

            // Total Conversations + Total Messages Row
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatCard(
                        title = "Conversations",
                        value = formatCount(stats.usageStats.totalConversations),
                        icon = Icons.Rounded.Chat,
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                    StatCard(
                        title = "Messages",
                        value = formatCount(stats.usageStats.totalMessages),
                        icon = Icons.Rounded.Message,
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                }
            }

            // Input Tokens + Output Tokens Row
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatCard(
                        title = "Input Tokens",
                        value = formatTokenCount(stats.usageStats.inputTokens),
                        icon = Icons.Rounded.Input,
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
                }
            }

            // Cached Tokens + App Launches Row
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
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
                    StatCard(
                        title = "App Launches",
                        value = formatCount(stats.usageStats.appLaunches),
                        icon = Icons.Rounded.RocketLaunch,
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                }
            }


            // Weekly Messages Graph
            item {
                WeeklyMessagesCard(
                    weeklyMessages = stats.weeklyMessages,
                    thisWeekMessageCount = stats.thisWeekMessageCount,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Bottom spacer
            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

// ===== Heatmap Card =====

@Composable
private fun ChatHeatmapCard(
    heatmapData: List<HeatmapDay>,
    modifier: Modifier = Modifier
) {
    val containerColor = if (LocalDarkMode.current) {
        MaterialTheme.colorScheme.surfaceContainerLow
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val contentColor = MaterialTheme.colorScheme.onSurface
    val heatmapBaseColor = MaterialTheme.colorScheme.primary

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
            // Title
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Activity",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                if (heatmapData.isNotEmpty()) {
                    val totalDays = heatmapData.count { it.count > 0 }
                    Text(
                        text = "$totalDays active days",
                        style = MaterialTheme.typography.labelSmall,
                        color = contentColor.copy(alpha = 0.6f)
                    )
                }
            }

            // Heatmap grid
            if (heatmapData.isNotEmpty()) {
                val maxCount = heatmapData.maxOfOrNull { it.count }?.coerceAtLeast(1) ?: 1
                val cellSize = 12.dp
                val cellSpacing = 3.dp
                val emptyColor = if (LocalDarkMode.current) {
                    contentColor.copy(alpha = 0.08f)
                } else {
                    contentColor.copy(alpha = 0.06f)
                }

                // Group by weeks (columns) with 7 rows each (Mon-Sun)
                val today = LocalDate.now()
                val startDate = if (heatmapData.isNotEmpty()) heatmapData.first().date else today.minusMonths(5)
                
                // Build a map for quick lookup
                val dataMap = heatmapData.associate { it.date to it.count }

                // Find the Monday on or before startDate
                val firstMonday = if (startDate.dayOfWeek == DayOfWeek.MONDAY) startDate
                    else startDate.minusDays((startDate.dayOfWeek.value - 1).toLong())

                // Build weeks
                val weeks = mutableListOf<List<Pair<LocalDate, Int>>>()
                var currentMonday = firstMonday
                while (!currentMonday.isAfter(today)) {
                    val week = (0..6).map { dayOffset ->
                        val date = currentMonday.plusDays(dayOffset.toLong())
                        date to (if (date.isAfter(today)) -1 else (dataMap[date] ?: 0))
                    }
                    weeks.add(week)
                    currentMonday = currentMonday.plusWeeks(1)
                }

                // Month labels
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Start
                ) {
                    // Space for day labels
                    Spacer(modifier = Modifier.width(20.dp))
                    
                    // Calculate month label positions
                    val monthLabels = mutableListOf<Pair<Int, String>>()
                    var lastMonth = -1
                    weeks.forEachIndexed { weekIndex, week ->
                        val firstDay = week.firstOrNull()?.first
                        if (firstDay != null && firstDay.monthValue != lastMonth) {
                            lastMonth = firstDay.monthValue
                            monthLabels.add(weekIndex to firstDay.month.getDisplayName(TextStyle.SHORT, Locale.getDefault()))
                        }
                    }

                    // Draw month labels positioned at their week column
                    if (weeks.isNotEmpty()) {
                        val totalWidth = weeks.size * (cellSize + cellSpacing).value
                        Box(modifier = Modifier.fillMaxWidth()) {
                            monthLabels.forEach { (weekIndex, label) ->
                                val xFraction = weekIndex.toFloat() / weeks.size.coerceAtLeast(1)
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = contentColor.copy(alpha = 0.5f),
                                    fontSize = 9.sp,
                                    modifier = Modifier.padding(start = (xFraction * totalWidth).dp.coerceAtLeast(0.dp))
                                )
                            }
                        }
                    }
                }

                // Heatmap grid: 7 rows x N columns
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Start
                ) {
                    // Day of week labels (Mon, Wed, Fri)
                    Column(
                        modifier = Modifier.width(20.dp),
                        verticalArrangement = Arrangement.spacedBy(cellSpacing)
                    ) {
                        (0..6).forEach { dayIndex ->
                            val label = when (dayIndex) {
                                0 -> "M"
                                2 -> "W"
                                4 -> "F"
                                else -> ""
                            }
                            Box(
                                modifier = Modifier.size(cellSize),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = contentColor.copy(alpha = 0.4f),
                                    fontSize = 8.sp
                                )
                            }
                        }
                    }

                    // Draw heatmap cells
                    Canvas(
                        modifier = Modifier
                            .weight(1f)
                            .height((cellSize + cellSpacing) * 7 - cellSpacing)
                    ) {
                        val cellSizePx = cellSize.toPx()
                        val cellSpacingPx = cellSpacing.toPx()
                        val totalCellWidth = cellSizePx + cellSpacingPx
                        
                        // Calculate cell width to fit all weeks
                        val availableWidth = size.width
                        val adjustedCellWidth = if (weeks.isNotEmpty()) {
                            (availableWidth / weeks.size).coerceAtMost(totalCellWidth)
                        } else totalCellWidth
                        val adjustedCellSize = (adjustedCellWidth - cellSpacingPx).coerceAtLeast(2f)

                        weeks.forEachIndexed { colIndex, week ->
                            week.forEachIndexed { rowIndex, (_, count) ->
                                if (count >= 0) { // Skip future dates
                                    val color = if (count == 0) {
                                        emptyColor
                                    } else {
                                        val intensity = (count.toFloat() / maxCount).coerceIn(0.2f, 1f)
                                        heatmapBaseColor.copy(alpha = intensity)
                                    }
                                    drawRoundRect(
                                        color = color,
                                        topLeft = Offset(
                                            x = colIndex * adjustedCellWidth,
                                            y = rowIndex * (adjustedCellSize + cellSpacingPx)
                                        ),
                                        size = Size(adjustedCellSize, adjustedCellSize),
                                        cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
                                    )
                                }
                            }
                        }
                    }
                }

                // Legend
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Less",
                        style = MaterialTheme.typography.labelSmall,
                        color = contentColor.copy(alpha = 0.5f),
                        fontSize = 9.sp
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
                        color = contentColor.copy(alpha = 0.5f),
                        fontSize = 9.sp
                    )
                }
            }
        }
    }
}

// ===== Stat Card =====

@Composable
private fun StatCard(
    title: String,
    value: String,
    icon: ImageVector,
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
            Surface(
                color = contentColor.copy(alpha = 0.15f),
                contentColor = contentColor,
                shape = me.rerere.rikkahub.ui.theme.AppShapes.Chip,
                modifier = Modifier.size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, null, modifier = Modifier.size(18.dp))
                }
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


// ===== Weekly Messages Card =====

@Composable
private fun WeeklyMessagesCard(
    weeklyMessages: List<DayMessages>,
    thisWeekMessageCount: Int,
    modifier: Modifier = Modifier
) {
    val totalMessages = weeklyMessages.sumOf { it.count }
    val dailyAverage = if (weeklyMessages.isNotEmpty()) totalMessages / weeklyMessages.size else 0
    val maxCount = weeklyMessages.maxOfOrNull { it.count } ?: 1
    
    val containerColor = if (LocalDarkMode.current) {
        MaterialTheme.colorScheme.surfaceContainerLow
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val contentColor = MaterialTheme.colorScheme.onSurface
    val barColor = contentColor.copy(alpha = 0.55f)
    val barHighlightColor = contentColor
    
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "This Week",
                        style = MaterialTheme.typography.labelMedium,
                        color = contentColor.copy(alpha = 0.7f)
                    )
                    Text(
                        text = "$thisWeekMessageCount messages",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "~$dailyAverage/day",
                        style = MaterialTheme.typography.labelMedium,
                        color = contentColor.copy(alpha = 0.6f)
                    )
                }
            }
            
            // Bar chart
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                weeklyMessages.forEach { day ->
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // Bar
                        val barFraction = if (maxCount > 0) day.count.toFloat() / maxCount else 0f
                        val isToday = day == weeklyMessages.lastOrNull()
                        val currentBarColor = if (isToday) barHighlightColor else barColor
                        
                        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                            Canvas(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth(0.7f)
                                    .fillMaxHeight(barFraction.coerceAtLeast(0.04f))
                            ) {
                                drawRoundRect(
                                    color = currentBarColor,
                                    cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
                                    size = size
                                )
                            }
                        }
                        
                        // Day label
                        Text(
                            text = day.dayLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (day.isWeekend) {
                                MaterialTheme.colorScheme.error
                            } else if (isToday) {
                                contentColor
                            } else {
                                contentColor.copy(alpha = 0.6f)
                            },
                            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }
    }
}

// ===== Helper Functions =====

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
