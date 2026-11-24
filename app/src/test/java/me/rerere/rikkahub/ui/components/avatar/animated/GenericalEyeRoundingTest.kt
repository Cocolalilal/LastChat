package me.rerere.rikkahub.ui.components.avatar.animated

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs
import kotlin.math.hypot

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GenericalEyeRoundingTest {
    private fun load() = AvatarPackData.ensureLoaded(RuntimeEnvironment.getApplication())

    private fun circle(xs: FloatArray, ys: FloatArray, indices: List<Int>): Triple<Float, Float, Float> {
        val a = indices.first(); val b = indices[indices.size / 2]; val c = indices.last()
        val ax = xs[a]; val ay = ys[a]; val bx = xs[b]; val by = ys[b]; val cx = xs[c]; val cy = ys[c]
        val d = 2f * (ax * (by - cy) + bx * (cy - ay) + cx * (ay - by))
        val aa = ax * ax + ay * ay; val bb = bx * bx + by * by; val cc = cx * cx + cy * cy
        val x = (aa * (by - cy) + bb * (cy - ay) + cc * (ay - by)) / d
        val y = (aa * (cx - bx) + bb * (ax - cx) + cc * (bx - ax)) / d
        val r = hypot(ax - x, ay - y)
        for (i in indices) assertEquals("Corner became elliptical", r, hypot(xs[i] - x, ys[i] - y), 0.16f)
        return Triple(x, y, r)
    }

    @Test fun lidsAndSizeChangesRetainCircularCorners() {
        load()
        val original = requireNotNull(GenericalFace.face("Neutral")).left
        val corner = original.x.indices.filter { original.x[it] < 149f && original.y[it] < 214f }
        assertTrue(corner.size >= 4)
        for (shape in listOf(EyeShape(upper = 0.7f), EyeShape(upper = 0.35f), EyeShape(lower = 0.4f),
            EyeShape(sx = 0.8f, sy = 0.55f), EyeShape(upper = 0.6f, slant = 0.15f))) {
            for (side in 0..1) {
                val xs = original.x.copyOf(); val ys = original.y.copyOf()
                FaceRig.shapeOutline(xs, ys, side, shape)
                val radius = circle(xs, ys, corner).third
                val expected = minOf(39f, 110f * shape.sx / 2f,
                    145f * shape.lower * shape.upper * shape.sy / 2f)
                assertEquals("$shape radius", expected, radius, 0.7f)
            }
        }
    }

    @Test fun identityRetainsAuthoredOutlineExactlyAndLidsKeepTheirAnchor() {
        load()
        for (name in listOf("Neutral", "Happy", "Angry", "Sad", "Sleeping", "hurt or cute")) {
            val original = requireNotNull(GenericalFace.face(name)).left
            val xs = original.x.copyOf(); val ys = original.y.copyOf()
            FaceRig.shapeOutline(xs, ys, 0, EyeShape.ID)
            assertArrayEquals(original.x, xs, 0f); assertArrayEquals(original.y, ys, 0f)
        }
        val original = requireNotNull(GenericalFace.face("Neutral")).left
        for (shape in listOf(EyeShape(upper = 0.5f), EyeShape(lower = 0.5f))) {
            val xs = original.x.copyOf(); val ys = original.y.copyOf()
            FaceRig.shapeOutline(xs, ys, 0, shape)
            if (shape.upper < 1f) assertEquals(original.y.max(), ys.max(), 0.01f)
            else assertEquals(original.y.min(), ys.min(), 0.01f)
        }
    }

    @Test fun tiltCorrectionKeepsCornerCirclesAndDoesNotInventLeanForShortEyes() {
        load()
        val original = requireNotNull(GenericalFace.face("Neutral")).left
        val corner = original.x.indices.filter { original.x[it] < 149f && original.y[it] < 214f }
        val lx = original.x.copyOf(); val ly = original.y.copyOf()
        val rx = original.x.copyOf(); val ry = original.y.copyOf()
        FaceRig.shapeOutline(lx, ly, 0, EyeShape(rot = 12f))
        FaceRig.shapeOutline(rx, ry, 1, EyeShape(rot = 12f))
        val common = abs(TiltGuard.common(TiltGuard.topTilt(lx, ly), TiltGuard.topTilt(rx, ry)))
        assertTrue("Expected a lean to correct, got $common", common > 0.1f)
        TiltGuard.generical(lx, ly, rx, ry)
        assertEquals(39f, circle(lx, ly, corner).third, 0.7f)
        assertTrue(abs(TiltGuard.common(TiltGuard.topTilt(lx, ly), TiltGuard.topTilt(rx, ry))) < TiltGuard.DEAD)
        for (upper in listOf(0.8f, 0.4f, 0.15f)) {
            val xs = original.x.copyOf(); val ys = original.y.copyOf()
            FaceRig.shapeOutline(xs, ys, 0, EyeShape(upper = upper))
            assertEquals("Short round eyes must not acquire a false lean", 0f, TiltGuard.topTilt(xs, ys), 1f)
        }
    }

    @Test fun closedBarFitsNarrowEyesWithoutFlatteningItsCaps() {
        load()
        for (weighted in listOf(false, true)) {
            val closed = requireNotNull(if (weighted) GenericalFace.outlineWeighted(0, listOf("Neutral" to 1f), 1f)
                else GenericalFace.outline(0, "Neutral", "Neutral", 1f, 1f))
            val left = closed.x.min(); val mid = (closed.y.min() + closed.y.max()) / 2f
            val cap = closed.x.indices.filter { closed.x[it] < left + 7f }
            assertTrue(cap.size >= 4)
            val fitted = circle(closed.x, closed.y, cap)
            assertEquals(8.5f, fitted.third, 0.2f)
            assertEquals(mid, fitted.second, 0.2f)
            assertEquals(110f, closed.x.max() - left, 0.15f)
        }
    }

    @Test fun sleepingBarsResizeInTheirAuthoredFrameWithoutSqueezingTheirCaps() {
        load()
        val original = requireNotNull(GenericalFace.face("Sleeping")).left
        val cap = original.x.indices.filter { original.x[it] < original.x.min() + 7f }
        assertTrue(cap.size >= 4)
        val xs = original.x.copyOf(); val ys = original.y.copyOf()
        FaceRig.shapeOutline(xs, ys, 0, EyeShape(upper = 0.4f))
        assertEquals(3.4f, circle(xs, ys, cap).third, 0.4f)
    }

    @Test fun allExpressionsRemainFiniteAndContinuousThroughLidAndSizeChanges() {
        load()
        for (name in AvatarPackData.generical.expressions.keys) {
            val original = requireNotNull(GenericalFace.face(name)).left
            var previous: Pair<FloatArray, FloatArray>? = null
            for (step in 0..100) {
                val t = step / 100f
                val xs = original.x.copyOf(); val ys = original.y.copyOf()
                FaceRig.shapeOutline(xs, ys, 0, EyeShape(upper = 1f - 0.8f * t, lower = 1f - 0.3f * t,
                    sx = 1f + 0.1f * t, sy = 1f - 0.2f * t, slant = 0.15f * t))
                assertTrue(xs.all { it.isFinite() } && ys.all { it.isFinite() })
                previous?.let { (px, py) -> assertTrue("$name jumped at $step", xs.indices.maxOf {
                    hypot(xs[it] - px[it], ys[it] - py[it]) } < 4f) }
                previous = xs to ys
            }
        }
    }

    @Test fun renderGenericalLidAndExpressionContactSheet() {
        load()
        val expressions = listOf("Neutral", "Happy", "Sad", "Angry", "Sleeping")
        val shapes = listOf(EyeShape.ID, EyeShape(upper = 0.7f), EyeShape(upper = 0.4f),
            EyeShape(lower = 0.4f), EyeShape(sx = 0.85f, sy = 0.55f), EyeShape(upper = 0.65f, slant = 0.15f))
        val bitmap = Bitmap.createBitmap(shapes.size * 200, expressions.size * 200, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        canvas.drawColor(android.graphics.Color.rgb(28, 28, 28))
        val id = MarkShapes.PICKER_IDS.first()
        for ((row, expression) in expressions.withIndex()) for ((column, shape) in shapes.withIndex()) {
            canvas.save(); canvas.translate(column * 200f, row * 200f)
            CanvasDrawScope().draw(Density(2f), LayoutDirection.Ltr, Canvas(canvas), Size(200f, 200f)) {
                drawMark(requireNotNull(MarkShapes.resolve(id)), id, false, MotionFrame(
                    exprFrom = expression, exprTo = expression, exprWeights = listOf(expression to 1f),
                    face = FaceParams(shape, shape),
                ), false, 1f, Color(0xFF2A92FE), Color.White, Color.White)
            }
            canvas.restore()
        }
        System.getProperty("lastchat.renderDir")?.takeIf { it.isNotBlank() }?.let {
            java.io.File(it, "generical-corner-rounding.png").apply { parentFile?.mkdirs() }.outputStream().use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        }
        bitmap.recycle()
    }
}
