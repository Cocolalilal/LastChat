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
    fun genericalFocusedFlattensCornersAndSlantTransitionIsContinuous() {
        load()
        val neutral = requireNotNull(GenericalFace.face("Neutral")).left
        val focused = FaceRig(Random(1)).accentPreview(FaceAccent.Focused).left
        val fx = neutral.x.copyOf()
        val fy = neutral.y.copyOf()
        FaceRig.shapeOutline(fx, fy, 0, focused)
        // Pressed upper corners pull outward toward the bounding box (squircle look) instead of
        // keeping a rigid circular radius that narrows the top edge.
        val topCornerIndices = neutral.x.indices.filter { neutral.y[it] < neutral.y.min() + 25f && abs(neutral.x[it] - neutral.cx) > 25f }
        assertTrue(topCornerIndices.isNotEmpty())
        val originalMeanAbsX = topCornerIndices.map { abs(neutral.x[it] - neutral.cx) }.average()
        val focusedMeanAbsX = topCornerIndices.map { abs(fx[it] - neutral.cx) }.average()
        assertTrue("Focused upper corners should flatten outward ($focusedMeanAbsX vs $originalMeanAbsX)", focusedMeanAbsX > originalMeanAbsX + 0.5)

        // Sweeping slant continuously across -0.05 (hard <-> soft lid) must never jump.
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

        // Sweeping upper across 1.0 with non-zero slant must never jump.
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
