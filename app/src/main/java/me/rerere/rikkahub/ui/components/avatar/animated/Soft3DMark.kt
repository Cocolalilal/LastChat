package me.rerere.rikkahub.ui.components.avatar.animated

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import kotlin.math.hypot

/**
 * Soft-3D mark body — desktop Wh (`ov`→`av` / `wS`→`vS`).
 *
 * Desktop paints ONE flat solid fill of the projected silhouette (`bodyFill` string
 * via `Na(t)`). Lit face meshes (`Xx` / proof) are proof-only — never user-visible.
 * Mesh/hull stay under the hood for eye seating + silhouette projection only.
 * No per-face shade, no radial sphere brush, no wire edges.
 */
data class SolidSpec(
    val kind: String,
    val halfDepth: Float = 0.36f,
    val bevel: Float = 0.22f,
    val depth: Float = 0.62f,
    val axis: Int = 0,
)

object Soft3DMark {

    const val FILL = 0.98f

    fun withBodyTransform(
        drawScope: DrawScope,
        clip: ClipSample,
        sizePx: Float,
        markRadius: Float,
        morphProgress: Float = 1f,
        allowXBob: Boolean = false,
        block: DrawScope.() -> Unit,
    ) {
        val cx = drawScope.size.width / 2f
        val cy = drawScope.size.height / 2f
        val posScale = sizePx * 0.18f
        val ox = if (allowXBob) clip.position[0] * posScale * 0.12f else 0f
        val oy = -clip.position[1] * posScale
        val pathScale = (sizePx * 0.5f) / markRadius.coerceAtLeast(1f)
        val morph = morphProgress.coerceIn(0.88f, 1f)
        // Normalize desktop zoom (vn=0.6) so missing/differing scale tracks don't jump size.
        val clipScale = MarkClipEngine.visualScale(clip.scale)

        drawScope.withTransform({
            translate(left = cx + ox, top = cy + oy)
            scale(
                scaleX = pathScale * clipScale * morph,
                scaleY = pathScale * clipScale * morph,
                pivot = Offset.Zero,
            )
        }, block)
    }

    fun withMarkTransform(
        drawScope: DrawScope,
        clip: ClipSample,
        gazeX: Float,
        gazeY: Float,
        sizePx: Float,
        markRadius: Float,
        morphProgress: Float = 1f,
        block: DrawScope.() -> Unit,
    ) {
        withBodyTransform(drawScope, clip, sizePx, markRadius, morphProgress, false, block)
        @Suppress("UNUSED_VARIABLE") val _g = gazeX + gazeY
    }

    /**
     * Draw soft-3D body as a single flat fill (desktop `av` / `vS` normal path).
     * Returns projected hull for eye clip / evenodd seating.
     */
    fun drawBody(
        drawScope: DrawScope,
        bodyPath: Path,
        fill: Color,
        solid: SolidSpec,
        clip: ClipSample,
        gazeX: Float,
        gazeY: Float,
        sizePx: Float,
        morphProgress: Float = 1f,
        markRadius: Float = AvatarPackData.He,
        mainFillPath: Path? = null,
        calmFaceOn: Boolean = false,
        allowXBob: Boolean = false,
        shapeId: String = "",
    ): Path {
        val pitch = if (calmFaceOn) 0f else Math.toRadians(clip.rotationDeg[0].toDouble()).toFloat()
        val yaw = if (calmFaceOn) 0f else Math.toRadians(clip.rotationDeg[1].toDouble()).toFloat()
        val roll = if (calmFaceOn) 0f else Math.toRadians(clip.rotationDeg[2].toDouble()).toFloat()

        if (solid.kind == "sphere") {
            val hull = WhSphere.ellipsePath(markRadius)
            val paintPath = mainFillPath ?: hull
            withBodyTransform(
                drawScope = drawScope,
                clip = clip,
                sizePx = sizePx,
                markRadius = markRadius,
                morphProgress = morphProgress,
                allowXBob = allowXBob && !calmFaceOn,
            ) {
                // Desktop vS: fill="${Na(t)}" — flat ink, no radial glow unless glow/proof.
                drawPath(paintPath, fill)
            }
            @Suppress("UNUSED_EXPRESSION") gazeX
            @Suppress("UNUSED_EXPRESSION") gazeY
            return hull
        }

        val mesh = WhMesh.buildMeshDraw(
            bodyPath, solid, yaw, pitch, roll, markRadius, fill, shapeId,
        )
        // Prefer even-odd body+eyes when provided.
        // Calm face-on: paint the closed SVG contour (desktop front silhouette ≡ path).
        // Otherwise: bh/uv hull; fall back to bodyPath so we never stroke face gaps.
        val paintPath = when {
            mainFillPath != null -> mainFillPath
            calmFaceOn -> bodyPath
            !mesh.hull.isEmpty -> mesh.hull
            else -> bodyPath
        }
        withBodyTransform(
            drawScope = drawScope,
            clip = clip,
            sizePx = sizePx,
            markRadius = markRadius,
            morphProgress = morphProgress,
            allowXBob = allowXBob && !calmFaceOn,
        ) {
            // Desktop av: <use href="#body" fill="${bodyFill}"/> — one flat fill, never Xx faces.
            drawPath(paintPath, fill)
        }
        @Suppress("UNUSED_EXPRESSION") gazeX
        @Suppress("UNUSED_EXPRESSION") gazeY
        return if (!mesh.hull.isEmpty) mesh.hull else bodyPath
    }

    /** Project body via full bh hull (He-centered). */
    fun projectBody(
        bodyPath: Path,
        solid: SolidSpec,
        yaw: Float,
        pitch: Float,
        roll: Float,
        markRadius: Float,
        shapeId: String = "",
    ): Path {
        if (solid.kind == "sphere") return WhSphere.ellipsePath(markRadius)
        // Face-on: desktop front silhouette ≡ closed SVG contour — skip bh to avoid
        // false holes from multipolygon union edge cases in Create Avatar calm.
        if (kotlin.math.abs(yaw) < 1e-4f && kotlin.math.abs(pitch) < 1e-4f && kotlin.math.abs(roll) < 1e-4f) {
            return bodyPath
        }
        val mesh = WhMesh.buildMeshDraw(bodyPath, solid, yaw, pitch, roll, markRadius, Color.Black, shapeId)
        if (mesh.hull.isEmpty) return bodyPath
        // Sanity: reject degenerate/holey hulls that shrink far below the SVG contour
        // (false holes from multipolygon union edge cases under large euler).
        val hb = mesh.hull.getBounds()
        val bb = bodyPath.getBounds()
        val hArea = (hb.width * hb.height).coerceAtLeast(1e-3f)
        val bArea = (bb.width * bb.height).coerceAtLeast(1e-3f)
        return if (hArea < bArea * 0.35f) bodyPath else mesh.hull
    }

    fun evenOddBodyWithEyes(body: Path, leftEye: Path, rightEye: Path): Path =
        Path().apply {
            fillType = PathFillType.EvenOdd
            addPath(body)
            addPath(leftEye)
            addPath(rightEye)
        }

    fun withBodyClip(
        drawScope: DrawScope,
        clip: ClipSample,
        bodyPath: Path,
        sizePx: Float,
        markRadius: Float,
        morphProgress: Float,
        allowXBob: Boolean,
        block: DrawScope.() -> Unit,
    ) {
        withBodyTransform(drawScope, clip, sizePx, markRadius, morphProgress, allowXBob) {
            clipPath(bodyPath, block = block)
        }
    }

    fun drawEyeDartTrails(
        drawScope: DrawScope,
        clip: ClipSample,
        sizePx: Float,
        markRadius: Float,
        morphProgress: Float,
        trailPoints: List<Offset>,
        fill: Color,
        allowXBob: Boolean,
    ) {
        if (trailPoints.size < 2) return
        withBodyTransform(drawScope, clip, sizePx, markRadius, morphProgress, allowXBob) {
            val n = trailPoints.size
            for (i in 0 until n - 1) {
                val t = i / (n - 1f)
                val alpha = (0.18f * (1f - t)).coerceIn(0.04f, 0.22f)
                val r = markRadius * (0.045f + 0.025f * (1f - t))
                drawCircle(color = fill.copy(alpha = alpha), radius = r, center = trailPoints[i])
            }
        }
    }

    fun eyeDartSpeed(gazeX: Float, gazeY: Float, prevGazeX: Float, prevGazeY: Float): Float =
        hypot(gazeX - prevGazeX, gazeY - prevGazeY)

    fun shouldEmitDartTrail(speed: Float, lifecycle: MarkLifecycle): Boolean =
        lifecycle == MarkLifecycle.Working && speed > 4.5f
}
