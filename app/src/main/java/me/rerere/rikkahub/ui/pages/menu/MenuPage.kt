package me.rerere.rikkahub.ui.pages.menu

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.stats.LastChatStatCard
import me.rerere.rikkahub.ui.components.stats.LastChatActivityHeatmapCard
import me.rerere.rikkahub.ui.components.stats.LastChatStatIcon
import me.rerere.rikkahub.ui.hooks.HapticPattern
import me.rerere.rikkahub.utils.currentAppLocale
import me.rerere.rikkahub.ui.hooks.rememberPremiumHaptics
import me.rerere.rikkahub.ui.modifier.shimmer
import me.rerere.rikkahub.ui.motion.LocalMotionPolicy
import me.rerere.rikkahub.ui.theme.LocalDarkMode
import me.rerere.rikkahub.utils.plus
import me.rerere.rikkahub.R
import kotlinx.coroutines.delay
import me.rerere.common.calendar.CalendarHeatmapDay
import me.rerere.common.calendar.CalendarMonth
import org.koin.androidx.compose.koinViewModel
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle

private val StatCardMinHeight = 136.dp
private val HeatmapCardMinHeight = 264.dp

@Composable
fun MenuPage() {
    val vm: MenuVM = koinViewModel()
    val uiState by vm.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    BackButton()
                },
                title = {
                    Text(
                        text = stringResource(R.string.menu_statistics_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
            )
        },
    ) { innerPadding ->
        MenuStatsContent(
            uiState = uiState,
            innerPadding = innerPadding
        )
    }
}

@Composable
private fun MenuStatsContent(
    uiState: MenuUiState,
    innerPadding: PaddingValues
) {
    val loadedStats = when (uiState) {
        MenuUiState.Loading -> null
        is MenuUiState.Ready -> uiState.stats
        is MenuUiState.Empty -> uiState.stats
    }
    val showEmptyActivity = uiState is MenuUiState.Empty
    val revealStage = rememberStatsWidgetRevealStage(
        isLoading = uiState is MenuUiState.Loading,
        revealKey = uiState !is MenuUiState.Loading
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = innerPadding + PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            StatsWidgetCrossfade(
                showContent = loadedStats != null && revealStage >= 0,
                loadingContent = {
                    ChatHeatmapSkeletonCard(modifier = Modifier.fillMaxWidth())
                },
                loadedContent = {
                    val stats = checkNotNull(loadedStats)
                    ChatHeatmapCard(
                        heatmapData = stats.heatmapData,
                        showEmptyState = showEmptyActivity,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            )
        }

        item {
            StatsWidgetCrossfade(
                showContent = loadedStats != null && revealStage >= 1,
                loadingContent = {
                    StatsRow {
                        StatCardSkeleton(
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight()
                        )
                    }
                },
                loadedContent = {
                    val stats = checkNotNull(loadedStats)
                    StatsRow {
                        StatCard(
                            title = stringResource(R.string.menu_stat_conversations),
                            value = formatCount(stats.usageStats.totalConversations),
                            icon = LastChatStatIcon.Conversations,
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight()
                        )
                    }
                }
            )
        }

        item {
            StatsWidgetCrossfade(
                showContent = loadedStats != null && revealStage >= 2,
                loadingContent = {
                    StatsRow {
                        StatCardSkeleton(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        )
                        StatCardSkeleton(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        )
                    }
                },
                loadedContent = {
                    val stats = checkNotNull(loadedStats)
                    StatsRow {
                        StatCard(
                            title = stringResource(R.string.menu_stat_messages),
                            value = formatCount(stats.usageStats.totalMessages),
                            icon = LastChatStatIcon.Messages,
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        )
                        StatCard(
                            title = stringResource(R.string.menu_stat_input_tokens),
                            value = formatTokenCount(stats.usageStats.inputTokens),
                            icon = LastChatStatIcon.InputTokens,
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
            )
        }

        item {
            StatsWidgetCrossfade(
                showContent = loadedStats != null && revealStage >= 3,
                loadingContent = {
                    StatsRow {
                        StatCardSkeleton(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        )
                        StatCardSkeleton(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        )
                    }
                },
                loadedContent = {
                    val stats = checkNotNull(loadedStats)
                    StatsRow {
                        StatCard(
                            title = stringResource(R.string.menu_stat_output_tokens),
                            value = formatTokenCount(stats.usageStats.outputTokens),
                            icon = LastChatStatIcon.OutputTokens,
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
                            title = stringResource(R.string.menu_stat_cached_tokens),
                            value = formatTokenCount(stats.usageStats.cachedTokens),
                            icon = LastChatStatIcon.CachedTokens,
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        )
                    }
                }
            )
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun StatsRow(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        content = content
    )
}

@Composable
private fun StatsWidgetCrossfade(
    showContent: Boolean,
    loadingContent: @Composable () -> Unit,
    loadedContent: @Composable () -> Unit
) {
    val motionPolicy = LocalMotionPolicy.current

    Crossfade(
        targetState = showContent,
        animationSpec = tween(
            durationMillis = if (motionPolicy.reduceMotion) 90 else 140
        ),
        label = "stats-widget-crossfade"
    ) { isLoaded ->
        if (isLoaded) {
            loadedContent()
        } else {
            loadingContent()
        }
    }
}

@Composable
private fun rememberStatsWidgetRevealStage(
    isLoading: Boolean,
    revealKey: Any
): Int {
    val motionPolicy = LocalMotionPolicy.current
    var revealStage by remember(revealKey, motionPolicy.reduceMotion) {
        mutableIntStateOf(if (isLoading) -1 else if (motionPolicy.reduceMotion) 3 else -1)
    }

    LaunchedEffect(isLoading, revealKey, motionPolicy.reduceMotion) {
        if (isLoading) {
            revealStage = -1
            return@LaunchedEffect
        }
        if (motionPolicy.reduceMotion) {
            revealStage = 3
            return@LaunchedEffect
        }

        revealStage = 0
        delay(36)
        revealStage = 1
        delay(36)
        revealStage = 2
        delay(36)
        revealStage = 3
    }

    return revealStage
}

@Composable
private fun ChatHeatmapSkeletonCard(modifier: Modifier = Modifier) {
    val containerColor = if (LocalDarkMode.current) {
        MaterialTheme.colorScheme.surfaceContainerLow
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val contentColor = MaterialTheme.colorScheme.onSurface
    val today = LocalDate.now()
    val windowStart = remember(today) { today.withDayOfMonth(1).minusMonths(11) }
    val layout = remember(today, windowStart) {
        buildHeatmapLayout(
            heatmapData = emptyList(),
            windowStart = windowStart,
            windowEnd = today
        )
    }
    val cellSize = 12.dp
    val cellSpacing = 4.dp
    val headerHeight = 16.dp
    val monthSpacing = 12.dp

    Card(
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = contentColor
        ),
        shape = me.rerere.rikkahub.ui.theme.AppShapes.CardMedium,
        modifier = modifier.heightIn(min = HeatmapCardMinHeight)
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
                .heightIn(min = HeatmapCardMinHeight - 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SkeletonBlock(
                modifier = Modifier
                    .width(92.dp)
                    .height(20.dp),
                color = contentColor
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SkeletonBlock(
                    modifier = Modifier
                        .width(134.dp)
                        .height(12.dp),
                    color = contentColor.copy(alpha = 0.85f)
                )
                SkeletonBlock(
                    modifier = Modifier
                        .width(78.dp)
                        .height(10.dp),
                    color = contentColor.copy(alpha = 0.7f)
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
                    listOf("Mon", "", "Wed", "", "Fri", "", "Sun").forEachIndexed { index, label ->
                        Box(
                            modifier = Modifier.height(cellSize),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            if (label.isBlank()) {
                                SkeletonBlock(
                                    modifier = Modifier
                                        .width(12.dp)
                                        .height(8.dp),
                                    color = contentColor.copy(alpha = 0.5f + (index * 0.04f))
                                )
                            } else {
                                SkeletonBlock(
                                    modifier = Modifier
                                        .width(22.dp)
                                        .height(10.dp),
                                    color = contentColor.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                }
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(cellSpacing)
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(monthSpacing)) {
                        layout.months.forEachIndexed { index, month ->
                            Box(
                                modifier = Modifier
                                    .width(
                                        heatmapWidthForWeeks(
                                            weekCount = month.weekSpan,
                                            cellSize = cellSize,
                                            cellSpacing = cellSpacing
                                        )
                                    )
                                    .height(headerHeight)
                                    .padding(horizontal = 2.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                SkeletonBlock(
                                    modifier = Modifier
                                        .width(
                                            when (index % 3) {
                                                0 -> 24.dp
                                                1 -> 28.dp
                                                else -> 32.dp
                                            }
                                        )
                                        .height(12.dp),
                                    color = contentColor.copy(alpha = 0.7f)
                                )
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
                                        cellSpacing = cellSpacing
                                    )
                                ),
                                horizontalArrangement = Arrangement.spacedBy(cellSpacing)
                            ) {
                                monthWeeks.forEach { week ->
                                    Column(
                                        modifier = Modifier.width(cellSize),
                                        verticalArrangement = Arrangement.spacedBy(cellSpacing)
                                    ) {
                                        week.cells.forEach { cell ->
                                            val isMonthCell = cell.isInWindow && cell.month == month.month
                                            if (!isMonthCell) {
                                                Spacer(modifier = Modifier.size(cellSize))
                                            } else {
                                                val placeholderAlpha = when ((cell.weekIndex + cell.dayIndex) % 5) {
                                                    0 -> 0.58f
                                                    1 -> 0.7f
                                                    2 -> 0.5f
                                                    3 -> 0.82f
                                                    else -> 0.64f
                                                }
                                                SkeletonBlock(
                                                    modifier = Modifier
                                                        .size(cellSize)
                                                        .clip(RoundedCornerShape(3.dp)),
                                                    color = contentColor.copy(alpha = placeholderAlpha)
                                                )
                                            }
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
                SkeletonBlock(
                    modifier = Modifier
                        .width(22.dp)
                        .height(10.dp),
                    color = contentColor.copy(alpha = 0.65f)
                )
                Spacer(modifier = Modifier.width(6.dp))
                repeat(5) { index ->
                    SkeletonBlock(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = contentColor.copy(alpha = 0.45f + (index * 0.09f))
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                }
                SkeletonBlock(
                    modifier = Modifier
                        .width(24.dp)
                        .height(10.dp),
                    color = contentColor.copy(alpha = 0.65f)
                )
            }
        }
    }
}

@Composable
private fun StatCardSkeleton(modifier: Modifier = Modifier) {
    val containerColor = if (LocalDarkMode.current) {
        MaterialTheme.colorScheme.surfaceContainerHigh
    } else {
        MaterialTheme.colorScheme.surfaceContainerHighest
    }
    val contentColor = MaterialTheme.colorScheme.onSurface

    Card(
        modifier = modifier.heightIn(min = StatCardMinHeight),
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
            SkeletonBlock(
                modifier = Modifier
                    .size(36.dp)
                    .clip(me.rerere.rikkahub.ui.theme.AppShapes.Chip),
                color = contentColor
            )
            Spacer(modifier = Modifier.height(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SkeletonBlock(
                    modifier = Modifier
                        .width(72.dp)
                        .height(26.dp),
                    color = contentColor
                )
                SkeletonBlock(
                    modifier = Modifier
                        .width(84.dp)
                        .height(12.dp),
                    color = contentColor.copy(alpha = 0.8f)
                )
            }
        }
    }
}

@Composable
private fun SkeletonBlock(
    modifier: Modifier,
    color: Color
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.16f))
            .shimmer(
                isLoading = true,
                shimmerColor = color.copy(alpha = 0.28f),
                backgroundColor = color.copy(alpha = 0.82f)
            )
    )
}

@Composable
private fun ChatHeatmapCard(
    heatmapData: List<HeatmapDay>,
    showEmptyState: Boolean,
    modifier: Modifier = Modifier
) {
    val haptics = rememberPremiumHaptics()
    val today = LocalDate.now()
    val activeLocale = currentAppLocale()
    LastChatActivityHeatmapCard(
        heatmapData = heatmapData.map { day ->
            CalendarHeatmapDay(
                date = kotlinx.datetime.LocalDate(day.date.year, day.date.monthValue, day.date.dayOfMonth),
                count = day.count,
            )
        },
        today = kotlinx.datetime.LocalDate(today.year, today.monthValue, today.dayOfMonth),
        activityTitle = stringResource(R.string.menu_activity_title),
        emptyText = stringResource(R.string.menu_activity_empty),
        weekdayLabels = listOf(
            stringResource(R.string.menu_weekday_monday),
            "",
            stringResource(R.string.menu_weekday_wednesday),
            "",
            stringResource(R.string.menu_weekday_friday),
            "",
            stringResource(R.string.menu_weekday_sunday),
        ),
        lessLabel = stringResource(R.string.menu_activity_less),
        moreLabel = stringResource(R.string.menu_activity_more),
        fallbackMonthLabel = stringResource(R.string.activity_timeline_title),
        monthName = { month: CalendarMonth, abbreviated: Boolean ->
            YearMonth.of(month.year, month.monthNumber).month.getDisplayName(
                if (abbreviated) TextStyle.SHORT else TextStyle.FULL,
                activeLocale,
            )
        },
        messageCountText = { count ->
            pluralStringResource(
                R.plurals.menu_messages_count,
                count.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                formatCount(count),
            )
        },
        showEmptyState = showEmptyState,
        darkTheme = LocalDarkMode.current,
        onMonthSelected = { haptics.perform(HapticPattern.Pop) },
        modifier = modifier,
    )
}

private fun heatmapWidthForWeeks(
    weekCount: Int,
    cellSize: Dp,
    cellSpacing: Dp,
): Dp {
    if (weekCount <= 0) return 0.dp
    return (cellSize * weekCount) + (cellSpacing * (weekCount - 1))
}

@Composable
private fun StatCard(
    title: String,
    value: String,
    icon: LastChatStatIcon,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
    subtitle: String? = null
) {
    LastChatStatCard(
        title = title,
        value = value,
        containerColor = containerColor,
        contentColor = contentColor,
        modifier = modifier,
        subtitle = subtitle,
        icon = { me.rerere.rikkahub.ui.components.stats.LastChatStatIconGlyph(icon) },
    )
}

private fun formatCount(count: Long): String {
    val locale = currentAppLocale()
    return when {
        count >= 1_000_000 -> String.format(locale, "%.1fM", count / 1_000_000.0)
        count >= 10_000 -> String.format(locale, "%.1fK", count / 1_000.0)
        count >= 1_000 -> String.format(locale, "%.1fK", count / 1_000.0)
        else -> count.toString()
    }
}

private fun formatTokenCount(count: Long): String {
    val locale = currentAppLocale()
    return when {
        count >= 1_000_000_000 -> String.format(locale, "%.1fB", count / 1_000_000_000.0)
        count >= 1_000_000 -> String.format(locale, "%.1fM", count / 1_000_000.0)
        count >= 1_000 -> String.format(locale, "%.1fK", count / 1_000.0)
        else -> count.toString()
    }
}
