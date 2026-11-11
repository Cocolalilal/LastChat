package me.rerere.rikkahub.phoneuse

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalView
import me.rerere.rikkahub.ui.hooks.HapticPattern
import me.rerere.rikkahub.ui.hooks.PremiumHaptics

internal const val PHONE_MOTION_STIFFNESS = 650f
internal const val PHONE_MOTION_DAMPING = 0.82f

class PhoneOverlayDragCallbacks(
    val onStart: () -> Unit,
    val onMove: (Float, Float) -> Unit,
    val onRelease: (Float, Float) -> Unit,
) {
    internal var reduceMotion = false
}

/** Screen coordinates keep release velocity correct while the window itself follows the finger. */
internal fun Modifier.phoneOverlayDrag(
    drag: PhoneOverlayDragCallbacks?,
    haptics: PremiumHaptics? = null,
): Modifier = if (drag == null) this else composed {
    val view = LocalView.current
    pointerInput(drag, haptics) {
        val tracker = VelocityTracker()
        val location = IntArray(2)
        var previous: Offset? = null
        detectDragGestures(
            onDragStart = {
                previous = null
                tracker.resetTracking()
                haptics?.perform(HapticPattern.Tick)
                drag.onStart()
            },
            onDragCancel = { drag.onRelease(0f, 0f) },
            onDragEnd = {
                val velocity = tracker.calculateVelocity()
                drag.onRelease(velocity.x, velocity.y)
                haptics?.perform(HapticPattern.Tick)
            },
        ) { change, amount ->
            view.getLocationOnScreen(location)
            val screen = change.position + Offset(location[0].toFloat(), location[1].toFloat())
            val delta = previous?.let { screen - it } ?: amount
            previous = screen
            tracker.addPosition(change.uptimeMillis, screen)
            change.consume()
            drag.onMove(delta.x, delta.y)
        }
    }
}

internal data class PhoneOverlayBounds(val maxX: Float, val minY: Float, val maxY: Float) {
    fun clampX(x: Float) = x.coerceIn(0f, maxX)
    fun clampY(y: Float) = y.coerceIn(minY, maxY)
}

internal fun phoneOverlayBounds(
    width: Int, height: Int, contentWidth: Int, contentHeight: Int, safeMargin: Float,
): PhoneOverlayBounds {
    val availableY = (height - contentHeight).coerceAtLeast(0).toFloat()
    val margin = safeMargin.coerceIn(0f, availableY / 2)
    return PhoneOverlayBounds((width - contentWidth).coerceAtLeast(0).toFloat(), margin, availableY - margin)
}

internal enum class PhoneOverlaySide { LEFT, RIGHT }
internal enum class PhoneOverlayVerticalEdge { TOP, BOTTOM }
internal data class PhoneOverlaySnap(
    val x: Float, val y: Float, val side: PhoneOverlaySide, val vertical: PhoneOverlayVerticalEdge?,
)

/** Offsets are measured from the bottom-right, so finger velocity is inverted by the caller. */
internal fun phoneOverlaySnap(
    x: Float, y: Float, velocityX: Float, velocityY: Float, bounds: PhoneOverlayBounds, edgeBand: Float,
): PhoneOverlaySnap {
    val projectedX = bounds.clampX(x + (if (velocityX.isFinite()) velocityX else 0f) * 0.28f)
    val projectedY = bounds.clampY(y + (if (velocityY.isFinite()) velocityY else 0f) * 0.28f)
    val side = if (projectedX <= bounds.maxX / 2) PhoneOverlaySide.RIGHT else PhoneOverlaySide.LEFT
    val band = edgeBand.coerceIn(0f, (bounds.maxY - bounds.minY) / 2)
    val vertical = when {
        projectedY <= bounds.minY + band -> PhoneOverlayVerticalEdge.BOTTOM
        projectedY >= bounds.maxY - band -> PhoneOverlayVerticalEdge.TOP
        else -> null
    }
    return PhoneOverlaySnap(
        x = if (side == PhoneOverlaySide.RIGHT) 0f else bounds.maxX,
        y = when (vertical) {
            PhoneOverlayVerticalEdge.BOTTOM -> bounds.minY
            PhoneOverlayVerticalEdge.TOP -> bounds.maxY
            null -> projectedY
        },
        side = side, vertical = vertical,
    )
}
