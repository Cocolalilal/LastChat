package me.rerere.rikkahub.phoneuse

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Shape
import kotlinx.coroutines.isActive
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.roundToInt
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.Avatar
import me.rerere.rikkahub.ui.components.avatar.animated.AnimatedMarkAvatar
import me.rerere.rikkahub.ui.components.avatar.animated.AvatarMotionHints
import me.rerere.rikkahub.ui.components.avatar.animated.ProvideAvatarMotionHints
import me.rerere.rikkahub.ui.components.ui.ReadOnlyAvatar
import me.rerere.rikkahub.ui.hooks.AVATAR_LOADING_ROTATION_DEGREES_PER_SECOND
import me.rerere.rikkahub.ui.motion.rememberSystemMotionPolicy

@Composable
internal fun PhoneUseAvatar(assistant: Assistant, modifier: Modifier = Modifier, active: Boolean) {
    val animate = !rememberSystemMotionPolicy().reduceMotion
    when (val avatar = assistant.avatar) {
        is Avatar.Animated -> ProvideAvatarMotionHints(AvatarMotionHints(isGenerating = active)) {
            AnimatedMarkAvatar(
                shapeId = avatar.shape, eyeType = avatar.eyeType, colorHex = avatar.colorHex,
                colorPreset = avatar.colorPreset, eyeColorHex = avatar.eyeColorHex,
                eyeShade = avatar.eyeShade,
                isLoading = active, animate = animate, modifier = modifier,
            )
        }
        else -> ReadOnlyAvatar(assistant.name, avatar, modifier, shape = rememberPhoneUseShape(active, animate))
    }
}

/** Retain the current outline so completion can return to a circle alongside the pill spring. */
@Composable
internal fun rememberPhoneUseShape(active: Boolean, animate: Boolean = true): Shape {
    val circle = remember { phoneAvatarContour(0, 0f) }
    val contours = remember {
        listOf(6 to 0.065f, 5 to 0.045f, 8 to 0.035f, 4 to 0.055f)
            .map { (lobes, depth) -> phoneAvatarContour(lobes, depth) }
    }
    val phase = remember { Animatable(0f) }
    val workingAmount = remember { Animatable(0f, visibilityThreshold = 0.0005f) }
    LaunchedEffect(active, animate) {
        if (animate && active) {
            while (isActive) {
                val next = phase.value.toInt() + 1f
                val duration = ((next - phase.value) * 680f).roundToInt().coerceAtLeast(1)
                phase.animateTo(next, tween(duration, easing = FastOutSlowInEasing))
                // The last contour blends directly into the first, so wrapping the clock
                // preserves the same working outline without going through a circle.
                if (phase.value >= contours.size) phase.snapTo(phase.value % contours.size)
            }
        }
    }
    LaunchedEffect(active, animate) {
        if (!animate) workingAmount.snapTo(0f)
        else workingAmount.animateTo(if (active) 1f else 0f,
            spring(PHONE_MOTION_DAMPING, PHONE_MOTION_STIFFNESS, visibilityThreshold = 0.0005f))
    }
    val index = phase.value.toInt() % contours.size
    val from = contours[index]
    val to = contours[(index + 1) % contours.size]
    val blend = phase.value % 1f
    val amount = workingAmount.value.coerceIn(0f, 1f)
    // Keep chat-speed rotation through the circle return. Any angular settling happens
    // only once the outline is circular, so finishing cannot suddenly speed up the clip.
    val rotation = rememberPhoneUseRotation(active || amount > 0.0005f, animate)
    return remember(circle, from, to, blend, amount, rotation) {
        GenericShape { size, _ ->
            fun coordinate(index: Int): Float {
                val contour = from[index] + (to[index] - from[index]) * blend
                return circle[index] + (contour - circle[index]) * amount
            }
            fun x(index: Int) = coordinate(index) * size.width
            fun y(index: Int) = coordinate(index) * size.height
            moveTo(x(0), y(1))
            for (index in circle.indices step 8) {
                cubicTo(x(index + 2), y(index + 3), x(index + 4), y(index + 5), x(index + 6), y(index + 7))
            }
            close()
            transform(Matrix().apply {
                translate(size.width / 2f, size.height / 2f)
                rotateZ(rotation)
                translate(-size.width / 2f, -size.height / 2f)
            })
        }
    }
}

/** Radial cubic contours share angles and a centered circle, avoiding feature-matching shrinkage. */
private fun phoneAvatarContour(lobes: Int, depth: Float): FloatArray {
    val segments = 64
    val step = (2.0 * PI / segments).toFloat()
    val result = FloatArray(segments * 8)
    fun point(angle: Float): FloatArray {
        val radius = 0.5f - depth * (1f - cos(lobes * angle)) * 0.5f
        val slope = -depth * lobes * sin(lobes * angle) * 0.5f
        val cosine = cos(angle)
        val sine = sin(angle)
        return floatArrayOf(0.5f + radius * cosine, 0.5f + radius * sine,
            slope * cosine - radius * sine, slope * sine + radius * cosine)
    }
    for (segment in 0 until segments) {
        val start = point(segment * step)
        val end = point((segment + 1) * step)
        val index = segment * 8
        result[index] = start[0]
        result[index + 1] = start[1]
        result[index + 2] = start[0] + start[2] * step / 3f
        result[index + 3] = start[1] + start[3] * step / 3f
        result[index + 4] = end[0] - end[2] * step / 3f
        result[index + 5] = end[1] - end[3] * step / 3f
        result[index + 6] = end[0]
        result[index + 7] = end[1]
    }
    return result
}

/** Rotate the clipping contour at chat speed; keep the underlying photo upright. */
@Composable
internal fun rememberPhoneUseRotation(active: Boolean, animate: Boolean): Float {
    val rotation = remember { Animatable(0f) }
    LaunchedEffect(active, animate) {
        when {
            !animate -> rotation.snapTo(0f)
            !active -> rotation.animateTo(0f, spring(PHONE_MOTION_DAMPING, PHONE_MOTION_STIFFNESS))
            else -> {
                var previous = withFrameNanos { it }
                while (isActive) {
                    val frame = withFrameNanos { it }
                    val delta = (frame - previous) / 1_000_000_000f
                    previous = frame
                    // Keep the equivalent angle near zero so completion never rewinds a full turn.
                    val next = rotation.value + AVATAR_LOADING_ROTATION_DEGREES_PER_SECOND * delta
                    rotation.snapTo(((next + 180f) % 360f + 360f) % 360f - 180f)
                }
            }
        }
    }
    return rotation.value
}
