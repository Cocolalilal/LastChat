package me.rerere.rikkahub.ui.components.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import me.rerere.rikkahub.ui.hooks.HapticPattern
import me.rerere.rikkahub.ui.hooks.rememberPremiumHaptics
import me.rerere.rikkahub.ui.theme.AppShapes
import me.rerere.rikkahub.ui.theme.appOutlinedBorderColor
import me.rerere.rikkahub.ui.theme.nestedSurfaceColor
import me.rerere.rikkahub.ui.theme.placedSurfaceColor

val AppFloatingOverlayContentBottomPadding = 112.dp

enum class AppFloatingActionEmphasis {
    Neutral,
    Primary,
}

@Composable
fun AppFloatingActionColumn(
    modifier: Modifier = Modifier,
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(12.dp),
    horizontalAlignment: Alignment.Horizontal = Alignment.CenterHorizontally,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier,
        verticalArrangement = verticalArrangement,
        horizontalAlignment = horizontalAlignment,
        content = content,
    )
}

@Composable
fun AppFloatingActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: androidx.compose.ui.graphics.Shape = AppShapes.CardLarge,
    emphasis: AppFloatingActionEmphasis = AppFloatingActionEmphasis.Neutral,
    containerColor: androidx.compose.ui.graphics.Color = when (emphasis) {
        AppFloatingActionEmphasis.Primary -> MaterialTheme.colorScheme.primaryContainer
        AppFloatingActionEmphasis.Neutral -> placedSurfaceColor()
    },
    contentColor: androidx.compose.ui.graphics.Color = when (emphasis) {
        AppFloatingActionEmphasis.Primary -> MaterialTheme.colorScheme.onPrimaryContainer
        AppFloatingActionEmphasis.Neutral -> MaterialTheme.colorScheme.onSurface
    },
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable BoxScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.85f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 300f),
        label = "floating_action_scale",
    )
    val haptics = rememberPremiumHaptics()

    Surface(
        onClick = {
            haptics.perform(HapticPattern.Pop)
            onClick()
        },
        modifier = modifier
            .defaultMinSize(minWidth = 56.dp, minHeight = 56.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        interactionSource = interactionSource,
        shape = shape,
        color = containerColor,
        contentColor = contentColor,
        border = BorderStroke(1.dp, appOutlinedBorderColor()),
        tonalElevation = 6.dp,
        shadowElevation = 8.dp,
    ) {
        Box(
            modifier = Modifier
                .padding(contentPadding),
            contentAlignment = Alignment.Center,
            content = content,
        )
    }
}

@Composable
fun BoxScope.AppFloatingControlsOverlay(
    modifier: Modifier = Modifier,
    showFade: Boolean = true,
    fadeHeight: androidx.compose.ui.unit.Dp = 120.dp,
    backgroundColor: Color = MaterialTheme.colorScheme.background,
    content: @Composable BoxScope.() -> Unit,
) {
    if (showFade) {
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(fadeHeight)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            backgroundColor,
                        )
                    )
                )
        )
    }

    Box(
        modifier = modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 16.dp)
    ) {
        content()
    }
}

@Composable
fun AppFloatingTabBar(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(AppShapes.GroupedOuterRadius),
        color = placedSurfaceColor(),
        border = BorderStroke(1.dp, appOutlinedBorderColor()),
        tonalElevation = 6.dp,
        shadowElevation = 8.dp,
    ) {
        Row(
            modifier = Modifier.padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            content = content,
        )
    }
}

@Composable
fun RowScope.AppFloatingTabButton(
    selected: Boolean,
    onClick: () -> Unit,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.85f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 300f),
        label = "floating_tab_scale",
    )
    val haptics = rememberPremiumHaptics()
    val iconColor = if (selected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        onClick = {
            haptics.perform(HapticPattern.Tick)
            onClick()
        },
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        interactionSource = interactionSource,
        shape = CircleShape,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
    ) {
        Box(
            modifier = Modifier.size(48.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = iconColor,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}
