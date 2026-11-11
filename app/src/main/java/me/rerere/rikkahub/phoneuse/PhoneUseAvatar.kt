package me.rerere.rikkahub.phoneuse

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.material3.MaterialShapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.Avatar
import me.rerere.rikkahub.ui.components.avatar.animated.AnimatedMarkAvatar
import me.rerere.rikkahub.ui.components.avatar.animated.AvatarMotionHints
import me.rerere.rikkahub.ui.components.avatar.animated.Personality
import me.rerere.rikkahub.ui.components.avatar.animated.ProvideAvatarMotionHints
import me.rerere.rikkahub.ui.components.ui.ReadOnlyAvatar
import me.rerere.rikkahub.ui.motion.rememberSystemMotionPolicy

@Composable
internal fun PhoneUseAvatar(assistant: Assistant, modifier: Modifier = Modifier, active: Boolean) {
    val animate = !rememberSystemMotionPolicy().reduceMotion
    when (val avatar = assistant.avatar) {
        is Avatar.Animated -> ProvideAvatarMotionHints(AvatarMotionHints(isGenerating = active)) {
            AnimatedMarkAvatar(
                shapeId = avatar.shape, eyeType = avatar.eyeType, colorHex = avatar.colorHex,
                colorPreset = avatar.colorPreset, eyeColorHex = avatar.eyeColorHex,
                eyeShade = avatar.eyeShade, personality = Personality.resolve(avatar.eyeType, avatar.traits),
                isLoading = active, animate = animate, modifier = modifier,
            )
        }
        else -> ReadOnlyAvatar(assistant.name, avatar, modifier, shape = rememberPhoneUseShape(active && animate))
    }
}

/** A continuous loop through soft circles; no rotating frame or restarting on each phone action. */
@Composable
private fun rememberPhoneUseShape(active: Boolean): Shape {
    if (!active) return CircleShape
    val polygons = remember {
        listOf(
            MaterialShapes.Circle,
            MaterialShapes.Cookie6Sided,
            RoundedPolygon(5, rounding = CornerRounding(0.55f, 0.7f)),
            RoundedPolygon(8, rounding = CornerRounding(0.35f, 0.7f)),
            RoundedPolygon(4, rounding = CornerRounding(0.8f, 0.7f)),
        )
    }
    val morphs = remember(polygons) { polygons.indices.map { Morph(polygons[it], polygons[(it + 1) % polygons.size]) } }
    val clock = rememberInfiniteTransition(label = "phoneAvatarShapes")
    val phase by clock.animateFloat(0f, morphs.size.toFloat(),
        infiniteRepeatable(tween(24000, easing = LinearEasing)), label = "phoneAvatarMorph")
    val morph = morphs[phase.toInt() % morphs.size]
    val progress = phase % 1f
    return remember(morph, progress) {
        GenericShape { size, _ ->
            val cubics = morph.asCubics(progress)
            val points = cubics.flatMap { listOf(it.anchor0X to it.anchor0Y, it.control0X to it.control0Y,
                it.control1X to it.control1Y, it.anchor1X to it.anchor1Y) }
            if (points.isEmpty()) return@GenericShape
            val minX = points.minOf { it.first }; val minY = points.minOf { it.second }
            val width = (points.maxOf { it.first } - minX).coerceAtLeast(0.001f)
            val height = (points.maxOf { it.second } - minY).coerceAtLeast(0.001f)
            val scale = minOf(size.width / width, size.height / height) * 0.96f
            val dx = (size.width - width * scale) / 2 - minX * scale
            val dy = (size.height - height * scale) / 2 - minY * scale
            cubics.forEachIndexed { i, c ->
                if (i == 0) moveTo(c.anchor0X * scale + dx, c.anchor0Y * scale + dy)
                cubicTo(c.control0X * scale + dx, c.control0Y * scale + dy,
                    c.control1X * scale + dx, c.control1Y * scale + dy,
                    c.anchor1X * scale + dx, c.anchor1Y * scale + dy)
            }
            close()
        }
    }
}
