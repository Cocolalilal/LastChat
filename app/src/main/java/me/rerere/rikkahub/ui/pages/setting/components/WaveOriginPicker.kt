package me.rerere.rikkahub.ui.pages.setting.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.math.roundToInt

/**
 * Full-screen dialog that lets the user pick where the assistant-overlay glow "wave" comes
 * from by dragging a handle around the screen edges. Positions are stored as fractions of
 * the screen (0..1); the handle is snapped to the nearest edge (the perimeter) so the wave
 * emanates from an edge — e.g. next to the side button.
 */
@Composable
fun WaveOriginPickerDialog(
    initialX: Float,
    initialY: Float,
    onDismiss: () -> Unit,
    onSave: (Float, Float) -> Unit,
) {
    var ox by remember { mutableStateOf(initialX) }
    var oy by remember { mutableStateOf(initialY) }
    var boxSize by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current
    val primary = MaterialTheme.colorScheme.primary

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .padding(16.dp),
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text("Wave origin", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Drag the dot around the screen edges to choose where the glow wave " +
                        "washes in from — e.g. next to your side button.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .fillMaxWidth(0.58f)
                        .aspectRatio(9f / 19.5f)
                        .clip(RoundedCornerShape(26.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(26.dp))
                        .onSizeChanged { boxSize = it }
                        .pointerInput(Unit) {
                            detectTapGestures { pos ->
                                val (sx, sy) = snapToEdge(
                                    (pos.x / size.width).coerceIn(0f, 1f),
                                    (pos.y / size.height).coerceIn(0f, 1f),
                                )
                                ox = sx; oy = sy
                            }
                        }
                        .pointerInput(Unit) {
                            detectDragGestures { change, _ ->
                                change.consume()
                                val (sx, sy) = snapToEdge(
                                    (change.position.x / size.width).coerceIn(0f, 1f),
                                    (change.position.y / size.height).coerceIn(0f, 1f),
                                )
                                ox = sx; oy = sy
                            }
                        }
                        .drawBehind {
                            val c = Offset(ox * size.width, oy * size.height)
                            val radius = size.maxDimension * 0.6f
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        primary.copy(alpha = 0.55f),
                                        primary.copy(alpha = 0.16f),
                                        Color.Transparent,
                                    ),
                                    center = c,
                                    radius = radius,
                                ),
                                radius = radius,
                                center = c,
                            )
                        },
                ) {
                    val handlePx = with(density) { 9.dp.toPx() }
                    Box(
                        modifier = Modifier
                            .offset {
                                IntOffset(
                                    (ox * boxSize.width - handlePx).roundToInt(),
                                    (oy * boxSize.height - handlePx).roundToInt(),
                                )
                            }
                            .size(18.dp)
                            .background(primary, CircleShape)
                            .border(2.dp, MaterialTheme.colorScheme.onPrimary, CircleShape),
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = { ox = 0.5f; oy = 1f }) { Text("Reset") }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(Modifier.width(4.dp))
                    Button(onClick = { onSave(ox, oy) }) { Text("Save") }
                }
            }
        }
    }
}

/** Snap a point in [0,1]² to the nearest screen edge (perimeter). */
private fun snapToEdge(fx: Float, fy: Float): Pair<Float, Float> {
    val left = fx
    val right = 1f - fx
    val top = fy
    val bottom = 1f - fy
    return when (minOf(left, right, top, bottom)) {
        left -> 0f to fy
        right -> 1f to fy
        top -> fx to 0f
        else -> fx to 1f
    }
}
