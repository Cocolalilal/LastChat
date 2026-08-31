package me.rerere.rikkahub.ui.modifier

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer

private const val DEFAULT_FADE_HEIGHT = 72f

/**
 * Applies a vertical alpha mask that fades the top and/or bottom edges of the
 * composable. Smoothly supports animated progress [0f..1f] for top and bottom.
 */
fun Modifier.fadeEdges(
    topProgress: Float = 0f,
    bottomProgress: Float = 0f,
    fadeHeight: Float = DEFAULT_FADE_HEIGHT,
): Modifier {
    val clampedTop = topProgress.coerceIn(0f, 1f)
    val clampedBottom = bottomProgress.coerceIn(0f, 1f)

    if (clampedTop <= 0f && clampedBottom <= 0f) {
        return this
    }

    return graphicsLayer { alpha = 0.99f }.drawWithCache {
        val fadeFraction = (fadeHeight / size.height).coerceIn(0.01f, 0.45f)
        val colorStops = buildList {
            // Top edge fade:
            if (clampedTop > 0f) {
                val t0 = 0f to Color.Black.copy(alpha = 1f - clampedTop)
                val t1 = (fadeFraction * 0.35f) to Color.Black.copy(alpha = (1f - clampedTop * 0.75f).coerceIn(0f, 1f))
                val t2 = (fadeFraction * 0.7f) to Color.Black.copy(alpha = (1f - clampedTop * 0.3f).coerceIn(0f, 1f))
                val t3 = fadeFraction to Color.Black
                add(t0)
                add(t1)
                add(t2)
                add(t3)
            } else {
                add(0f to Color.Black)
            }

            // Bottom edge fade:
            if (clampedBottom > 0f) {
                val b3 = (1f - fadeFraction) to Color.Black
                val b2 = (1f - fadeFraction * 0.7f) to Color.Black.copy(alpha = (1f - clampedBottom * 0.3f).coerceIn(0f, 1f))
                val b1 = (1f - fadeFraction * 0.35f) to Color.Black.copy(alpha = (1f - clampedBottom * 0.75f).coerceIn(0f, 1f))
                val b0 = 1f to Color.Black.copy(alpha = 1f - clampedBottom)
                add(b3)
                add(b2)
                add(b1)
                add(b0)
            } else {
                add(1f to Color.Black)
            }
        }.toTypedArray()

        val brush = Brush.verticalGradient(
            colorStops = colorStops,
            startY = 0f,
            endY = size.height
        )

        onDrawWithContent {
            drawContent()
            drawRect(
                brush = brush,
                size = Size(size.width, size.height),
                blendMode = BlendMode.DstIn
            )
        }
    }
}

/**
 * Convenience overload accepting boolean flags for top and bottom fade.
 */
fun Modifier.fadeEdges(
    fadeTop: Boolean,
    fadeBottom: Boolean,
    fadeHeight: Float = DEFAULT_FADE_HEIGHT,
): Modifier = fadeEdges(
    topProgress = if (fadeTop) 1f else 0f,
    bottomProgress = if (fadeBottom) 1f else 0f,
    fadeHeight = fadeHeight,
)
