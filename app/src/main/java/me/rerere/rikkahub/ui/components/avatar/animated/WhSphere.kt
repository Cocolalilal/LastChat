package me.rerere.rikkahub.ui.components.avatar.animated

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin

/**
 * Desktop Grok Bot 0.68.1 `wS` / `vS` sphere body path.
 *
 * Wh routes `body==="sphere"` through wS (ellipse via Tr arcs) + vS flat fill.
 * Normal paint is flat Na(t) ink. Radial glow/proof only when desktop sets glow/isProof.
 * Compose default: flat ellipse fill — NOT qb loft remesh, NOT radial shade.
 */
object WhSphere {

    private val NT = floatArrayOf(-0.50140591f, 0.60168709f, 0.62174333f)
    private const val WS = 0.18f
    private const val UX = 0.4f

    /**
     * Desktop `Tr(radii, sweepSign)` body — full ellipse as Compose Path,
     * He-centered (origin at mark center).
     */
    fun ellipsePath(he: Float, squashX: Float = 1f, squashY: Float = 1f): Path {
        val rx = he * squashX
        val ry = he * squashY
        return Path().apply {
            addOval(
                androidx.compose.ui.geometry.Rect(
                    offset = Offset(-rx, -ry),
                    size = Size(rx * 2f, ry * 2f),
                ),
            )
        }
    }

    /**
     * Soft-3D sphere fill matching desktop glow shading direction `nt`.
     * Light comes from upper-left-front → bright spot offset.
     */
    fun sphereBrush(ink: Color, he: Float): Brush {
        fun lift(v: Float) = v + (1f - v) * UX
        val bright = Color(
            (lift(ink.red) * 1.05f).coerceIn(0f, 1f),
            (lift(ink.green) * 1.05f).coerceIn(0f, 1f),
            (lift(ink.blue) * 1.05f).coerceIn(0f, 1f),
            ink.alpha,
        )
        val mid = Color(
            (lift(ink.red) * (WS + (1f - WS) * 0.72f)).coerceIn(0f, 1f),
            (lift(ink.green) * (WS + (1f - WS) * 0.72f)).coerceIn(0f, 1f),
            (lift(ink.blue) * (WS + (1f - WS) * 0.72f)).coerceIn(0f, 1f),
            ink.alpha,
        )
        val dark = Color(
            (lift(ink.red) * (WS + (1f - WS) * 0.22f)).coerceIn(0f, 1f),
            (lift(ink.green) * (WS + (1f - WS) * 0.22f)).coerceIn(0f, 1f),
            (lift(ink.blue) * (WS + (1f - WS) * 0.22f)).coerceIn(0f, 1f),
            ink.alpha,
        )
        // Highlight toward light direction projected on XY (desktop nt ≈ [-0.5, 0.6])
        // Compose y-down: light Y flips → highlight above-left
        val hx = -NT[0] * he * 0.35f
        val hy = -NT[1] * he * 0.35f
        return Brush.radialGradient(
            colorStops = arrayOf(
                0f to bright,
                0.42f to mid,
                1f to dark,
            ),
            center = Offset(hx, hy),
            radius = he * 1.35f,
        )
    }

    /**
     * Draw sphere body in He-centered space (caller supplies body transform).
     */
    fun DrawScope.drawSphereBody(
        fill: Color,
        he: Float,
        yaw: Float,
        pitch: Float,
        // mild perspective squash from euler (desktop squash on sphere)
        roll: Float = 0f,
    ): Path {
        // Desktop squash from quat; approximate with pitch/yaw foreshortening
        val squashX = max(0.72f, cos(yaw) * 0.15f + 0.85f)
        val squashY = max(0.72f, cos(pitch) * 0.15f + 0.85f)
        @Suppress("UNUSED_VARIABLE") val _r = roll
        val path = ellipsePath(he, squashX, squashY)
        // Flat fill — desktop vS uses Na(t) string ink unless glow/proof.
        drawPath(path, fill)
        return path
    }

    /** Sphere surfaceZ for loft-style eye seating: z = sqrt(r² - x² - y²). */
    fun surfaceZ(radius: Float = 1f): (Float, Float) -> Float = { x, y ->
        val o = (x * x + y * y) / (radius * radius)
        if (o >= 1f) 0f else radius * kotlin.math.sqrt(1f - o)
    }

    fun eyeSeat(): WhMesh.Seat = WhMesh.Seat.LoftSeat(surfaceZ(1f))
}
