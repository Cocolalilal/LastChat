package me.rerere.rikkahub.ui.activity

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import me.rerere.rikkahub.phoneuse.PHONE_MOTION_DAMPING
import me.rerere.rikkahub.phoneuse.PHONE_MOTION_STIFFNESS
import me.rerere.rikkahub.ui.motion.rememberSystemMotionPolicy
import kotlin.math.roundToInt

/** Preserve the input composition and let it slide below the panel's rounded window edge. */
@Composable
internal fun AssistantInputReveal(
    visible: Boolean,
    onHidden: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val reduced = rememberSystemMotionPolicy().reduceMotion
    val progress = remember { Animatable(if (visible) 1f else 0f, visibilityThreshold = 0.0005f) }
    val callback by rememberUpdatedState(onHidden)
    LaunchedEffect(visible, reduced) {
        callback(false)
        progress.animateTo(if (visible) 1f else 0f,
            if (reduced) snap() else spring(PHONE_MOTION_DAMPING, PHONE_MOTION_STIFFNESS))
        callback(!visible)
    }
    Box(modifier.layout { measurable, constraints ->
        val child = measurable.measure(constraints.copy(minHeight = 0))
        layout(child.width, (child.height * progress.value.coerceIn(0f, 1f)).roundToInt()) {
            child.place(0, 0)
        }
    }.graphicsLayer { alpha = progress.value.coerceIn(0f, 1f) }) {
        content()
    }
}
