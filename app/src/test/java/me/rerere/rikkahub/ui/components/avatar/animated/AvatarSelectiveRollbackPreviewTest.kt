package me.rerere.rikkahub.ui.components.avatar.animated

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Paint
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.random.Random

/** Focused checks and an opt-in sheet of the real renderer; no synthetic animation model. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class)
class AvatarSelectiveRollbackPreviewTest {
    @Before fun loadPack() {
        AvatarPackData.ensureLoaded(RuntimeEnvironment.getApplication())
    }

    @Test fun tiltedEmotionsKeepAnOpenEyeAndAReadableLean() {
        val neutral = WhSphereEyes.expressionPair("neutral", 1f)
        for (name in listOf("sad", "angry", "pouty")) {
            WhSphereEyes.expressionPair(name, 1f).forEachIndexed { index, eye ->
                val first = eye.pts.first()
                val last = eye.pts.last()
                val span = hypot(last[0] - first[0], last[1] - first[1]) + 2f * eye.wid
                val n = neutral[index]
                val normalSpan = hypot(n.pts.last()[0] - n.pts.first()[0], n.pts.last()[1] - n.pts.first()[1]) + 2f * n.wid
                assertTrue("$name should retain the neutral eye's open length", span >= normalSpan - 0.001f)
                val lean = atan2(abs(last[0] - first[0]), abs(last[1] - first[1])) * 180f / Math.PI.toFloat()
                assertTrue("$name should read through its lean, not a shortened eyelid", lean >= 30f)
                assertEquals("$name must not contain a resting closure", 0f, eye.closeK, 0f)
            }
        }
    }

    @Test fun openTiltedEyesStillCloseFullyDuringAnActualBlink() {
        for (name in listOf("Sad", "Worried", "Angry", "pouty")) {
            val open = WhSphereEyes.morphPair(name, name, 1f, 1f)
            val closed = WhSphereEyes.morphPair(name, name, 1f, 0.02f)
            open.forEach { assertEquals(0f, it.closeK, 0.001f) }
            closed.forEach { assertTrue("$name blink must still close", it.closeK >= 0.97f) }
        }
    }

    @Test fun liveSpinKeepsUniformScaleFixed() {
        val hints = AvatarMotionHints(isGenerating = true, hasStreamedTokens = true)
        for (path in SpinPath.entries) {
            val avatar = AvatarMotionState(Random(71))
            repeat(120) { avatar.tick(1f / 60f, hints, 1_800_000_000_000L) }
            avatar.debugSpin(true, false, path)
            var maxAngle = 0f
            repeat(360) {
                avatar.tick(1f / 60f, hints, 1_800_000_000_000L)
                val f = avatar.frame
                assertEquals("$path uniform scale", 1f, f.body.scale, 0f)
                assertTrue("$path finite squash X", f.body.squashX.isFinite())
                assertTrue("$path finite squash Y", f.body.squashY.isFinite())
                maxAngle = maxOf(maxAngle, abs(f.spinYaw), abs(f.spinPitch), abs(f.spinRoll))
            }
            assertTrue("$path should have actually spun", maxAngle > 180f)
        }
    }

    @Test fun happyToNeutralUsesAClosedMaskForBothEyeStyles() {
        val hints = AvatarMotionHints(isGenerating = true, hasStreamedTokens = true)
        for (grok in listOf(false, true)) {
            val avatar = AvatarMotionState(Random(4)).apply { isGrok = grok }
            avatar.debugReact("Happy", 20f)
            repeat(240) { avatar.tick(1f / 120f, hints, 1_800_000_000_000L) }
            assertTrue("Happy must be visible before testing its release", happyWeight(avatar.frame) > 0.7f)
            avatar.debugReact("Neutral", 20f)
            var lastWeight = happyWeight(avatar.frame)
            var hiddenSwap = false
            repeat(360) {
                avatar.tick(1f / 120f, hints, 1_800_000_000_000L)
                val f = avatar.frame
                val weight = happyWeight(f)
                if (lastWeight > 0.7f && weight < 0.1f) {
                    assertTrue("grok=$grok exposed the Happy release", f.maskBlink >= 0.98f)
                    hiddenSwap = true
                }
                lastWeight = weight
            }
            assertTrue("grok=$grok never committed its masked release", hiddenSwap)
        }
    }

    private fun happyWeight(frame: MotionFrame): Float = frame.exprWeights
        ?.sumOf { (name, weight) -> if (name == "Happy") weight.toDouble() else 0.0 }?.toFloat()
        ?: if (frame.exprTo == "Happy") 1f else 0f

    /** Set -Dlastchat.rollbackPreviewDir=... to write the actual 120px/32dp expression sheet. */
    @Test fun renderOpenGrokExpressionsAtEditorAndChatSize() {
        val path = System.getProperty("lastchat.rollbackPreviewDir")
        assumeTrue("opt-in actual-render sheet", !path.isNullOrBlank())
        val dir = File(path!!).apply { mkdirs() }
        val expressions = listOf("Neutral", "Sad", "Worried", "Angry", "pouty")
        val rows = listOf("blob", "heart").flatMap { shape -> listOf(-15f, 0f, 15f).map { shape to it } }
        val left = 140
        val cellWidth = 200
        val rowHeight = 218
        val header = 75
        val image = ImageBitmap(left + expressions.size * cellWidth, header + rows.size * rowHeight)
        val canvas = androidx.compose.ui.graphics.Canvas(image)
        canvas.drawRect(0f, 0f, image.width.toFloat(), image.height.toFloat(), androidx.compose.ui.graphics.Paint().apply { color = Color(0xFFF1F3F7) })
        val labels = android.graphics.Canvas(image.asAndroidBitmap())
        val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.rgb(25, 33, 45); textSize = 18f }
        labels.drawText("Grok open tilted expressions — actual renderer; 120px + 32dp at density 2", 16f, 25f, ink)
        expressions.forEachIndexed { index, expression -> labels.drawText(expression, (left + index * cellWidth + 32).toFloat(), 59f, ink) }
        for ((row, shapeYaw) in rows.withIndex()) {
            val (shapeId, yaw) = shapeYaw
            val shape = MarkShapes.resolve(shapeId) ?: error("Missing $shapeId")
            val top = header + row * rowHeight
            labels.drawText(shapeId, 16f, (top + 52).toFloat(), ink)
            labels.drawText("yaw ${yaw.toInt()}°", 16f, (top + 78).toFloat(), ink)
            for ((col, expression) in expressions.withIndex()) {
                val f = MotionFrame(body = BodyPose.REST.copy(yaw = yaw), exprFrom = expression, exprTo = expression, exprWeights = listOf(expression to 1f))
                for ((px, offsetY) in listOf(120 to 0, 64 to 132)) {
                    canvas.save()
                    canvas.translate((left + col * cellWidth + (cellWidth - px) / 2).toFloat(), (top + offsetY).toFloat())
                    CanvasDrawScope().draw(Density(2f), LayoutDirection.Ltr, canvas, Size(px.toFloat(), px.toFloat())) {
                        drawMark(shape, shapeId, true, f, false, 1f, Color(0xFF009FE0), Color(0xFF14171C), Color(0xFF14171C))
                    }
                    canvas.restore()
                }
            }
        }
        File(dir, "grok-open-expressions.png").outputStream().use { image.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
        File(dir, "grok-open-expressions.txt").writeText("Actual current drawMark renderer, no simulated drawing. Columns: ${expressions.joinToString()}. Rows: ${rows.joinToString()}. Each cell shows 120 physical pixels and 32dp at density2 (64 pixels). This sheet is the candidate appearance, not an approved result.\n")
    }

    /** Short studies run the restored engine itself; only spin/face triggers are scripted. */
    @Test fun renderRollbackMotionStudies() {
        val path = System.getProperty("lastchat.rollbackPreviewDir")
        assumeTrue("opt-in actual runtime recordings", !path.isNullOrBlank())
        val directory = File(path!!).apply { mkdirs() }
        val shape = MarkShapes.resolve("blob") ?: error("Missing blob")
        val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.rgb(25, 33, 45); textSize = 16f }
        val baseTime = 1_800_000_000_000L
        for ((scene, seconds) in listOf("original-behavior" to 30, "spin" to 8, "happy-mask" to 8)) {
            val frames = File(directory, "frames/$scene").apply { mkdirs() }
            val states = listOf(false, true).map { grok ->
                AvatarMotionState(Random(17)).apply {
                    isGrok = grok
                    personality = Personality.defaultFor(if (grok) "grok" else "generical")
                    if (scene == "happy-mask") debugReact("Happy", 20f)
                }
            }
            // Start focused studies from an established live pose, not the startup blend.
            if (scene != "original-behavior") repeat(240) {
                states.forEach { it.tick(1f / 120f, AvatarMotionHints(), baseTime) }
            }
            val image = ImageBitmap(560, 300)
            val bitmap = image.asAndroidBitmap()
            val canvas = androidx.compose.ui.graphics.Canvas(image)
            val labels = android.graphics.Canvas(bitmap)
            val scope = CanvasDrawScope()
            val trace = StringBuilder("frame,time,style,expression,lid,mask,spinYaw,spinPitch,spinRoll,scale\n")
            repeat(seconds * 30) { index ->
                val time = index / 30f
                if (scene == "spin" && index == 30) states.forEach { it.debugSpin(true, false, SpinPath.Turn) }
                if (scene == "happy-mask" && index == 60) states.forEach { it.debugReact("Neutral", 20f) }
                val hints = if (scene == "original-behavior") AvatarMotionHints(
                    isGenerating = time >= 10f,
                    hasStreamedTokens = time >= 20f,
                    idleMs = if (time < 10f) (time * 1000f).toLong() else 0L,
                ) else AvatarMotionHints()
                repeat(4) { step -> states.forEach { it.tick(1f / 120f, hints, baseTime + (time * 1000f).toLong() + step * 8) } }
                canvas.drawRect(0f, 0f, 560f, 300f, androidx.compose.ui.graphics.Paint().apply { color = Color(0xFFF1F3F7) })
                val caption = when (scene) {
                    "original-behavior" -> "Original behavior: " + when {
                        time < 10f -> "idle"
                        time < 20f -> "generating / thinking"
                        else -> "generating / streaming"
                    }
                    "spin" -> "Controlled spin triggered at 1s"
                    else -> "Controlled Happy to Neutral at 2s"
                }
                labels.drawText(caption, 16f, 23f, ink)
                labels.drawText("Generical", 104f, 49f, ink)
                labels.drawText("Grok", 399f, 49f, ink)
                for ((column, state) in states.withIndex()) {
                    val f = state.frame
                    for ((px, top) in listOf(120 to 64, 64 to 203)) {
                        canvas.save()
                        canvas.translate((column * 280 + (280 - px) / 2).toFloat(), top.toFloat())
                        scope.draw(Density(2f), LayoutDirection.Ltr, canvas, Size(px.toFloat(), px.toFloat())) {
                            drawMark(shape, "blob", column == 1, f, false, 1f, Color(0xFF009FE0), Color(0xFF14171C), Color(0xFF14171C))
                        }
                        canvas.restore()
                    }
                    trace.append("$index,$time,${if (column == 1) "Grok" else "Generical"},${f.exprTo},${f.lid},${f.maskBlink},${f.spinYaw},${f.spinPitch},${f.spinRoll},${f.body.scale}\n")
                }
                labels.drawText("Actual engine + renderer | 120px and 32dp at density 2 | ${"%.1f".format(java.util.Locale.ROOT, time)}s", 16f, 289f, ink)
                File(frames, "%04d.png".format(java.util.Locale.ROOT, index)).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
            File(frames, "trace.csv").writeText(trace.toString())
            bitmap.recycle()
        }
    }
}
