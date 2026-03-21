package me.rerere.rikkahub.ui.pages.menu

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.hooks.HapticPattern
import me.rerere.rikkahub.ui.hooks.rememberPremiumHaptics
import me.rerere.rikkahub.ui.modifier.shimmer
import me.rerere.rikkahub.ui.motion.LocalMotionPolicy
import me.rerere.rikkahub.ui.theme.LocalDarkMode
import me.rerere.rikkahub.utils.plus
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

private val StatCardMinHeight = 136.dp
private val HeatmapCardMinHeight = 284.dp

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
                        text = "Statistics",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
            )
        },
    ) { innerPadding ->
        when (val state = uiState) {
            MenuUiState.Loading -> MenuLoadingContent(innerPadding = innerPadding)
            is MenuUiState.Ready -> MenuLoadedContent(
                stats = state.stats,
                showEmptyActivity = false,
                innerPadding = innerPadding
            )
            is MenuUiState.Empty -> MenuLoadedContent(
                stats = state.stats,
                showEmptyActivity = true,
                innerPadding = innerPadding
            )
        }
    }
}

@Composable
private fun MenuLoadedContent(
    stats: MenuStats,
    showEmptyActivity: Boolean,
    innerPadding: PaddingValues
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = innerPadding + PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            ChatHeatmapCard(
                heatmapData = stats.heatmapData,
                showEmptyState = showEmptyActivity,
                modifier = Modifier.fillMaxWidth()
            )
        }

        item {
            StatsRow {
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
            StatsRow {
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
            StatsRow {
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

@Composable
private fun MenuLoadingContent(innerPadding: PaddingValues) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = innerPadding + PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            ChatHeatmapSkeletonCard(modifier = Modifier.fillMaxWidth())
        }

        item {
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
        }

        item {
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
        }

        item {
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

private data class HeatmapRevealState(
    val titleAlpha: Float,
    val summaryAlpha: Float,
    val monthChipsAlpha: Float,
    val rowAlphas: List<Float>,
    val legendAlpha: Float
) {
    fun rowAlpha(index: Int): Float = rowAlphas.getOrElse(index) { 1f }
}

@Composable
private fun rememberHeatmapRevealState(revealKey: Any): HeatmapRevealState {
    val motionPolicy = LocalMotionPolicy.current
    val titleAlpha = remember(revealKey) { Animatable(if (motionPolicy.reduceMotion) 1f else 0f) }
    val summaryAlpha = remember(revealKey) { Animatable(if (motionPolicy.reduceMotion) 1f else 0f) }
    val monthChipsAlpha = remember(revealKey) { Animatable(if (motionPolicy.reduceMotion) 1f else 0f) }
    val rowAlphas = remember(revealKey) {
        List(7) { Animatable(if (motionPolicy.reduceMotion) 1f else 0f) }
    }
    val legendAlpha = remember(revealKey) { Animatable(if (motionPolicy.reduceMotion) 1f else 0f) }

    LaunchedEffect(revealKey, motionPolicy.reduceMotion) {
        if (motionPolicy.reduceMotion) {
            titleAlpha.snapTo(1f)
            summaryAlpha.snapTo(1f)
            monthChipsAlpha.snapTo(1f)
            rowAlphas.forEach { it.snapTo(1f) }
            legendAlpha.snapTo(1f)
            return@LaunchedEffect
        }

        titleAlpha.snapTo(0f)
        summaryAlpha.snapTo(0f)
        monthChipsAlpha.snapTo(0f)
        rowAlphas.forEach { it.snapTo(0f) }
        legendAlpha.snapTo(0f)

        coroutineScope {
            launch {
                titleAlpha.animateTo(1f, animationSpec = tween(durationMillis = 80))
            }
            launch {
                summaryAlpha.animateTo(
                    1f,
                    animationSpec = tween(durationMillis = 90, delayMillis = 24)
                )
            }
            launch {
                monthChipsAlpha.animateTo(
                    1f,
                    animationSpec = tween(durationMillis = 90, delayMillis = 48)
                )
            }
            rowAlphas.forEachIndexed { index, animatable ->
                launch {
                    animatable.animateTo(
                        1f,
                        animationSpec = tween(
                            durationMillis = 85,
                            delayMillis = 72 + (index * 18)
                        )
                    )
                }
            }
            launch {
                legendAlpha.animateTo(
                    1f,
                    animationSpec = tween(durationMillis = 90, delayMillis = 220)
                )
            }
        }
    }

    return HeatmapRevealState(
        titleAlpha = titleAlpha.value,
        summaryAlpha = summaryAlpha.value,
        monthChipsAlpha = monthChipsAlpha.value,
        rowAlphas = rowAlphas.map { it.value },
        legendAlpha = legendAlpha.value
    )
}

@Composable
private fun ChatHeatmapCard(
    heatmapData: List<HeatmapDay>,
    showEmptyState: Boolean,
    modifier: Modifier = Modifier
) {
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
        modifier = modifier.heightIn(min = HeatmapCardMinHeight)
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
                .heightIn(min = HeatmapCardMinHeight - 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val reveal = rememberHeatmapRevealState(revealKey = heatmapData)
            Text(
                text = "Activity",
                modifier = Modifier.graphicsLayer { alpha = reveal.titleAlpha },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            if (showEmptyState || heatmapData.isEmpty()) {
                Text(
                    text = "No activity yet",
                    style = MaterialTheme.typography.bodyMedium,
                    color = contentColor.copy(alpha = 0.7f)
                )
                return@Column
            }
            val today = LocalDate.now()
            val windowStart = remember(today) { today.withDayOfMonth(1).minusMonths(11) }
            val layout = remember(heatmapData, windowStart, today) {
                buildHeatmapLayout(
                    heatmapData = heatmapData,
                    windowStart = windowStart,
                    windowEnd = today
                )
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
            val currentMonth = YearMonth.from(today)
            var selectedMonth by remember(layout.months, currentMonth) {
                mutableStateOf(
                    layout.months.firstOrNull { it.month == currentMonth }?.month
                        ?: layout.months.lastOrNull()?.month
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
                "${it.month.month.getDisplayName(TextStyle.FULL, Locale.getDefault())} ${it.month.year}"
            } ?: "Activity Timeline"
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
                        monthSpacing = monthSpacing
                    ).roundToPx()
                }
                scrollState.scrollTo(targetOffset.coerceAtMost(scrollState.maxValue))
            }
            val selectMonth: (YearMonth) -> Unit = remember(layout.months, selectedMonth) {
                { month ->
                    if (layout.months.any { it.month == month } && selectedMonth != month) {
                        haptics.perform(HapticPattern.Pop)
                        selectedMonth = month
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer { alpha = reveal.summaryAlpha },
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
                    listOf("Mon", "", "Wed", "", "Fri", "", "Sun").forEachIndexed { index, label ->
                        Box(
                            modifier = Modifier
                                .height(cellSize)
                                .graphicsLayer { alpha = reveal.rowAlpha(index) },
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

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(scrollState),
                    verticalArrangement = Arrangement.spacedBy(cellSpacing)
                ) {
                    Row(
                        modifier = Modifier.graphicsLayer { alpha = reveal.monthChipsAlpha },
                        horizontalArrangement = Arrangement.spacedBy(monthSpacing)
                    ) {
                        layout.months.forEach { month ->
                            val isSelected = month.month == selectedMonth
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
                                    .clickable { selectMonth(month.month) }
                                    .padding(horizontal = 2.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(999.dp))
                                        .background(
                                            if (isSelected) contentColor.copy(alpha = 0.12f) else Color.Transparent
                                        )
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = month.month.month.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                        color = if (isSelected) contentColor else contentColor.copy(alpha = 0.55f)
                                    )
                                }
                            }
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(monthSpacing)
                    ) {
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
                                            val color = when {
                                                !isMonthCell -> Color.Transparent
                                                cell.count == 0 -> emptyColor
                                                else -> {
                                                    val intensity = (cell.count.toFloat() / maxCount).coerceIn(0.2f, 1f)
                                                    heatmapBaseColor.copy(alpha = intensity)
                                                }
                                            }
                                            val boundaryColor = when {
                                                !isMonthCell -> Color.Transparent
                                                else -> contentColor.copy(alpha = 0.12f)
                                            }

                                            Box(
                                                modifier = Modifier
                                                    .graphicsLayer { alpha = reveal.rowAlpha(cell.dayIndex) }
                                                    .size(cellSize)
                                                    .clip(RoundedCornerShape(3.dp))
                                                    .background(color)
                                                    .drawBehind {
                                                        if (!isMonthCell || boundaryColor.alpha <= 0f) return@drawBehind
                                                        val strokeWidth = 1.dp.toPx()
                                                        if (cell.boundary.top) {
                                                            drawLine(
                                                                color = boundaryColor,
                                                                start = androidx.compose.ui.geometry.Offset(0f, 0f),
                                                                end = androidx.compose.ui.geometry.Offset(size.width, 0f),
                                                                strokeWidth = strokeWidth
                                                            )
                                                        }
                                                        if (cell.boundary.right) {
                                                            drawLine(
                                                                color = boundaryColor,
                                                                start = androidx.compose.ui.geometry.Offset(size.width, 0f),
                                                                end = androidx.compose.ui.geometry.Offset(size.width, size.height),
                                                                strokeWidth = strokeWidth
                                                            )
                                                        }
                                                        if (cell.boundary.bottom) {
                                                            drawLine(
                                                                color = boundaryColor,
                                                                start = androidx.compose.ui.geometry.Offset(0f, size.height),
                                                                end = androidx.compose.ui.geometry.Offset(size.width, size.height),
                                                                strokeWidth = strokeWidth
                                                            )
                                                        }
                                                        if (cell.boundary.left) {
                                                            drawLine(
                                                                color = boundaryColor,
                                                                start = androidx.compose.ui.geometry.Offset(0f, 0f),
                                                                end = androidx.compose.ui.geometry.Offset(0f, size.height),
                                                                strokeWidth = strokeWidth
                                                            )
                                                        }
                                                    }
                                                    .then(
                                                        if (isMonthCell) {
                                                            Modifier.clickable { cell.month?.let(selectMonth) }
                                                        } else {
                                                            Modifier
                                                        }
                                                    )
                                                    .then(
                                                        if (cell.date == today && isMonthCell) {
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
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer { alpha = reveal.legendAlpha },
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

private fun heatmapWidthForWeeks(
    weekCount: Int,
    cellSize: Dp,
    cellSpacing: Dp
): Dp {
    if (weekCount <= 0) return 0.dp
    return (cellSize * weekCount) + (cellSpacing * (weekCount - 1))
}

private fun monthSectionOffset(
    months: List<HeatmapMonthMetadata>,
    targetIndex: Int,
    cellSize: Dp,
    cellSpacing: Dp,
    monthSpacing: Dp
): Dp {
    if (targetIndex <= 0) return 0.dp
    return months.take(targetIndex).fold(0.dp) { acc, month ->
        acc + heatmapWidthForWeeks(month.weekSpan, cellSize, cellSpacing) + monthSpacing
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
