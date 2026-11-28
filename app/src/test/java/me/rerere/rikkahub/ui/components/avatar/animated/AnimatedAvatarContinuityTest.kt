package me.rerere.rikkahub.ui.components.avatar.animated

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.random.Random

/**
 * Guards against animated avatar hard cuts and verifies Generical squircle lid shaping:
 *  - Generical Focused/lidded eyes flatten pressed corners smoothly (no rigid circular 9-slice)
 *    and remain continuous across slant/upper thresholds;
 *  - Frame 0 -> frame 1 eases out of rest instead of snapping to an off-centre beat;
 *  - Behind-blink and Happy expression swaps only attach to fresh blinks and commit behind
 *    bit-for-bit identical closed bars on both Generical and Grok;
 *  - Mid-blink startles, dumbfounded freezes, Gesture.Attend re-triggers, spins, and long
 *    mixed sessions never hard-cut any rendered channel.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AnimatedAvatarContinuityTest {

    private fun load() = AvatarPackData.ensureLoaded(RuntimeEnvironment.getApplication())

    @Test
    fun genericalSquircleSmoothingIsUnsqueezedAndContinuous() {
        load()
        val neutral = requireNotNull(GenericalFace.face("Neutral")).left

        // 1) Neutral uses Figma 100% corner smoothing (full-span squircle, not a circular RoundRect).
        val topMinY = neutral.y.min()
        val minX = neutral.x.min(); val maxX = neutral.x.max()
        val midX = (minX + maxX) * 0.5f
        // On a plain RoundRect(w=110, rx=39), points with |x - midX| <= 16 sit flat at y == topMinY.
        // With 100% Figma corner smoothing (p = 55), curvature begins right away so points around
        // |x - midX| == 24..32 have already curved smoothly below topMinY.
        val shoulder = neutral.x.indices.filter { neutral.y[it] < neutral.cy && abs(neutral.x[it] - midX) in 20f..34f }
        assertTrue("Expected shoulder samples on Neutral", shoulder.isNotEmpty())
        assertTrue(
            "Neutral should have Figma 100% squircle shoulder curvature",
            shoulder.any { neutral.y[it] > topMinY + 0.08f },
        )

        // 2) Pressed lids (Focused, Drowsy, Glee) tighten their pressed corners isotropically:
        //    horizontal and vertical corner ratios match (never squeezed!) and Focused tightens
        //    rather than keeping Neutral's oversized corner span.
        val (origTopSpan, origBotSpan) = SquircleEyeGeometry.cornerSpans(
            neutral.x, neutral.y, minX, maxX, topMinY, neutral.y.max(),
        )
        for (accent in listOf(FaceAccent.Focused, FaceAccent.Drowsy, FaceAccent.Glee, FaceAccent.Startled)) {
            val shape = FaceRig(Random(1)).accentPreview(accent).left
            val xs = neutral.x.copyOf(); val ys = neutral.y.copyOf()
            FaceRig.shapeOutline(xs, ys, 0, shape)
            val sMinX = xs.min(); val sMaxX = xs.max()
            val sMinY = ys.min(); val sMaxY = ys.max()
            // Top-left corner sample around the 45° diagonal of Neutral must remain symmetric (dx ≈ dy).
            val origDiagIdx = neutral.x.indices
                .filter { neutral.x[it] < midX && neutral.y[it] < neutral.cy }
                .minBy { abs((neutral.x[it] - minX) - (neutral.y[it] - topMinY)) }
            val origRatio = (neutral.y[origDiagIdx] - topMinY) / (neutral.x[origDiagIdx] - minX)
            val dxNew = xs[origDiagIdx] - sMinX
            val dyNew = ys[origDiagIdx] - sMinY
            val ratio = if (dxNew > 1e-3f) dyNew / dxNew else 0f
            assertEquals("$accent top-left corner was squeezed (dx=$dxNew, dy=$dyNew)", origRatio, ratio, 0.01f)
            if (accent == FaceAccent.Focused || accent == FaceAccent.Drowsy) {
                val (newTopSpan, _) = SquircleEyeGeometry.cornerSpans(xs, ys, sMinX, sMaxX, sMinY, sMaxY)
                assertTrue("$accent top corner should tighten ($newTopSpan vs $origTopSpan)", newTopSpan < origTopSpan - 4f)
            }
        }
        assertTrue(origBotSpan > 35f)

        // 3) Closed-bar caps stay unsqueezed (rx ≈ ry ≈ 8.5) when closing a narrow eye.
        val closed = requireNotNull(GenericalFace.outlineWeighted(0, listOf("Neutral" to 1f), 1f))
        val cMinX = closed.x.min(); val cMidY = (closed.y.min() + closed.y.max()) * 0.5f
        val capHeight = (closed.y.max() - closed.y.min()) * 0.5f
        val leftCapApex = closed.x.indices.minBy { closed.x[it] }
        val nearCap = closed.x.indices.filter { closed.x[it] < cMinX + capHeight * 0.85f }
        assertTrue(nearCap.size >= 4)
        for (idx in nearCap) {
            val rDist = hypot(closed.x[idx] - (cMinX + capHeight), closed.y[idx] - cMidY)
            assertEquals("Closed bar cap was squeezed at idx=$idx (apex=$leftCapApex)", capHeight, rDist, 0.45f)
        }

        // 4) Sweeping slant continuously across -0.05 (hard <-> soft lid) must never jump.
        var prevX: FloatArray? = null
        var prevY: FloatArray? = null
        for (step in 0..80) {
            val slant = -0.12f + 0.24f * (step / 80f)
            val xs = neutral.x.copyOf(); val ys = neutral.y.copyOf()
            FaceRig.shapeOutline(xs, ys, 0, EyeShape(upper = 0.72f, lower = 0.88f, slant = slant))
            prevX?.let { px ->
                val py = prevY!!
                val maxDelta = xs.indices.maxOf { hypot(xs[it] - px[it], ys[it] - py[it]) }
                assertTrue("Slant jump at slant=$slant: $maxDelta px", maxDelta < 2.5f)
            }
            prevX = xs; prevY = ys
        }

        // 5) Sweeping upper across 1.0 with non-zero slant must never jump.
        prevX = null; prevY = null
        for (step in 0..60) {
            val upper = 0.85f + 0.30f * (step / 60f)
            val xs = neutral.x.copyOf(); val ys = neutral.y.copyOf()
            FaceRig.shapeOutline(xs, ys, 0, EyeShape(upper = upper, slant = 0.12f))
            prevX?.let { px ->
                val py = prevY!!
                val maxDelta = xs.indices.maxOf { hypot(xs[it] - px[it], ys[it] - py[it]) }
                assertTrue("Upper jump at upper=$upper: $maxDelta px", maxDelta < 2.5f)
            }
            prevX = xs; prevY = ys
        }

        // Optional visual review sheet when -Dlastchat.renderDir is set.
        System.getProperty("lastchat.renderDir")?.takeIf { it.isNotBlank() }?.let { dir ->
            val rig = FaceRig(Random(1))
            val items = listOf(
                "Neutral" to FaceParams.ID,
                "Neutral" to rig.accentPreview(FaceAccent.Focused),
                "Neutral" to rig.accentPreview(FaceAccent.Drowsy),
                "Neutral" to rig.accentPreview(FaceAccent.Glee),
                "Neutral" to rig.accentPreview(FaceAccent.Curious),
                "Neutral" to rig.accentPreview(FaceAccent.Annoyed),
                "Neutral" to rig.accentPreview(FaceAccent.Smug),
                "Neutral" to rig.accentPreview(FaceAccent.Startled),
                "Neutral" to rig.accentPreview(FaceAccent.Sad),
                "angered or concentrated" to FaceParams.ID,
                "Bored" to FaceParams.ID,
                "Happy" to FaceParams.ID,
                "Surprised" to FaceParams.ID,
                "Angry" to FaceParams.ID,
                "Sleeping" to FaceParams.ID,
            )
            val cols = 5
            val rows = (items.size + cols - 1) / cols
            val cell = 220
            val bmp = Bitmap.createBitmap(cols * cell, rows * cell, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bmp)
            canvas.drawColor(android.graphics.Color.rgb(24, 26, 32))
            val shape = requireNotNull(MarkShapes.resolve("blob"))
            for ((idx, pair) in items.withIndex()) {
                val (expr, face) = pair
                canvas.save()
                canvas.translate((idx % cols) * cell.toFloat(), (idx / cols) * cell.toFloat())
                CanvasDrawScope().draw(Density(2f), LayoutDirection.Ltr, Canvas(canvas), Size(cell.toFloat(), cell.toFloat())) {
                    drawMark(
                        shape, "blob", false,
                        MotionFrame(exprFrom = expr, exprTo = expr, exprWeights = listOf(expr to 1f), face = face),
                        false, 1f, Color(0xFF2A92FE), Color.White, Color(0xFF87D2E9),
                    )
                }
                canvas.restore()
            }
            java.io.File(dir, "generical-squircle-sheet.png").apply { parentFile?.mkdirs() }
                .outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bmp.recycle()
        }
    }

    @Test
    fun canonicalBlinkMaskMatchesAcrossAllExpressionPairs() {
        load()
        fun render(shapeId: String, expression: String, grok: Boolean, closure: Float): Bitmap {
            val bitmap = Bitmap.createBitmap(180, 180, Bitmap.Config.ARGB_8888)
            val shape = requireNotNull(MarkShapes.resolve(shapeId))
            CanvasDrawScope().draw(Density(2f), LayoutDirection.Ltr, Canvas(android.graphics.Canvas(bitmap)), Size(180f, 180f)) {
                drawMark(
                    shape, shapeId, grok,
                    MotionFrame(
                        exprFrom = expression, exprTo = expression,
                        exprWeights = listOf(expression to 1f),
                        lid = 1f - closure, maskBlink = closure,
                    ),
                    false, 1f, Color(0xFF2A92FE), Color.White, Color.White,
                )
            }
            return bitmap
        }
        val expressions = listOf("Neutral", "Happy", "Angry", "Sad", "Surprised", "confused", "angered or concentrated", "hurt or cute")
        for (grok in listOf(false, true)) {
            for (shape in listOf("blob", "square", "heart")) {
                val reference = render(shape, "Neutral", grok, 0.98f)
                for (expr in expressions) {
                    val candidate = render(shape, expr, grok, 0.98f)
                    assertTrue(
                        "$shape grok=$grok expr=$expr did not match canonical closed bar at maskBlink=0.98",
                        reference.sameAs(candidate),
                    )
                    candidate.recycle()
                }
                reference.recycle()
            }
        }
    }

    @Test
    fun firstFrameFreezeStartleAndLongSessionsHaveNoHardCuts() {
        load()
        val dt = 1f / 60f
        val failures = ArrayList<String>()

        fun checkPair(tag: String, prev: MotionFrame, curr: MotionFrame) {
            val spin = curr.spinYaw != 0f || curr.spinPitch != 0f || curr.spinRoll != 0f ||
                prev.spinYaw != 0f || prev.spinPitch != 0f || prev.spinRoll != 0f
            // Rendered yaw/pitch (through softCap) must NEVER wrap across ±180° and jump.
            val prevYaw = softCap(prev.body.yaw, 19.5f)
            val currYaw = softCap(curr.body.yaw, 19.5f)
            val prevPitch = softCap(prev.body.pitch, 14f)
            val currPitch = softCap(curr.body.pitch, 14f)
            if (abs(currYaw - prevYaw) > 8f) failures += "$tag: softCap yaw jumped $prevYaw -> $currYaw"
            if (abs(currPitch - prevPitch) > 8f) failures += "$tag: softCap pitch jumped $prevPitch -> $currPitch"
            if (!spin) {
                val dRoll = abs(MarkClipEngine.wrap180(curr.body.roll - prev.body.roll))
                if (dRoll > 9f) failures += "$tag: roll jumped $dRoll°"
            }
            val dLook = maxOf(abs(curr.headLookX - prev.headLookX), abs(curr.headLookY - prev.headLookY))
            if (dLook > 0.16f) failures += "$tag: headLook jumped $dLook"
            val dLead = maxOf(abs(curr.leadX - prev.leadX), abs(curr.leadY - prev.leadY))
            if (dLead > 0.06f) failures += "$tag: lead jumped $dLead"
            // A fast 40ms blink legitimately closes by up to ~0.60 in one 60fps frame, while
            // opening (including startle openFast) is smoother (<= 0.48/frame) — never a 0->1 snap.
            val dLid = curr.lid - prev.lid
            if (dLid > 0.48f) failures += "$tag: lid snapped open ${prev.lid} -> ${curr.lid}"
            if (dLid < -0.65f) failures += "$tag: lid snapped shut ${prev.lid} -> ${curr.lid}"
            // maskBlink must start from near-zero (never latch onto an already-closed blink).
            if (prev.maskBlink == 0f && curr.maskBlink > 0.35f) {
                failures += "$tag: maskBlink latched mid-blink 0 -> ${curr.maskBlink}"
            }
            if (prev.maskBlink > 0.35f && curr.maskBlink == 0f) {
                failures += "$tag: maskBlink dropped abruptly ${prev.maskBlink} -> 0"
            }
            // If expression weights jump instantaneously, curr MUST be behind the opaque canonical
            // closed mask (>= 0.85 -> smootherstep == 1f) so the swap is bit-for-bit invisible.
            val prevW = (prev.exprWeights ?: listOf(prev.exprTo to 1f)).toMap()
            val currW = (curr.exprWeights ?: listOf(curr.exprTo to 1f)).toMap()
            val keys = prevW.keys + currW.keys
            val exprStep = keys.maxOfOrNull { abs((currW[it] ?: 0f) - (prevW[it] ?: 0f)) } ?: 0f
            if (exprStep > 0.35f && curr.maskBlink < 0.85f) {
                failures += "$tag: visible expression jump $exprStep (${prevW} -> ${currW}) with maskBlink=${prev.maskBlink}->${curr.maskBlink}, lid=${prev.lid}->${curr.lid}"
            }
        }

        // 1) First-frame continuity from initial MotionFrame() across all lifecycles.
        val initialHints = listOf(
            "idle" to AvatarMotionHints(),
            "thinking" to AvatarMotionHints(isGenerating = true),
            "working" to AvatarMotionHints(isGenerating = true, isUsingTools = true),
            "streaming" to AvatarMotionHints(isGenerating = true, hasStreamedTokens = true),
            "listening" to AvatarMotionHints(isTyping = true, inputFocused = true),
        )
        for ((name, hints) in initialHints) {
            for (seed in 1..15) {
                val s = AvatarMotionState(Random(seed))
                val f0 = s.frame
                s.tick(dt, hints, 1_700_000_000_000L)
                checkPair("first-$name-s$seed", f0, s.frame)
            }
        }

        // 2) Explicit hard-cut stress: Freeze thaw, mid-blink startle, mid-blink Happy release,
        //    repeated Attend, spins, and animate toggles on both Generical and Grok.
        for (grok in listOf(false, true)) {
            for (seed in 1..10) {
                val stage = AvatarStage(Random(seed))
                val s = AvatarMotionState(Random(seed)).apply { isGrok = grok }
                val id = stage.newId()
                s.stage = stage; s.stageId = id
                stage.update(id, 1, Offset(300f, 900f), 60f, "blob|${if (grok) "grok" else "generical"}", 1080f, 2400f)
                var prev = s.frame
                var ms = 1_700_000_000_000L
                val totalFrames = 60 * 35
                for (frameIdx in 0 until totalFrames) {
                    val t = frameIdx * dt
                    val hints = when {
                        t < 3f -> AvatarMotionHints()
                        t < 8f -> AvatarMotionHints(isGenerating = true, isUsingTools = true)
                        t < 14f -> AvatarMotionHints(isGenerating = true, hasStreamedTokens = true)
                        t < 24f -> AvatarMotionHints()
                        t < 28f -> AvatarMotionHints(isTyping = true, inputFocused = true)
                        else -> AvatarMotionHints()
                    }
                    // Trigger Response.Freeze mid-motion so the thaw is exercised every seed.
                    if (frameIdx == 60 * 4) s.debugPerform(Response.Freeze)
                    // Poke right during active blinks/expressions to test startle mid-blink.
                    if (frameIdx == 60 * 6 + 10 || frameIdx == 60 * 15 || frameIdx == 60 * 15 + 18) s.poke(0.3f, 0.2f)
                    // Re-trigger Attend while already active.
                    if (frameIdx == 60 * 17 || frameIdx == 60 * 17 + 30) s.director.gesture(MotionDirector.Gesture.Attend, 1.5f)
                    // Trigger Happy -> Neutral transitions and spins.
                    if (frameIdx == 60 * 19) s.debugReact("Happy", 1.2f)
                    if (frameIdx == 60 * 22) s.debugSpin(runUp = true, shake = true)
                    // Toggle animate off and back on smoothly.
                    if (frameIdx == 60 * 29) s.animate = false
                    if (frameIdx == 60 * 31) s.animate = true

                    s.tick(dt, hints, ms)
                    ms += 16L
                    val curr = s.frame
                    checkPair("stress-g$grok-s$seed-t${"%.2f".format(t)}", prev, curr)
                    prev = curr
                }
            }
        }

        assertTrue("Hard cuts detected (${failures.size}):\n" + failures.take(25).joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun sphereSpinDoesNotSnapVisibleEyeToNeutralWhenOtherEyeWrapsBehindLimb() {
        load()
        val blob = requireNotNull(MarkShapes.resolve("blob"))
        // Mid-spin yaw where the right eye wraps behind the sphere while the left eye is still visible.
        // Rendering with Angry expression must differ from Neutral (not fall back to Neutral!).
        fun renderEyePixels(expr: String, spinYaw: Float): Bitmap {
            val bitmap = Bitmap.createBitmap(180, 180, Bitmap.Config.ARGB_8888)
            CanvasDrawScope().draw(Density(2f), LayoutDirection.Ltr, Canvas(android.graphics.Canvas(bitmap)), Size(180f, 180f)) {
                drawMark(
                    blob, "blob", true,
                    MotionFrame(
                        exprFrom = expr, exprTo = expr, exprWeights = listOf(expr to 1f),
                        spinYaw = spinYaw,
                    ),
                    false, 1f, Color(0xFF2A92FE), Color.White, Color.White,
                )
            }
            return bitmap
        }
        // Check around 70°..82° where one eye has wrapped behind the sphere limb and one remains visible.
        var checkedPartialVisibility = false
        for (deg in 65..88) {
            val angry = renderEyePixels("Angry", deg.toFloat())
            val neutral = renderEyePixels("Neutral", deg.toFloat())
            var whiteAngry = 0
            var diffPx = 0
            for (y in 0 until 180) for (x in 0 until 180) {
                val pa = angry.getPixel(x, y)
                val pn = neutral.getPixel(x, y)
                if (android.graphics.Color.red(pa) > 220 && android.graphics.Color.green(pa) > 220 && android.graphics.Color.blue(pa) > 220) whiteAngry++
                if (pa != pn) diffPx++
            }
            angry.recycle(); neutral.recycle()
            if (whiteAngry in 20..500) {
                checkedPartialVisibility = true
                assertTrue("At spinYaw=$deg° still-visible sphere eye snapped to neutral (diffPx=$diffPx)", diffPx > 10)
            }
        }
        assertTrue("Expected to test partial limb visibility during sphere spin", checkedPartialVisibility)
    }
}
