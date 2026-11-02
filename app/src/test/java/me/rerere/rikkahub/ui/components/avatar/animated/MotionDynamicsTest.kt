package me.rerere.rikkahub.ui.components.avatar.animated

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random

class MotionDynamicsTest {
    @Test
    fun leanKeepsMomentumAndSettlesTheSameAtDifferentFrameRates() {
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

    private fun spin() = SpinTrajectory(0.4f, 1.7f, 11f, 0.03f, 8f, 0.1f, 0.26f, 0.78f)

    @Test
    fun spinHasAnticipationOvershootAndDelayedEyeSettle() {
        val s = spin()
        s.sample(s.windup)
        assertEquals(-11f, s.angle, 0.001f)
        assertTrue("eyes anticipate", s.eyeLead < 0f)
        s.sample(s.turnEnd)
        assertEquals(368f, s.angle, 0.001f)
        val leadAtStop = s.eyeLead
        s.sample(s.turnEnd + s.followOut)
        assertTrue("eyes carry on after body brakes", s.eyeLead > leadAtStop)
        s.sample(s.duration)
        assertEquals(360f, s.angle, 0.0001f)
        assertEquals(0f, s.compression, 0f)
        assertEquals(0f, s.eyeLead, 0f)
    }

    @Test
    fun spinJoinsPreservePositionAndVelocity() {
        val s = spin()
        val h = 0.001f
        fun values(t: Float): FloatArray {
            s.sample(t)
            return floatArrayOf(s.angle, s.compression, s.eyeLead)
        }
        for (join in listOf(s.windup, s.windup + 0.18f, s.windup + 0.48f,
            s.turnEnd, s.turnEnd + s.followOut, s.duration)) {
            val a = values(join - h); val b = values(join); val c = values(join + h)
            for (i in a.indices) {
                val incoming = (b[i] - a[i]) / h
                val outgoing = (c[i] - b[i]) / h
                val limit = if (i == 0) 4f else 0.015f
                assertTrue("velocity jump at $join channel $i: $incoming → $outgoing", abs(incoming - outgoing) < limit)
            }
        }
    }

    @Test
    fun gesturePeaksAndEndsHaveNoVelocityCuts() {
        val curves = listOf<(Float) -> Float>(MotionDirector::springNod, MotionDirector::startlePop,
            { MotionDirector.snapCurve(it, 0.15f, 0.12f) })
        val joins = listOf(listOf(0f, 0.26f, 1f), listOf(0f, 0.09f, 1f), listOf(0f, 0.15f, 0.405f, 1f))
        val h = 0.0001f
        for ((j, curve) in curves.withIndex()) for (t in joins[j]) {
            val incoming = (curve(t) - curve((t - h).coerceAtLeast(0f))) / h
            val outgoing = (curve((t + h).coerceAtMost(1f)) - curve(t)) / h
            assertTrue("gesture $j velocity cut at $t: $incoming → $outgoing", abs(incoming - outgoing) < 0.3f)
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
