package me.rerere.rikkahub.ui.components.avatar

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.isActive
import me.rerere.rikkahub.data.model.Avatar
import me.rerere.rikkahub.data.model.BlobEyePack
import me.rerere.rikkahub.ui.motion.LocalMotionPolicy
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

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
    val glancePulse = LocalBlobGlance.current
    var glanceStrength by remember { mutableFloatStateOf(0f) }
    var glanceX by remember { mutableFloatStateOf(0f) }
    var glanceY by remember { mutableFloatStateOf(0f) }
    var lastPulseId by remember { mutableIntStateOf(0) }

    LaunchedEffect(glancePulse.id) {
        if (glancePulse.id != 0 && glancePulse.id != lastPulseId && glancePulse.strength > 0f) {
            lastPulseId = glancePulse.id
            glanceX = glancePulse.x
            glanceY = glancePulse.y
            glanceStrength = 1f
        }
    }

    LaunchedEffect(reduceMotion) {
        var last = 0L
        while (isActive) {
            val frame = withFrameNanos { it }
            if (last != 0L) {
                val dt = (frame - last) / 1_000_000_000f
                tSeconds += if (reduceMotion) dt * 0.35f else dt
                if (glanceStrength > 0f) {
                    glanceStrength = (glanceStrength - dt / BLOB_GLANCE_DECAY_SECONDS).coerceAtLeast(0f)
                }
            }
            last = frame
        }
    }

    val frame = runtime.sample(
        tSeconds = tSeconds,
        spec = spec,
        requested = lifecycle,
        reduceMotion = reduceMotion,
        glance = BlobGlance(glanceX, glanceY, glanceStrength),
    )
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
                val radius = blobDrawRadius(min(size.width, size.height))
                val cx = size.width / 2f
                val cy = size.height / 2f
                val body = BlobShapes.toPath(
                    radii = frame.radii,
                    cx = cx,
                    cy = cy,
                    scale = radius,
                    squashX = frame.squashX,
                    squashY = frame.squashY,
                )
                val bodyColor = Color(parseHexArgb(frame.colorHex))
                val (left, right) = layoutEyes(frame, cx, cy, radius)
                val blink = blinkScale(frame.lid)
                val glowColor = Color(parseHexArgb(frame.glowHex, 0xFF87D2E9.toInt()))
                val accent = Color(parseHexArgb(frame.accentHex, 0xFFE8F7FF.toInt()))
                val hx = cx + cos(frame.highlightAngle) * radius * 0.30f
                val hy = cy + sin(frame.highlightAngle) * radius * 0.26f

                onDrawBehind {
                    if (frame.pack == BlobEyePack.Generical && frame.glowEnabled) {
                        val glowR = radius * (1.10f + 0.28f * frame.glowStrength)
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    glowColor.copy(alpha = frame.glowAlpha * 0.62f),
                                    glowColor.copy(alpha = 0f),
                                ),
                                center = Offset(cx, cy),
                                radius = glowR,
                            ),
                            radius = glowR,
                            center = Offset(cx, cy),
                        )
                    }

                    if (frame.flatFill) {
                        drawPath(path = body, color = bodyColor)
                        drawPath(
                            path = body,
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0.08f),
                                    Color.White.copy(alpha = 0f),
                                ),
                                center = Offset(hx, hy),
                                radius = radius * 0.20f,
                            ),
                        )
                    } else {
                        val highlight = Color.White.copy(alpha = 0.22f)
                        drawPath(
                            path = body,
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    androidx.compose.ui.graphics.lerp(bodyColor, highlight, 0.28f),
                                    bodyColor,
                                ),
                                center = Offset(cx - radius * 0.16f, cy - radius * 0.20f),
                                radius = radius * 1.28f,
                            ),
                        )
                    }

                    if (frame.pack == BlobEyePack.Generical) {
                        drawGenericalEye(left, blink, accent)
                        drawGenericalEye(right, blink, accent)
                    } else {
                        drawGrokEye(left, blink, size.minDimension)
                        drawGrokEye(right, blink, size.minDimension)
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
    minDim: Float,
) {
    if (!eye.visible) return
    val lid = blink * eye.open
    val path = eyeCapsulePath(eye, lid)
    drawPath(path, Color.White, blendMode = BlendMode.DstOut)
    val rim = (minDim * 0.012f).coerceAtLeast(1f)
    drawPath(
        path = path,
        color = Color.White.copy(alpha = 0.42f),
        style = Stroke(width = rim, cap = StrokeCap.Round),
    )
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawGenericalEye(
    eye: LaidOutEye,
    blink: Float,
    accent: Color,
) {
    if (!eye.visible) return
    val lid = blink * eye.open
    val path = eyeCapsulePath(eye, lid)
    drawPath(path, Color.White)
    val gloss = eyeCapsulePath(eye, lid, inset = 0.38f, lift = -0.28f)
    drawPath(gloss, accent.copy(alpha = 0.55f))
    val shade = eyeCapsulePath(eye, lid, inset = 0.22f, lift = 0.42f)
    drawPath(shade, Color.Black.copy(alpha = 0.05f))
}

@Preview(showBackground = true, backgroundColor = 0xFF101010, widthDp = 400, heightDp = 280)
@Composable
private fun PreviewBlobLifecycleGrid() {
    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        for (pack in listOf(BlobEyePack.Generical, BlobEyePack.Grok)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (life in BlobLifecycle.entries) {
                    BlobAvatar(
                        spec = if (pack == BlobEyePack.Grok) Avatar.Blob.grok() else Avatar.Blob.generical(),
                        modifier = Modifier.size(56.dp),
                        lifecycle = life,
                    )
                }
            }
        }
    }
}
