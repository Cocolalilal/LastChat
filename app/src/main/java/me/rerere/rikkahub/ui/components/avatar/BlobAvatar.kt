package me.rerere.rikkahub.ui.components.avatar

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import kotlinx.coroutines.isActive
import me.rerere.rikkahub.data.model.Avatar
import me.rerere.rikkahub.data.model.BlobEyePack
import me.rerere.rikkahub.ui.motion.LocalMotionPolicy
import kotlin.math.min

@Composable
fun BlobAvatar(
    spec: Avatar.Blob,
    modifier: Modifier = Modifier,
    lifecycle: BlobLifecycle = BlobLifecycle.Idle,
    onClick: (() -> Unit)? = null,
) {
    val runtime = remember { BlobRuntime() }
    var tSeconds by remember { mutableFloatStateOf(0f) }
    val reduceMotion = LocalMotionPolicy.current.reduceMotion

    LaunchedEffect(reduceMotion) {
        var last = 0L
        while (isActive) {
            val frame = withFrameNanos { it }
            if (last != 0L) {
                val dt = (frame - last) / 1_000_000_000f
                tSeconds += if (reduceMotion) dt * 0.35f else dt
            }
            last = frame
        }
    }

    val frame = runtime.sample(tSeconds, spec, lifecycle, reduceMotion)
    val clickModifier = if (onClick != null) {
        Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick,
        )
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .then(clickModifier)
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithCache {
                val radius = min(size.width, size.height) / 2f
                val cx = size.width / 2f
                val cy = size.height / 2f
                val body = BlobShapes.toPath(frame.radii, cx, cy, radius)
                val bodyColor = Color(parseHexArgb(frame.colorHex))
                val highlight = Color.White.copy(alpha = 0.22f)
                val (left, right) = layoutEyes(frame, cx, cy, radius)
                val blink = blinkScale(frame.lid)
                val glowColor = Color(parseHexArgb(frame.glowHex, 0xFF87D2E9.toInt()))

                onDrawBehind {
                    drawPath(
                        path = body,
                        brush = Brush.radialGradient(
                            colors = listOf(
                                androidx.compose.ui.graphics.lerp(bodyColor, highlight, 0.35f),
                                bodyColor,
                            ),
                            center = Offset(cx - radius * 0.18f, cy - radius * 0.22f),
                            radius = radius * 1.35f,
                        ),
                    )

                    if (frame.pack == BlobEyePack.Generical) {
                        drawGenericalEye(left, blink, frame, glowColor)
                        drawGenericalEye(right, blink, frame, glowColor)
                    } else {
                        drawGrokEye(left, blink)
                        drawGrokEye(right, blink)
                    }
                }
            }
            .fillMaxSize()
    )
}

@Composable
fun BlobAvatar(
    spec: Avatar.Blob,
    size: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
    lifecycle: BlobLifecycle = BlobLifecycle.Idle,
    onClick: (() -> Unit)? = null,
) {
    BlobAvatar(
        spec = spec,
        modifier = modifier.size(size),
        lifecycle = lifecycle,
        onClick = onClick,
    )
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawGrokEye(
    eye: LaidOutEye,
    blink: Float,
) {
    if (!eye.visible) return
    val path = eyeCapsulePath(eye, blink * eye.open)
    drawPath(path, Color.White, blendMode = BlendMode.DstOut)
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawGenericalEye(
    eye: LaidOutEye,
    blink: Float,
    frame: BlobFrame,
    glowColor: Color,
) {
    if (!eye.visible) return
    val lid = blink * eye.open
    val path = eyeCapsulePath(eye, lid)
    if (frame.glowEnabled) {
        val glowR = maxOf(eye.hw, eye.hh) * 1.55f
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    glowColor.copy(alpha = frame.glowAlpha),
                    glowColor.copy(alpha = 0f),
                ),
                center = Offset(eye.cx, eye.cy),
                radius = glowR,
            ),
            radius = glowR,
            center = Offset(eye.cx, eye.cy),
        )
    }
    drawPath(
        path = path,
        brush = Brush.verticalGradient(
            colors = listOf(Color.White, Color(0xFFD7F4FF)),
            startY = eye.cy - eye.hh,
            endY = eye.cy + eye.hh,
        ),
    )
    val stroke = (size.minDimension * 0.028f).coerceAtLeast(1.2f)
    drawPath(
        path = path,
        color = Color.White,
        style = Stroke(width = stroke, cap = StrokeCap.Round),
    )
}

private fun eyeCapsulePath(eye: LaidOutEye, blink: Float): Path {
    val corner = min(eye.hw, eye.hh)
    val path = Path().apply {
        addRoundRect(
            RoundRect(
                rect = Rect(-eye.hw, -eye.hh, eye.hw, eye.hh),
                radiusX = corner,
                radiusY = corner,
            )
        )
    }
    path.transform(eyeMatrix(eye, blink))
    return path
}

private fun eyeMatrix(eye: LaidOutEye, blink: Float): Matrix {
    val ct = kotlin.math.cos(eye.tiltRad)
    val st = kotlin.math.sin(eye.tiltRad)
    val m00 = eye.a * ct + eye.c * st
    val m01 = -eye.a * st + eye.c * ct
    val m10 = blink * (eye.b * ct + eye.d * st)
    val m11 = blink * (-eye.b * st + eye.d * ct)
    val values = FloatArray(16)
    values[10] = 1f
    values[15] = 1f
    values[Matrix.ScaleX] = m00
    values[Matrix.SkewX] = m01
    values[Matrix.SkewY] = m10
    values[Matrix.ScaleY] = m11
    values[Matrix.TranslateX] = eye.cx
    values[Matrix.TranslateY] = eye.cy
    val matrix = Matrix()
    matrix.setFrom(values)
    return matrix
}

@Preview(showBackground = true, backgroundColor = 0xFF101010)
@Composable
private fun PreviewBlobAvatars() {
    Row(
        modifier = Modifier.padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        BlobAvatar(spec = Avatar.Blob.generical(), modifier = Modifier.size(72.dp))
        BlobAvatar(spec = Avatar.Blob.grok(), modifier = Modifier.size(72.dp))
    }
}
