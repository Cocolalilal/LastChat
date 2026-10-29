package me.rerere.rikkahub.ui.components.richtext

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import me.rerere.rikkahub.ui.theme.LocalDarkMode
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Port of Jakub Antalik's Thinking Orb (orbits & globe mode).
 * Reference: https://github.com/Jakubantalik/thinking-orbs
 *
 * Renders particles on tilted orbits and depth-shaded 3D trajectories
 * directly on a 2D Compose Canvas with zero WebGL/filter overhead.
 */
enum class ThinkingOrbMode {
    ORBITS,
    GLOBE
}

private class Dot(
    val x: Float,
    val y: Float,
    val z: Float,
    var r: Float,
    val white: Float,
    val a: Float
)

private fun hashD(a: Double, b: Double): Double {
    val h = sin(a * 12.9898 + b * 78.233) * 43758.5453
    return h - kotlin.math.floor(h)
}

@Composable
fun ThinkingOrb(
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
    mode: ThinkingOrbMode = ThinkingOrbMode.ORBITS,
    speed: Float = 1.0f,
    isDark: Boolean = LocalDarkMode.current
) {
    val infiniteTransition = rememberInfiniteTransition(label = "ThinkingOrbLoop")
    val time by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI * 100).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 200000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ThinkingOrbTime"
    )

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val canvasSize = size.toPx().toDouble()
            val cx = canvasSize / 2.0
            val cy = canvasSize / 2.0
            val t = (time.toDouble() * speed)

            val dots = mutableListOf<Dot>()

            if (mode == ThinkingOrbMode.ORBITS) {
                // Tuned 64-preset: orbitN = 12, ghostN = 40, particles = 3
                val R = (canvasSize / 2.0) * 0.82
                val yaw = t * 0.12 * 1.885
                val tilt = 0.3
                val st = sin(tilt)
                val ct = cos(tilt)
                val sy = sin(yaw)
                val cyw = cos(yaw)

                val rs = Math.pow(canvasSize / 300.0, 0.6)
                val orbitN = 12
                val ghostN = 40
                val particles = 3

                for (orb in 0 until orbitN) {
                    val h1 = hashD(orb.toDouble(), 1.7)
                    val h2 = hashD(orb.toDouble(), 5.2)
                    val h3 = hashD(orb.toDouble(), 8.9)
                    val ro = R * (0.45 + 0.52 * h1)
                    val th = h1 * 2.0 * PI
                    val phi = acos(2.0 * h2 - 1.0)

                    val nx = sin(phi) * cos(th)
                    val ny = cos(phi)
                    val nz = sin(phi) * sin(th)

                    var ux = -ny
                    var uy = nx
                    val uz = 0.0
                    val ul = Math.max(1e-6, sqrt(ux * ux + uy * uy))
                    ux /= ul
                    uy /= ul

                    val vx = ny * uz - nz * uy
                    val vy = nz * ux - nx * uz
                    val vz = nx * uy - ny * ux
                    val orbSpeed = (0.25 + 0.55 * h3) * (if (h3 > 0.5) 1.0 else -1.0) * 1.885

                    // Ghost path
                    for (k in 0 until ghostN) {
                        val a = (k.toDouble() / ghostN) * 2.0 * PI
                        val gx = (ux * cos(a) + vx * sin(a)) * ro
                        val gy = (uy * cos(a) + vy * sin(a)) * ro
                        val gz = (uz * cos(a) + vz * sin(a)) * ro

                        val x1 = gx * cyw + gz * sy
                        val z1 = -gx * sy + gz * cyw
                        val y1 = gy * ct - z1 * st
                        val z2 = gy * st + z1 * ct

                        val px = cx + x1
                        val py = cy - y1
                        val depth = ((z2 / ro) + 1.0) / 2.0
                        val r = (0.9 * rs).toFloat().coerceAtLeast(0.4f)
                        val alpha = (0.5 * (0.4 + 0.6 * depth)).toFloat()

                        dots.add(
                            Dot(
                                x = px.toFloat(),
                                y = py.toFloat(),
                                z = z2.toFloat(),
                                r = r,
                                white = 0.72f,
                                a = alpha
                            )
                        )
                    }

                    // Running particles
                    for (m in 0 until particles) {
                        val a = t * orbSpeed + (m.toDouble() / particles) * 2.0 * PI + h2 * 6.0
                        val px3 = (ux * cos(a) + vx * sin(a)) * ro
                        val py3 = (uy * cos(a) + vy * sin(a)) * ro
                        val pz3 = (uz * cos(a) + vz * sin(a)) * ro

                        val x1 = px3 * cyw + pz3 * sy
                        val z1 = -px3 * sy + pz3 * cyw
                        val y1 = py3 * ct - z1 * st
                        val z2 = py3 * st + z1 * ct

                        val px = cx + x1
                        val py = cy - y1
                        val depth = ((z2 / ro) + 1.0) / 2.0
                        val r = ((1.2 + 1.6 * depth) * rs).toFloat().coerceAtLeast(0.6f)
                        val white = (0.3 - 0.22 * depth).toFloat()

                        dots.add(
                            Dot(
                                x = px.toFloat(),
                                y = py.toFloat(),
                                z = z2.toFloat(),
                                r = r,
                                white = white,
                                a = 1.0f
                            )
                        )
                    }
                }
            }

            // Z-sort far -> near so dots stack in realistic 3D depth
            dots.sortBy { it.z }

            // Paint dots
            for (dot in dots) {
                val w = dot.white.coerceIn(0f, 1f)
                val gray = if (isDark) (1f - w) else w
                val color = Color(gray, gray, gray, dot.a.coerceIn(0f, 1f))
                drawCircle(
                    color = color,
                    radius = dot.r,
                    center = Offset(dot.x, dot.y)
                )
            }
        }
    }
}
