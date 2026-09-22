package me.rerere.rikkahub.ui.modifier

import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.blurEffect
import dev.chrisbanes.haze.blur.materials.HazeMaterials
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import me.rerere.rikkahub.ui.theme.AppSurface
import me.rerere.rikkahub.ui.theme.LocalDarkMode

data class LastChatBlur(
    val enabled: Boolean = false,
    val hazeState: HazeState? = null,
)

val LocalLastChatBlur = staticCompositionLocalOf { LastChatBlur() }

@Composable
fun Modifier.lastChatBlurSource(): Modifier {
    val blur = LocalLastChatBlur.current
    val hazeState = blur.hazeState
    val hostAttached = rememberHazeHostAttached()
    return if (blur.enabled && hazeState != null && hostAttached) {
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
    val hostAttached = rememberHazeHostAttached()
    return if (blur.enabled && hazeState != null && hostAttached) {
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

/**
 * Charcoal floating fill. Glass alpha is applied only when blur is enabled *and*
 * a haze source exists; otherwise the color is forced opaque so blur-off chrome
 * is a solid floating layer, not leftover glass.
 */
@Composable
fun lastChatFloatingSurfaceColor(
    charcoal: Color = AppSurface.fill(MaterialTheme.colorScheme),
): Color {
    val blur = LocalLastChatBlur.current
    return AppSurface.resolve(
        charcoal = charcoal,
        blurEnabled = blur.enabled && blur.hazeState != null,
        dark = LocalDarkMode.current,
    )
}

@Composable
fun blurredContainerColor(
    fallback: Color,
): Color = lastChatFloatingSurfaceColor(fallback)

/** Sheets stay solid charcoal (large surfaces need readable fill even when chrome is glass). */
@Composable
fun lastChatSheetContainerColor(): Color = AppSurface.fill(MaterialTheme.colorScheme)

@Composable
fun lastChatDialogContainerColor(): Color = lastChatFloatingSurfaceColor()

@Composable
fun lastChatSoftEdgeBorder(): BorderStroke = BorderStroke(
    AppSurface.SoftEdgeWidth,
    AppSurface.softEdgeColor(MaterialTheme.colorScheme),
)

@Composable
fun lastChatSheetTonalElevation(): Dp = AppSurface.TonalElevation

@Composable
private fun rememberHazeHostAttached(): Boolean {
    var attached by remember { mutableStateOf(true) }
    DisposableEffect(Unit) {
        attached = true
        onDispose { attached = false }
    }
    return attached
}

internal fun isDetachedLayoutCoordinate(error: Throwable): Boolean {
    return error is IllegalStateException &&
        error.message?.contains("isAttached") == true
}

internal fun isHazeDetachedCoordinateCrash(error: Throwable): Boolean {
    if (!isDetachedLayoutCoordinate(error)) return false
    return generateSequence(error) { it.cause }
        .any { throwable ->
            throwable.stackTrace.any { frame ->
                frame.className.startsWith("dev.chrisbanes.haze")
            }
        }
}

internal inline fun <T> runCatchingDetachedLayout(block: () -> T): T? {
    return try {
        block()
    } catch (error: IllegalStateException) {
        if (isDetachedLayoutCoordinate(error)) null else throw error
    }
}
