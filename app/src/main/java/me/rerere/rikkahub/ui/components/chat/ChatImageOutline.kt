package me.rerere.rikkahub.ui.components.chat

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import me.rerere.rikkahub.ui.theme.AppSurface

/**
 * 4dp bubble-colored rim with the photo clipped to the inner rounded rect.
 * Corners of the outer shape stay outline-only, so image pixels cannot sit
 * outside the rounded edge.
 */
fun Modifier.chatImageOutline(shape: Shape, color: Color): Modifier {
    val rim = AppSurface.ImageRimWidth
    return border(width = rim, color = color, shape = shape)
        .padding(rim)
        .clip(shape.insetBy(rim))
}

internal fun Shape.insetBy(rim: Dp): Shape {
    val rounded = this as? RoundedCornerShape ?: return this
    if (rim <= 0.dp) return rounded
    fun CornerSize.shrink(): CornerSize {
        val source = this
        return object : CornerSize {
            override fun toPx(shapeSize: Size, density: Density): Float {
                val rimPx = with(density) { rim.toPx() }
                val outer = Size(shapeSize.width + rimPx * 2f, shapeSize.height + rimPx * 2f)
                return (source.toPx(outer, density) - rimPx).coerceAtLeast(0f)
            }
        }
    }
    return RoundedCornerShape(
        topStart = rounded.topStart.shrink(),
        topEnd = rounded.topEnd.shrink(),
        bottomEnd = rounded.bottomEnd.shrink(),
        bottomStart = rounded.bottomStart.shrink(),
    )
}
