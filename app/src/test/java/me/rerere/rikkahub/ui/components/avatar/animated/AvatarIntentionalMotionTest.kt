package me.rerere.rikkahub.ui.components.avatar.animated

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random

class AvatarIntentionalMotionTest {
    @Test fun leanTravelsThroughIntermediatePosesWithoutAStaticHold() {
        val p = LeanPhrase(listOf(
            LeanPhrase.Knot(0f, 0f, 0f, 0f, 0f),
            LeanPhrase.Knot(0.9f, 7f, 2f, 0.28f, 0.08f),
            LeanPhrase.Knot(1.55f, 5.25f, 1.4f, 0.12f, 0.1f),
            LeanPhrase.Knot(2.35f, -4.9f, -1.8f, -0.24f, 0.06f),
            LeanPhrase.Knot(4.2f, 0f, 0f, 0f, 0.02f),
        ))
        var still = 0; var maxStill = 0
        var last = p.sample(0f)
        repeat(240) { i ->
            val f = p.sample((i + 1) / 60f)
            still = if (abs(f.roll - last.roll) < 0.002f) still + 1 else 0
            maxStill = maxOf(maxStill, still)
            if (f.roll < -1f) assertTrue("gaze goes with the second lean", f.gazeX < 0f)
            last = f
        }
        assertTrue("a phrase should not lock into a hold", maxStill < 10)
        for (k in p.knots.drop(1).dropLast(1)) {
            val h = 0.001f
            val a = p.sample(k.t - h).roll; val b = p.sample(k.t).roll; val c = p.sample(k.t + h).roll
            assertTrue("velocity discontinuity at ${k.t}", abs((b-a)/h - (c-b)/h) < 0.3f)
        }
    }

    @Test fun spontaneousBlinksAreCalmerAndGazeShiftsCannotStackThem() {
        for (life in listOf(MarkLifecycle.Idle, MarkLifecycle.Thinking, MarkLifecycle.Streaming)) {
            val b = Blinker(Random(7))
            repeat(600 * 60) { b.update(1f / 60f, life, false) }
            assertTrue("$life has ${b.starts} blinks in ten minutes", b.starts in 35..100)
        }
        val b = Blinker(Random(7))
        var previous = -100f
        b.onStart = { chained ->
            if (!chained) {
                assertTrue("gaze blinks bunched together", clock - previous >= Blinker.SACCADE_GAP)
                previous = clock
            }
        }
        repeat(120 * 60) {
            clock = it / 60f
            b.saccade()
            b.update(1f / 60f, MarkLifecycle.Idle, false)
        }
        assertEquals(0, b.overlaps)
    }
    private var clock = 0f

    @Test fun authoredClipsAndLiveSpinsNeverChangeUniformScale() {
        val clip = MarkClip("zoom", 2f, "sphere", 0f, true, mapOf(
            "scale" to listOf(ClipKeyframe(0f, floatArrayOf(0.42f)), ClipKeyframe(2f, floatArrayOf(0.9f))),
        ))
        val src = ClipSource(clip, 1f, false, 2f)
        repeat(120) { assertEquals(1f, src.pose(it / 60f).scale, 0f) }
        val s = AvatarMotionState(Random(71))
        val hints = AvatarMotionHints(isGenerating = true, hasStreamedTokens = true)
        repeat(120) { s.tick(1f / 60f, hints, 1_800_000_000_000L) }
        s.debugSpin(true, false, SpinPath.Turn)
        repeat(360) {
            s.tick(1f / 60f, hints, 1_800_000_000_000L)
            assertEquals("live spin uniform scale", 1f, s.frame.body.scale, 0f)
        }
    }

    @Test fun bothEyeStylesHideHappyReturnBehindAClosedMask() {
        for (grok in listOf(false, true)) {
            val s = AvatarMotionState(Random(4)).apply { isGrok = grok }
            val hints = AvatarMotionHints(isGenerating = true, hasStreamedTokens = true)
            s.debugReact("Happy", 1.2f)
            var last = s.frame
            var masks = 0; var hiddenSwaps = 0
            repeat(960) { i ->
                // Own both endpoints: the busy planner may legitimately choose Happy again
                // after the first reaction expires. Test the transition, not that random choice.
                if (i == 240) s.debugReact("Neutral", 20f)
                s.tick(1f / 120f, hints, 1_800_000_000_000L)
                val f = s.frame
                if (f.maskBlink > 0.85f) masks++
                if (last.exprTo == "Happy" && f.exprTo != "Happy") {
                    assertTrue("Happy changed with visible open geometry", f.maskBlink >= 0.98f)
                    hiddenSwaps++
                }
                last = f
            }
            assertTrue("no masking blink", masks > 0)
            assertTrue("Happy never returned (grok=$grok)", hiddenSwaps > 0)
        }
    }
}
