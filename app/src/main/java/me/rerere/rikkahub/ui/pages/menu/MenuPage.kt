package me.rerere.rikkahub.ui.pages.menu

import me.rerere.rikkahub.ui.theme.LocalDarkMode

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.rikkahub.R
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.Greeting
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.hooks.HapticPattern
import me.rerere.rikkahub.ui.hooks.rememberPremiumHaptics
import me.rerere.rikkahub.utils.plus
import org.koin.androidx.compose.koinViewModel

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
                title = {},
            )
        },
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = it + PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            item {
                Greeting(
                    modifier = Modifier.padding(vertical = 16.dp),
                    style = MaterialTheme.typography.displayMedium,
                    assistant = currentAssistant
                )
            }

            item {
                StatsSection(stats)
            }
        }
    }
}

@Composable
private fun StatsSection(stats: MenuStats) {
    val navController = LocalNavController.current
    
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Daily Chat Streak (Full Width)
        StatCard(
            title = "Daily Chat Streak",
            value = "${stats.dailyChatStreak} Days",
            icon = Icons.Rounded.LocalFireDepartment,
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
        )

        // Chat Style & Image Gen (Row) - Chat Style moved to left, Image Gen on right
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Chat Style widget (moved from right to left, new color)
            val (timeLabelText, timeLabelIcon) = when (stats.timeLabel) {
                TimeLabel.EARLY_BIRD -> stringResource(R.string.time_label_early_bird) to Icons.Rounded.WbSunny
                TimeLabel.DAYTIME_CHATTER -> stringResource(R.string.time_label_daytime_chatter) to Icons.Rounded.WbSunny
                TimeLabel.NIGHT_OWL -> stringResource(R.string.time_label_night_owl) to Icons.Rounded.NightsStay
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
            
            // Image Gen widget (navigates to image generation page)
            ImageGenCard(
                onClick = { navController.navigate(Screen.ImageGen) },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            )
        }

        // Weekly Messages Graph (replaces Most Active Character)
        WeeklyMessagesCard(
            weeklyMessages = stats.weeklyMessages,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun StatCard(
    title: String,
    value: String,
    icon: ImageVector,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier
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
                .padding(20.dp)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Surface(
                color = contentColor.copy(alpha = 0.2f),
                contentColor = contentColor,
                shape = me.rerere.rikkahub.ui.theme.AppShapes.Chip,
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, null)
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = contentColor.copy(alpha = 0.8f)
                )
            }
        }
    }
}

@Composable
private fun ImageGenCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = rememberPremiumHaptics()
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 400f),
        label = "imagegen_scale"
    )
    
    Card(
        onClick = {
            haptics.perform(HapticPattern.Pop)
            onClick()
        },
        interactionSource = interactionSource,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = me.rerere.rikkahub.ui.theme.AppShapes.CardMedium,
        modifier = modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
    ) {
        Column(
            modifier = Modifier
                .padding(20.dp)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f),
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    shape = me.rerere.rikkahub.ui.theme.AppShapes.Chip,
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Image, null)
                    }
                }
                Surface(
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.14f),
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    shape = RoundedCornerShape(999.dp)
                ) {
                    Text(
                        text = "Open",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Image Gen",
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold
                    )
                    Icon(
                        imageVector = Icons.Rounded.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Text(
                    text = "Create images",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                )
            }
        }
    }
}

@Composable
private fun WeeklyMessagesCard(
    weeklyMessages: List<DayMessages>,
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
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
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
                        color = contentColor.copy(alpha = 0.8f)
                    )
                    Text(
                        text = "$totalMessages messages",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "~$dailyAverage/day",
                        style = MaterialTheme.typography.labelMedium,
                        color = contentColor.copy(alpha = 0.7f)
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
                            color = if (isToday) {
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
