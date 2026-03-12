package me.rerere.rikkahub.ui.components.chat

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Position of a bubble within a group - determines corner styling.
 */
enum class BubblePosition {
    /** Single bubble, not part of a group */
    SINGLE,
    /** First bubble in a group (top) */
    FIRST,
    /** Middle bubble in a group */
    MIDDLE,
    /** Last bubble in a group (bottom) */
    LAST
}

/**
 * Role determines alignment and color scheme.
 */
enum class BubbleRole {
    USER,
    ASSISTANT,
    ACTIVITY  // Activity pill styling
}

/**
 * A message bubble with grouped corner radii support.
 * 
 * When bubbles are stacked in a group, the inner corners (where they meet)
 * are smaller to create a visual "stack" effect like in iMessage.
 * 
 * For assistant messages, the small corners are on the LEFT side.
 * For user messages, the small corners are on the RIGHT side.
 */
@Composable
fun GroupedMessageBubble(
    position: BubblePosition,
    role: BubbleRole,
    modifier: Modifier = Modifier,
    containerColor: Color? = null,
    contentColor: Color? = null,
    largeRadius: Dp = 20.dp,
    smallRadius: Dp = 6.dp,
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
        BubbleRole.ASSISTANT -> MaterialTheme.colorScheme.onSurface
        BubbleRole.ACTIVITY -> MaterialTheme.colorScheme.onSurface
    }
    
    // Calculate corner radii based on position and role
    // For assistant (left-aligned): small corners on the left side where bubbles stack
    // For user (right-aligned): small corners on the right side where bubbles stack
    val isLeftAligned = role != BubbleRole.USER
    
    val shape = when (position) {
        BubblePosition.SINGLE -> RoundedCornerShape(largeRadius)
        
        BubblePosition.FIRST -> if (isLeftAligned) {
            // Left aligned: small bottom-left corner
            RoundedCornerShape(
                topStart = largeRadius,
                topEnd = largeRadius,
                bottomEnd = largeRadius,
                bottomStart = smallRadius
            )
        } else {
            // Right aligned: small bottom-right corner
            RoundedCornerShape(
                topStart = largeRadius,
                topEnd = largeRadius,
                bottomEnd = smallRadius,
                bottomStart = largeRadius
            )
        }
        
        BubblePosition.MIDDLE -> if (isLeftAligned) {
            // Left aligned: small top-left and bottom-left corners
            RoundedCornerShape(
                topStart = smallRadius,
                topEnd = largeRadius,
                bottomEnd = largeRadius,
                bottomStart = smallRadius
            )
        } else {
            // Right aligned: small top-right and bottom-right corners
            RoundedCornerShape(
                topStart = largeRadius,
                topEnd = smallRadius,
                bottomEnd = smallRadius,
                bottomStart = largeRadius
            )
        }
        
        BubblePosition.LAST -> if (isLeftAligned) {
            // Left aligned: small top-left corner
            RoundedCornerShape(
                topStart = smallRadius,
                topEnd = largeRadius,
                bottomEnd = largeRadius,
                bottomStart = largeRadius
            )
        } else {
            // Right aligned: small top-right corner
            RoundedCornerShape(
                topStart = largeRadius,
                topEnd = smallRadius,
                bottomEnd = largeRadius,
                bottomStart = largeRadius
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
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                content = content
            )
        }
    } else {
        Surface(
            modifier = modifier,
            shape = shape,
            color = containerColor ?: defaultContainerColor,
            contentColor = contentColor ?: defaultContentColor,
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                content = content
            )
        }
    }
}

/**
 * Helper to determine bubble position in a list.
 */
fun getBubblePosition(index: Int, total: Int): BubblePosition {
    return when {
        total == 1 -> BubblePosition.SINGLE
        index == 0 -> BubblePosition.FIRST
        index == total - 1 -> BubblePosition.LAST
        else -> BubblePosition.MIDDLE
    }
}
