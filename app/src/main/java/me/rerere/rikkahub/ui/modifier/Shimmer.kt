package me.rerere.rikkahub.ui.modifier

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntSize
import me.rerere.rikkahub.ui.motion.LocalMotionPolicy

@Composable
fun Modifier.shimmer(
    isLoading: Boolean,
    shimmerColor: Color = LocalContentColor.current.copy(alpha = 0.3f),
    backgroundColor: Color = LocalContentColor.current.copy(alpha = 0.9f),
    durationMillis: Int = 1200,
    angle: Float = 20f,
    gradientWidthRatio: Float = 0.5f
): Modifier = composed {
    if (!isLoading) {
        this
    } else {
        val motionPolicy = LocalMotionPolicy.current
        var size by remember { mutableStateOf(IntSize.Zero) }
        val translateAnimation = if (motionPolicy.reduceMotion) {
            null
        } else {
            val transition = rememberInfiniteTransition(label = "ShimmerTransition")
            transition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = durationMillis, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "ShimmerTranslate"
            )
        }
        val angleRad = Math.toRadians(angle.toDouble()).toFloat()
        val colors = remember(shimmerColor, backgroundColor) {
            listOf(backgroundColor, shimmerColor, backgroundColor)
        }

        this
            .onGloballyPositioned { coordinates ->
                size = coordinates.size
            }
            .graphicsLayer { alpha = 0.99f }
            .drawWithContent {
                if (size == IntSize.Zero) {
                    drawContent()
                    return@drawWithContent
                }

                val width = size.width.toFloat()
                val height = size.height.toFloat()
                val diagonal = kotlin.math.sqrt(width * width + height * height)
                val gradientWidth = diagonal * gradientWidthRatio
                val totalDistance = diagonal + gradientWidth
                val shimmerProgress = translateAnimation?.value ?: 0.35f
                val currentOffset = shimmerProgress * totalDistance - gradientWidth
                val startX = currentOffset * kotlin.math.cos(angleRad)
                val startY = currentOffset * kotlin.math.sin(angleRad)
                val endX = (currentOffset + gradientWidth) * kotlin.math.cos(angleRad)
                val endY = (currentOffset + gradientWidth) * kotlin.math.sin(angleRad)
                val shimmerBrush = Brush.linearGradient(
                    colors = colors,
                    start = Offset(startX, startY),
                    end = Offset(endX, endY),
                    tileMode = TileMode.Clamp
                )

                drawContent()
                drawRect(
                    brush = shimmerBrush,
                    blendMode = BlendMode.DstIn
                )
            }
    }
}
