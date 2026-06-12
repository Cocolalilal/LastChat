package me.rerere.rikkahub.ui.modifier

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.blurEffect
import dev.chrisbanes.haze.blur.materials.HazeMaterials
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource

data class LastChatBlur(
    val enabled: Boolean = false,
    val hazeState: HazeState? = null,
)

val LocalLastChatBlur = staticCompositionLocalOf { LastChatBlur() }

@Composable
fun Modifier.lastChatBlurSource(): Modifier {
    val blur = LocalLastChatBlur.current
    val hazeState = blur.hazeState
    return if (blur.enabled && hazeState != null) {
        this.hazeSource(hazeState)
    } else {
        this
    }
}

@Composable
@OptIn(ExperimentalHazeApi::class)
fun Modifier.lastChatBlurEffect(
    containerColor: Color,
    shape: Shape? = null,
): Modifier {
    val blur = LocalLastChatBlur.current
    val hazeState = blur.hazeState
    val style = HazeMaterials.ultraThin(containerColor = containerColor)
    return if (blur.enabled && hazeState != null) {
        val clippedModifier = if (shape != null) this.clip(shape) else this
        val effectModifier = clippedModifier.hazeEffect(state = hazeState) {
            clipToAreasBounds = true
            expandLayerBounds = false
            forceInvalidateOnPreDraw = false
            canDrawArea = { true }
            blurEffect {
                this.style = style
            }
        }
        if (shape != null) {
            effectModifier.clip(shape)
        } else {
            effectModifier
        }
    } else {
        this
    }
}

@Composable
fun blurredContainerColor(
    fallback: Color,
): Color {
    val blur = LocalLastChatBlur.current
    if (!blur.enabled || blur.hazeState == null) {
        return fallback
    }

    val minimumAlpha = if (isSystemInDarkTheme()) 0.34f else 0.28f
    val glassAlpha = if (fallback.alpha < 1f) {
        fallback.alpha.coerceAtLeast(minimumAlpha)
    } else {
        minimumAlpha
    }
    return fallback.copy(alpha = glassAlpha)
}
