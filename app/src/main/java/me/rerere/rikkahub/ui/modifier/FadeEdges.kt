package me.rerere.rikkahub.ui.modifier

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer

private const val DEFAULT_FADE_HEIGHT = 48f

/**
 * Applies a vertical alpha mask that fades the top and/or bottom edges of the
 * composable. This matches the code-block peek fade.
 */
fun Modifier.fadeEdges(
    fadeTop: Boolean,
    fadeBottom: Boolean,
    fadeHeight: Float = DEFAULT_FADE_HEIGHT,
): Modifier = graphicsLayer { alpha = 0.99f }.drawWithCache {
    val fadeFraction = (fadeHeight / size.height).coerceIn(0f, 0.45f)
    val colorStops = buildList {
        add(0f to if (fadeTop) Color.Transparent else Color.Black)
        if (fadeTop) add(fadeFraction to Color.Black)
        if (fadeBottom) add((1f - fadeFraction).coerceIn(0f, 1f) to Color.Black)
        add(1f to if (fadeBottom) Color.Transparent else Color.Black)
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
