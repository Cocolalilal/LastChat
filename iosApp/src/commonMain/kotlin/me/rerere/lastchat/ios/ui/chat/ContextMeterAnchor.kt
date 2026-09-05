package me.rerere.lastchat.ios.ui.chat

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

data class IosContextUsage(
    val usedTokens: Int,
    val totalTokens: Int,
    val conversationTokens: Int = 0,
    val systemPromptTokens: Int = 0,
    val memoryTokens: Int = 0,
    val toolTokens: Int = 0,
) {
    val fractionUsed: Float
        get() = if (totalTokens > 0) (usedTokens.toFloat() / totalTokens).coerceIn(0f, 1f) else 0f

    val remainingPercent: Int
        get() = if (totalTokens > 0) {
            (((totalTokens - usedTokens).coerceAtLeast(0).toFloat() / totalTokens) * 100).toInt().coerceIn(0, 100)
        } else 100
}

@Composable
fun ContextMeterAnchor(
    usage: IosContextUsage?,
    onHaptic: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    if (usage == null) return
    var showDialog by remember { mutableStateOf(false) }

    val animatedPressure by animateFloatAsState(
        targetValue = usage.fractionUsed,
        animationSpec = spring(dampingRatio = 0.78f, stiffness = 240f),
        label = "context_meter_pressure",
    )

    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val targetProgressColor = when {
        animatedPressure >= 0.95f -> MaterialTheme.colorScheme.error
        animatedPressure >= 0.82f -> if (isDark) Color(0xFFFFC857) else Color(0xFFB46900)
        else -> MaterialTheme.colorScheme.primary
    }

    val progressColor by animateColorAsState(
        targetValue = targetProgressColor,
        animationSpec = tween(220),
        label = "context_meter_color",
    )

    val shape = CircleShape
    val containerColor = MaterialTheme.colorScheme.surfaceContainer

    Surface(
        modifier = modifier
            .size(42.dp)
            .clickable {
                onHaptic()
                showDialog = true
            },
        shape = shape,
        color = containerColor,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
    ) {
        Box(contentAlignment = Alignment.Center) {
            val trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
            Canvas(modifier = Modifier.size(24.dp)) {
                val strokeWidth = 3.dp.toPx()
                val stroke = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                drawArc(
                    color = trackColor,
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    style = stroke,
                )
                if (animatedPressure > 0f) {
                    drawArc(
                        color = progressColor,
                        startAngle = -90f,
                        sweepAngle = animatedPressure * 360f,
                        useCenter = false,
                        style = stroke,
                    )
                }
            }
        }
    }

    if (showDialog) {
        ContextUsageDialog(
            usage = usage,
            onDismiss = { showDialog = false },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ContextUsageDialog(
    usage: IosContextUsage,
    onDismiss: () -> Unit,
) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val categoryColors = if (isDark) {
        listOf(
            Color(0xFF8AB4F8), // Conversation - Blue
            Color(0xFFFF8A80), // System Prompt - Coral
            Color(0xFFFFD166), // Memory - Amber
            Color(0xFF7ED99B), // Tools - Green
        )
    } else {
        listOf(
            Color(0xFF2457C5),
            Color(0xFFC43D3D),
            Color(0xFF9A6500),
            Color(0xFF187A3B),
        )
    }

    val segments = listOf(
        Triple("Messages", usage.conversationTokens, categoryColors[0]),
        Triple("System prompt", usage.systemPromptTokens, categoryColors[1]),
        Triple("Memory", usage.memoryTokens, categoryColors[2]),
        Triple("Tools", usage.toolTokens, categoryColors[3]),
    ).filter { it.second > 0 }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "Context Window",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        "${compactTokenCount(usage.usedTokens)} / ${compactTokenCount(usage.totalTokens)} tokens",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "${usage.remainingPercent}% free",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // Segmented Progress Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                ) {
                    if (segments.isNotEmpty()) {
                        segments.forEach { (_, value, color) ->
                            Spacer(
                                Modifier
                                    .weight(value.toFloat().coerceAtLeast(1f))
                                    .fillMaxHeight()
                                    .background(color)
                            )
                        }
                        val remainingTokens = (usage.totalTokens - usage.usedTokens).coerceAtLeast(0)
                        if (remainingTokens > 0) {
                            Spacer(Modifier.weight(remainingTokens.toFloat()).fillMaxHeight())
                        }
                    } else if (usage.usedTokens > 0) {
                        Spacer(
                            Modifier
                                .weight(usage.usedTokens.toFloat().coerceAtLeast(1f))
                                .fillMaxHeight()
                                .background(MaterialTheme.colorScheme.primary)
                        )
                        val remainingTokens = (usage.totalTokens - usage.usedTokens).coerceAtLeast(0)
                        if (remainingTokens > 0) {
                            Spacer(Modifier.weight(remainingTokens.toFloat()).fillMaxHeight())
                        }
                    }
                }

                // Legend breakdown
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    segments.forEach { (label, value, color) ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier
                                    .size(8.dp)
                                    .background(color, CircleShape)
                            )
                            Spacer(Modifier.width(5.dp))
                            Text(
                                "$label ${compactTokenCount(value)}",
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        },
        shape = RoundedCornerShape(24.dp),
    )
}

fun compactTokenCount(tokens: Int): String = when {
    tokens >= 1_000_000 -> "${tokens / 1_000_000}M"
    tokens >= 1_000 -> "${tokens / 1000}k"
    else -> tokens.toString()
}
