package me.rerere.rikkahub.ui.components.chat

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp

/**
 * The shared interaction surface used by the production conversation list.
 * Keeping the press physics and selected-state paint here makes Android and
 * iOS consume the same visual implementation rather than matching constants.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ConversationRowSurface(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    restingAlpha: Float = 1f,
    onLongClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(dampingRatio = 0.4f, stiffness = 400f),
        label = "conversation_scale",
    )
    val pressAlpha by animateFloatAsState(
        targetValue = if (isPressed) 0.7f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 300f),
        label = "conversation_alpha",
    )
    val backgroundColor = if (selected) {
        lerp(MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.surfaceContainerLow, 0.8f)
    } else {
        Color.Transparent
    }

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = restingAlpha * pressAlpha
            }
            .clip(RoundedCornerShape(50f))
            .combinedClickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .background(backgroundColor),
    ) {
        content()
    }
}
