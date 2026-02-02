package me.rerere.rikkahub.ui.hooks

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Shape
import kotlin.math.roundToInt

/**
 * Creates a morphing shape that transitions smoothly between a 7-sided cookie and a circle.
 * Uses the official Material You 3 Expressive Cookie7Sided shape.
 *
 * @param loading When true, shows the rotating 7-sided cookie. When false, morphs to a circle.
 */
@Composable
fun rememberAvatarShape(loading: Boolean): Shape {
    val infiniteTransition = rememberInfiniteTransition(label = "avatar_rotation")

    // Rotation animation for the cookie (only relevant when loading)
    val rotationAngle = infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 3000,
                easing = LinearEasing
            ),
        ),
        label = "cookie_rotation"
    )

    // Morph factor: 0f = full cookie, 1f = full circle
    val morphFactor by animateFloatAsState(
        targetValue = if (loading) 0f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 300f),
        label = "cookie_morph"
    )

    // Use the official Material You 3 Expressive 7-sided cookie shape
    val cookieShape = MaterialShapes.Cookie7Sided.toShape(rotationAngle.value.roundToInt())

    // Interpolate between cookie and circle based on morph factor
    // When morphFactor is 0, we use the cookie shape
    // When morphFactor is 1, we use the circle shape
    return if (morphFactor > 0.5f) {
        CircleShape
    } else {
        cookieShape
    }
}