package me.rerere.rikkahub.ui.motion

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandIn
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkOut
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntSize

/**
 * Shared settings-section expand/collapse.
 *
 * This is the available-variables disclosure on the system prompt: Compose's default
 * [AnimatedVisibility] (no-bounce MediumLow spring fade + expandIn). Short tweens and
 * bouncy springs feel steppy or like a parade; reduce-motion fades only.
 */
internal fun MotionPolicy.sectionExpandEnter(): EnterTransition {
    return if (reduceMotion) {
        fadeIn(animationSpec = tween(durationMillis = TOP_LEVEL_FADE_IN_DURATION_MS))
    } else {
        fadeIn(animationSpec = sectionFadeSpring()) + expandIn(animationSpec = sectionSizeSpring())
    }
}

internal fun MotionPolicy.sectionExpandExit(): ExitTransition {
    return if (reduceMotion) {
        fadeOut(animationSpec = tween(durationMillis = TOP_LEVEL_FADE_OUT_DURATION_MS))
    } else {
        shrinkOut(animationSpec = sectionSizeSpring()) + fadeOut(animationSpec = sectionFadeSpring())
    }
}

@Composable
fun ExpandableContent(
    visible: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable AnimatedVisibilityScope.() -> Unit,
) {
    val motionPolicy = LocalMotionPolicy.current
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = motionPolicy.sectionExpandEnter(),
        exit = motionPolicy.sectionExpandExit(),
        content = content,
    )
}

private fun sectionFadeSpring() = spring<Float>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessMediumLow,
)

private fun sectionSizeSpring() = spring<IntSize>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessMediumLow,
)
