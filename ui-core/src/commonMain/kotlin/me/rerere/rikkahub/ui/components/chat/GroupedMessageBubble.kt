package me.rerere.rikkahub.ui.components.chat

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import me.rerere.rikkahub.ui.theme.AppShapes
import me.rerere.rikkahub.ui.theme.LocalOpticalFrame
import me.rerere.rikkahub.ui.theme.OpticalFrame

enum class BubblePosition { SINGLE, FIRST, MIDDLE, LAST }

enum class BubbleRole { USER, ASSISTANT, ACTIVITY }

/** The production LastChat grouped-message container, shared by Android and iOS. */
@Composable
fun GroupedMessageBubble(
    position: BubblePosition,
    role: BubbleRole,
    modifier: Modifier = Modifier,
    containerColor: Color? = null,
    contentColor: Color? = null,
    largeRadius: Dp = AppShapes.MessageBubbleRadius,
    smallRadius: Dp = AppShapes.MessageBubbleJoint,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val defaultContainerColor = when (role) {
        BubbleRole.USER -> MaterialTheme.colorScheme.primaryContainer
        BubbleRole.ASSISTANT -> MaterialTheme.colorScheme.surfaceContainerHigh
        BubbleRole.ACTIVITY -> MaterialTheme.colorScheme.surfaceContainerHighest
    }
    val defaultContentColor = when (role) {
        BubbleRole.USER -> MaterialTheme.colorScheme.onPrimaryContainer
        BubbleRole.ASSISTANT, BubbleRole.ACTIVITY -> MaterialTheme.colorScheme.onSurface
    }
    val isLeftAligned = role != BubbleRole.USER
    val shape = when (position) {
        BubblePosition.SINGLE -> RoundedCornerShape(largeRadius)
        BubblePosition.FIRST -> if (isLeftAligned) {
            RoundedCornerShape(
                topStart = largeRadius, topEnd = largeRadius,
                bottomEnd = largeRadius, bottomStart = smallRadius,
            )
        } else {
            RoundedCornerShape(
                topStart = largeRadius, topEnd = largeRadius,
                bottomEnd = smallRadius, bottomStart = largeRadius,
            )
        }
        BubblePosition.MIDDLE -> if (isLeftAligned) {
            RoundedCornerShape(
                topStart = smallRadius, topEnd = largeRadius,
                bottomEnd = largeRadius, bottomStart = smallRadius,
            )
        } else {
            RoundedCornerShape(
                topStart = largeRadius, topEnd = smallRadius,
                bottomEnd = smallRadius, bottomStart = largeRadius,
            )
        }
        BubblePosition.LAST -> if (isLeftAligned) {
            RoundedCornerShape(
                topStart = smallRadius, topEnd = largeRadius,
                bottomEnd = largeRadius, bottomStart = largeRadius,
            )
        } else {
            RoundedCornerShape(
                topStart = largeRadius, topEnd = smallRadius,
                bottomEnd = largeRadius, bottomStart = largeRadius,
            )
        }
    }
    val bubbleContent: @Composable () -> Unit = {
        // Horizontal padding is the larger inset. Nested cards read it from
        // LocalOpticalFrame and step their corners down by that amount.
        // Full-bleed blocks add the vertical shortfall themselves.
        val horizontal = AppShapes.MessageBubblePaddingHorizontal
        val vertical = AppShapes.MessageBubblePaddingVertical
        CompositionLocalProvider(
            LocalOpticalFrame provides OpticalFrame(
                outer = largeRadius,
                inset = maxOf(horizontal, vertical),
            )
        ) {
            Column(
                modifier = Modifier.padding(horizontal = horizontal, vertical = vertical),
                content = content,
            )
        }
    }
    if (onClick != null) {
        Surface(
            modifier = modifier,
            shape = shape,
            color = containerColor ?: defaultContainerColor,
            contentColor = contentColor ?: defaultContentColor,
            onClick = onClick,
            content = bubbleContent,
        )
    } else {
        Surface(
            modifier = modifier,
            shape = shape,
            color = containerColor ?: defaultContainerColor,
            contentColor = contentColor ?: defaultContentColor,
            content = bubbleContent,
        )
    }
}

fun getBubblePosition(index: Int, total: Int): BubblePosition = when {
    total == 1 -> BubblePosition.SINGLE
    index == 0 -> BubblePosition.FIRST
    index == total - 1 -> BubblePosition.LAST
    else -> BubblePosition.MIDDLE
}
