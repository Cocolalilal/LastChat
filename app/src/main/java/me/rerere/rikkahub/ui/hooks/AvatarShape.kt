package me.rerere.rikkahub.ui.hooks

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.material3.MaterialShapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.graphics.shapes.Morph
import kotlinx.coroutines.isActive

/**
 * Creates a morphing shape that transitions smoothly between a 6-sided cookie and a circle.
 * Uses the official Material You 3 Expressive Cookie6Sided shape with proper Morph animation.
 *
 * @param loading When true, shows the rotating 6-sided cookie. When false, morphs to a circle.
 */
@Composable
fun rememberAvatarShape(loading: Boolean): Shape {
    // Create the morph between cookie and circle
    val cookiePolygon = MaterialShapes.Cookie6Sided
    val circlePolygon = MaterialShapes.Circle
    val morph = remember(cookiePolygon, circlePolygon) { Morph(cookiePolygon, circlePolygon) }
    
    // Rotation animation for when loading
    val rotation = remember { Animatable(0f) }
    LaunchedEffect(loading) {
        if (loading) {
            // Smooth continuous rotation at a steady pace (one full rotation every 3s)
            val degreesPerSecond = 120f
            var lastFrameTimeNanos = 0L
            while (isActive) {
                val frameTime = withFrameNanos { it }
                if (lastFrameTimeNanos == 0L) {
                    lastFrameTimeNanos = frameTime
                    continue
                }
                val deltaSeconds = (frameTime - lastFrameTimeNanos) / 1_000_000_000f
                lastFrameTimeNanos = frameTime
                val next = (rotation.value + degreesPerSecond * deltaSeconds) % 360f
                rotation.snapTo(next)
            }
        } else {
            // Reset to 0 when not loading
            rotation.animateTo(
                targetValue = 0f,
                animationSpec = spring(dampingRatio = 0.5f, stiffness = 400f)
            )
        }
    }

    // Morph factor: 0f = full cookie, 1f = full circle
    val morphProgress by animateFloatAsState(
        targetValue = if (loading) 0f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 400f),
        label = "cookie_morph"
    )

    val rotationDegrees = rotation.value

    return remember(morph, morphProgress, rotationDegrees) {
        GenericShape { size, _ ->
            // Get the cubics for the current morph progress
            val cubics = morph.asCubics(morphProgress)
            
            if (cubics.isNotEmpty()) {
                // Calculate bounds
                var minX = Float.MAX_VALUE
                var minY = Float.MAX_VALUE
                var maxX = Float.MIN_VALUE
                var maxY = Float.MIN_VALUE
                
                cubics.forEach { cubic ->
                    val points = listOf(
                        cubic.anchor0X, cubic.anchor0Y,
                        cubic.control0X, cubic.control0Y,
                        cubic.control1X, cubic.control1Y,
                        cubic.anchor1X, cubic.anchor1Y
                    )
                    for (i in points.indices step 2) {
                        minX = minOf(minX, points[i])
                        minY = minOf(minY, points[i + 1])
                        maxX = maxOf(maxX, points[i])
                        maxY = maxOf(maxY, points[i + 1])
                    }
                }
                
                val boundsWidth = maxX - minX
                val boundsHeight = maxY - minY
                
                if (boundsWidth > 0 && boundsHeight > 0) {
                    val scaleX = size.width / boundsWidth
                    val scaleY = size.height / boundsHeight
                    val scale = minOf(scaleX, scaleY)
                    
                    // Calculate center offset
                    val offsetX = (size.width - boundsWidth * scale) / 2f - minX * scale
                    val offsetY = (size.height - boundsHeight * scale) / 2f - minY * scale
                    
                    // Apply rotation if needed
                    val centerX = size.width / 2f
                    val centerY = size.height / 2f
                    val rotationRad = Math.toRadians(rotationDegrees.toDouble()).toFloat()
                    val cos = kotlin.math.cos(rotationRad)
                    val sin = kotlin.math.sin(rotationRad)
                    
                    fun transformPoint(x: Float, y: Float): Pair<Float, Float> {
                        // Scale and translate
                        var tx = x * scale + offsetX
                        var ty = y * scale + offsetY
                        
                        // Rotate around center
                        if (rotationDegrees != 0f) {
                            val dx = tx - centerX
                            val dy = ty - centerY
                            tx = centerX + (dx * cos - dy * sin)
                            ty = centerY + (dx * sin + dy * cos)
                        }
                        
                        return Pair(tx, ty)
                    }
                    
                    // Build the path from cubics
                    cubics.forEachIndexed { index, cubic ->
                        val (ax0, ay0) = transformPoint(cubic.anchor0X, cubic.anchor0Y)
                        val (cx0, cy0) = transformPoint(cubic.control0X, cubic.control0Y)
                        val (cx1, cy1) = transformPoint(cubic.control1X, cubic.control1Y)
                        val (ax1, ay1) = transformPoint(cubic.anchor1X, cubic.anchor1Y)
                        
                        if (index == 0) {
                            moveTo(ax0, ay0)
                        }
                        cubicTo(cx0, cy0, cx1, cy1, ax1, ay1)
                    }
                    
                    close()
                }
            }
        }
    }
}
