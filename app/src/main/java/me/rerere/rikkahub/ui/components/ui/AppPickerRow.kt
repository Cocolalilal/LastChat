package me.rerere.rikkahub.ui.components.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.rerere.rikkahub.ui.hooks.HapticPattern
import me.rerere.rikkahub.ui.hooks.rememberPremiumHaptics
import me.rerere.rikkahub.ui.theme.groupedItemShape
import me.rerere.rikkahub.ui.theme.nestedSurfaceColor
import me.rerere.rikkahub.ui.theme.placedSurfaceColor

enum class AppPickerRowStyle {
    FilledNested,
    PlacedSurface,
    FlatTransparent,
    Destructive,
}

@Composable
fun AppPickerRow(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    position: ItemPosition? = null,
    style: AppPickerRowStyle = AppPickerRowStyle.FilledNested,
    onClick: () -> Unit,
    trailing: @Composable (RowScope.() -> Unit)? = null,
) {
    val resolvedPosition = rememberGroupedStackPosition(position)
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 400f),
        label = "picker_row_scale",
    )
    val haptics = rememberPremiumHaptics()
    val containerColor = when (style) {
        AppPickerRowStyle.FilledNested -> nestedSurfaceColor()
        AppPickerRowStyle.PlacedSurface -> placedSurfaceColor()
        AppPickerRowStyle.FlatTransparent -> MaterialTheme.colorScheme.surface.copy(alpha = 0f)
        AppPickerRowStyle.Destructive -> MaterialTheme.colorScheme.errorContainer
    }
    val titleColor = when (style) {
        AppPickerRowStyle.Destructive -> MaterialTheme.colorScheme.onErrorContainer
        else -> MaterialTheme.colorScheme.onSurface
    }
    val subtitleColor = when (style) {
        AppPickerRowStyle.Destructive -> MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.78f)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val rowContentColor = when (style) {
        AppPickerRowStyle.Destructive -> MaterialTheme.colorScheme.onErrorContainer
        else -> titleColor
    }

    Surface(
        onClick = {
            haptics.perform(HapticPattern.Pop)
            onClick()
        },
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        interactionSource = interactionSource,
        color = containerColor,
        contentColor = rowContentColor,
        shape = groupedItemShape(resolvedPosition),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(
                modifier = Modifier.size(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                icon()
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = titleColor,
                )
                if (subtitle.isNotBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = subtitleColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            trailing?.invoke(this)
        }
    }
}
