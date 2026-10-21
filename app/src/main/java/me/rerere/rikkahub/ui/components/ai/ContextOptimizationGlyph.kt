package me.rerere.rikkahub.ui.components.ai

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import me.rerere.rikkahub.service.ContextCompressionStatus
import me.rerere.rikkahub.service.ContextManagementActivity
import me.rerere.rikkahub.ui.motion.LocalMotionPolicy

/** Native expressive loader → circle → usage ring, all inside the existing fixed-size chrome. */
@Composable
fun ContextOptimizationGlyph(
    status: ContextCompressionStatus?,
    windowFraction: Float,
    reservedFraction: Float,
    progressColor: Color,
) {
    val reduceMotion = LocalMotionPolicy.current.reduceMotion
    val running = status?.isRunning == true
    var completing by remember { mutableStateOf(false) }
    val completionMorph = remember { Animatable(0f) }
    val shapes = remember { listOf(MaterialShapes.Cookie9Sided, MaterialShapes.Circle) }
    LaunchedEffect(status?.activity, status?.completedAt, reduceMotion) {
        completing = false
        completionMorph.snapTo(0f)
        if (status?.activity == ContextManagementActivity.COMPLETE && status.tokensSaved > 0 &&
            (System.currentTimeMillis() - status.completedAt) in 0..1_499 && !reduceMotion
        ) {
            completing = true
            completionMorph.animateTo(1f, tween(420))
            delay(280)
            completing = false
        }
    }
    val loaderWeight by animateFloatAsState(
        if (running || completing) 1f else 0f,
        animationSpec = tween(if (reduceMotion) 0 else 260), label = "context_loader_blend",
    )
    val ringProgress by animateFloatAsState(
        if (completing) 1f else windowFraction,
        animationSpec = spring(dampingRatio = 0.85f, stiffness = 180f), label = "context_return_to_usage",
    )
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val reserve = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
    val primary = MaterialTheme.colorScheme.primary
    Box(Modifier.size(28.dp).clearAndSetSemantics {
        contentDescription = "Conversation context"
        stateDescription = when {
            running -> "Optimizing context"
            completing -> "Context optimized"
            else -> "${(windowFraction * 100).toInt()} percent of context used"
        }
    }, contentAlignment = androidx.compose.ui.Alignment.Center) {
        Canvas(Modifier.size(27.dp).graphicsLayer { alpha = 1f - loaderWeight }) {
            val stroke = Stroke(3.5.dp.toPx(), cap = StrokeCap.Round)
            drawArc(track, -90f, 360f, false, style = stroke)
            if (reservedFraction > 0) {
                drawArc(reserve, -90f + (1f - reservedFraction) * 360f, reservedFraction * 360f, false, style = stroke)
            }
            drawArc(progressColor, -90f, ringProgress * 360f, false, style = stroke)
        }
        if (loaderWeight > 0f) {
            val modifier = Modifier.size(28.dp).graphicsLayer {
                alpha = loaderWeight
                scaleX = 0.88f + loaderWeight * 0.12f
                scaleY = scaleX
            }
            if (completing || !running || reduceMotion) {
                LoadingIndicator(
                    progress = { if (reduceMotion) 0f else completionMorph.value },
                    modifier = modifier, color = primary, polygons = shapes,
                )
            } else {
                LoadingIndicator(modifier = modifier, color = primary, polygons = shapes)
            }
        }
    }
}
