package me.rerere.rikkahub.ui.motion

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import me.rerere.rikkahub.ui.components.settings.sectionExpandProgress
import kotlin.math.roundToInt

internal fun MotionPolicy.sectionExpandFadesOnly(): Boolean = reduceMotion

private const val SECTION_EXPAND_MILLIS = 240

/**
 * Shared settings-section expand/collapse.
 *
 * Height eases to exactly zero and the content fades where it already sits.
 * No corner scale: that reads as content flying in from the top end.
 *
 * The old close rested, then hard-cut, for two reasons that stacked:
 * the size spring is asymptotic, so it crawled and then snapped the last
 * sliver when the visibility threshold fired; and the parent
 * [androidx.compose.foundation.layout.Arrangement.spacedBy] kept a full gap
 * beside this child until the child left the composition, then deleted that
 * gap in one frame. The available-variables block had no gap, so it did not
 * show the cut. [sectionExpandProgress] lets [me.rerere.rikkahub.ui.components.settings.CollapseSpacingColumn]
 * take the extra gap down with the height. A tween reaches zero on its last
 * frame, so removing the child then does not move anything.
 * Reduce-motion fades only.
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
        animationSpec = tween(
            durationMillis = SECTION_EXPAND_MILLIS,
            easing = FastOutSlowInEasing,
        ),
        label = "sectionExpand",
    )
    if (!visible && progress <= 0f) return

    Box(
        modifier = modifier
            .sectionExpandProgress(progress)
            .graphicsLayer { alpha = progress }
            .clipToBounds()
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                val height = (placeable.height * progress).roundToInt().coerceIn(0, placeable.height)
                layout(placeable.width, height) {
                    placeable.place(0, 0)
                }
            },
    ) {
        content()
    }
}
