package me.rerere.rikkahub.ui.components.avatar

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.isActive
import me.rerere.rikkahub.data.model.Avatar
import me.rerere.rikkahub.data.model.BlobEyePack
import me.rerere.rikkahub.ui.motion.LocalMotionPolicy

/**
 * Animated vector "blob" avatar. Compose owns the clock; the engine
 * ([BlobRuntime]) is a pure function of time, so pausing/reduce-motion just
 * scale the clock and the life. Drawing happens in the draw phase only (reads a
 * snapshot-state time), so there is no per-frame recomposition.
 */
@Composable
fun BlobAvatar(
    spec: Avatar.Blob,
    modifier: Modifier = Modifier,
    lifecycle: BlobLifecycle = BlobLifecycle.Idle,
    onClick: (() -> Unit)? = null,
) {
    val runtime = remember { BlobRuntime(lifecycle) }
    val tSeconds = remember { mutableFloatStateOf(0f) }
    val reduceMotion = LocalMotionPolicy.current.reduceMotion
    val motion = if (reduceMotion) 0.4f else 1f
    val specState by rememberUpdatedState(spec)
    val lifeState by rememberUpdatedState(lifecycle)

    LaunchedEffect(Unit) {
        var last = 0L
        while (isActive) {
            val now = androidx.compose.runtime.withFrameNanos { it }
            if (last != 0L) {
                tSeconds.floatValue += (now - last) / 1_000_000_000f
            }
            last = now
        }
    }

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
            .drawBehind {
                val frame = runtime.sample(tSeconds.floatValue, specState, lifeState, motion)
                AvatarDraw.draw(drawContext.canvas.nativeCanvas, frame, size.width, size.height)
            }
    )
}

@Composable
fun BlobAvatar(
    spec: Avatar.Blob,
    size: Dp,
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

@Preview(showBackground = true, backgroundColor = 0xFF101418, widthDp = 360, heightDp = 160)
@Composable
private fun PreviewBlobLifecycleGrid() {
    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        for (pack in listOf(BlobEyePack.Generical, BlobEyePack.Grok)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (life in BlobLifecycle.entries) {
                    BlobAvatar(
                        spec = if (pack == BlobEyePack.Grok) Avatar.Blob.grok() else Avatar.Blob.generical(),
                        modifier = Modifier.size(48.dp),
                        lifecycle = life,
                    )
                }
            }
        }
    }
}
