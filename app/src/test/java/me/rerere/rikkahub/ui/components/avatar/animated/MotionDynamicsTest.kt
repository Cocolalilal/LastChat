package me.rerere.rikkahub.ui.components.avatar.animated

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random

class MotionDynamicsTest {
    @Test
    fun spinOscillatorKeepsMomentumAndSettlesTheSameAtDifferentFrameRates() {
        fun run(hz: Int): DampedMotion {
            val s = DampedMotion()
            repeat(hz / 2) { s.advance(8f, 1f / hz, 7f, 0.78f) }
            val velocity = s.velocity
            val position = s.value
            s.advance(0f, 0f, 7f, 0.78f)
            assertEquals("retarget must preserve position", position, s.value, 0f)
            assertEquals("retarget must preserve momentum", velocity, s.velocity, 0f)
            repeat(hz * 2) { s.advance(0f, 1f / hz, 7f, 0.78f) }
            return s
        }
        val reference = run(120)
        for (hz in listOf(20, 30, 60)) {
            val s = run(hz)
            assertEquals("position at $hz Hz", reference.value, s.value, 0.0001f)
            assertEquals("velocity at $hz Hz", reference.velocity, s.velocity, 0.0001f)
        }
        assertTrue("lean settles near rest", abs(reference.value) < 0.05f)
    }

    private fun spin() = SpinTrajectory(0.4f, 1.7f, 11f, 8f, 0.1f)

    @Test
    fun spinCarriesMomentumThroughItsWindupAndBraking() {
        val s = spin()
        s.sample(s.windup)
        assertTrue("wind-up is visible", s.angle < -5f)
        assertTrue("wind-up never locks at a keyframe", s.velocity < -1f)
        var peak = 0f; var positiveLead = 0f; var peakSpeed = 0f
        repeat(600) { i ->
            s.sample(i / 120f)
            peak = maxOf(peak, s.angle)
            positiveLead = maxOf(positiveLead, s.eyeLead)
            peakSpeed = maxOf(peakSpeed, abs(s.velocity))
        }
        assertTrue("physical overshoot", peak > 362f && peak < 380f)
        assertTrue("eyes continue in the braking direction", positiveLead > 0.015f)
        assertTrue("decisive but controlled turn", peakSpeed in 300f..700f)
        s.sample(s.duration)
        assertEquals(360f, s.angle, 0f)
        assertEquals(0f, s.eyeLead, 0f)
    }

    @Test
    fun spinVelocityAndEyeLeadRemainContinuousAtLaunchAndFinish() {
        val s = spin()
        val h = 0.001f
        for (join in listOf(s.windup, s.duration)) {
            s.sample(join - h); val a = s.angle; val ea = s.eyeLead
            s.sample(join); val b = s.angle; val eb = s.eyeLead
            s.sample(join + h); val c = s.angle; val ec = s.eyeLead
            assertTrue("angle cut at $join", abs(b - a) < 0.1f && abs(c - b) < 0.1f)
            assertTrue("eye cut at $join", abs(eb - ea) < 0.002f && abs(ec - eb) < 0.002f)
        }
    }

    @Test
    fun spinSurfaceConstraintsFadeContinuouslyAndIgnoreWholeTurns() {
        for (a in listOf(0f, 0.49f, 0.51f, 10f, 34.99f, 35f, 180f, 359.5f)) {
            assertEquals(spinSafetyWeight(a, 0f, 0f), spinSafetyWeight(a + 360f, 0f, 0f), 0.0001f)
            assertTrue(abs(spinSafetyWeight(a + 0.01f, 0f, 0f) - spinSafetyWeight(a, 0f, 0f)) < 0.001f)
        }
    }

    @Test
    fun movingGazeStaysLiveThroughoutEverySpinPath() {
        for (path in SpinPath.entries) {
            val s = AvatarMotionState(Random(71))
            val hints = AvatarMotionHints(isGenerating = true, hasStreamedTokens = true)
            repeat(120) { s.tick(1f / 60f, hints, 1_800_000_000_000L) }
            s.debugGaze.focus(0.65f, 0.15f, 5f, 10, force = true)
            repeat(4) { s.tick(1f / 60f, hints, 1_800_000_000_000L) }
            s.debugSpin(true, false, path)
            var last = s.frame
            var travel = 0f
            repeat(48) {
                s.tick(1f / 60f, hints, 1_800_000_000_000L)
                val f = s.frame
                assertTrue("$path froze the face clock", f.timeSec > last.timeSec)
                travel += abs(f.headLookX - last.headLookX)
                assertTrue("$path cut gaze", abs(f.headLookX - last.headLookX) < 0.12f)
                assertTrue("$path cut eye lead", abs(f.leadX - last.leadX) < 0.025f)
                last = f
            }
            assertTrue("$path locked a moving head", travel > 0.1f)
        }
    }
}
