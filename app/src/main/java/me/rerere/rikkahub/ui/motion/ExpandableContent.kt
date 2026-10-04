package me.rerere.rikkahub.ui.motion

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout

internal fun MotionPolicy.sectionExpandFadesOnly(): Boolean = reduceMotion

/**
 * Shared settings-section expand/collapse.
 *
 * Height eases all the way to rest and the content fades where it already sits.
 * No corner scale: that reads as content flying in from the end, and the size
 * spring then rests and snaps the last sliver shut. Reduce-motion fades only.
 */
@Composable
fun ExpandableContent(
    visible: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val motionPolicy = LocalMotionPolicy.current
    if (motionPolicy.sectionExpandFadesOnly()) {
        AnimatedVisibility(
            visible = visible,
            modifier = modifier,
            enter = fadeIn(animationSpec = tween(durationMillis = TOP_LEVEL_FADE_IN_DURATION_MS)),
            exit = fadeOut(animationSpec = tween(durationMillis = TOP_LEVEL_FADE_OUT_DURATION_MS)),
        ) {
            content()
        }
        return
    }

    val progress by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow,
            visibilityThreshold = 0.001f,
        ),
        label = "sectionExpand",
    )
    if (!visible && progress <= 0f) return

    Box(
        modifier = modifier
            .graphicsLayer {
                alpha = progress
                clip = true
            }
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                val height = (placeable.height * progress).toInt().coerceIn(0, placeable.height)
                layout(placeable.width, height) {
                    placeable.place(0, 0)
                }
            },
    ) {
        content()
    }
}
