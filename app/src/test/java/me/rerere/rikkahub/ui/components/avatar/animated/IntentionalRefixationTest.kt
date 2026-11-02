package me.rerere.rikkahub.ui.components.avatar.animated

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot
import kotlin.random.Random

class IntentionalRefixationTest {
    private val dt = 1f / 120f

    private fun firstTarget(r: IntentionalRefixation): IntentionalRefixation.Target {
        repeat(120 * 60) {
            val goal = r.update(dt, 0.3f, 0.1f, settled = true, allowed = true)
            if (r.sequence > 0) return goal
        }
        error("seed produced no refixation in a minute")
    }

    @Test fun heldLooksHaveRareSingleDestinationsInsteadOfContinuousNoise() {
        val r = IntentionalRefixation(Random(41))
        var last = IntentionalRefixation.Target(0f, 0f)
        var sequence = 0
        var lastEvent = -100f
        val intervals = ArrayList<Float>()
        repeat(120 * 120) { i ->
            val t = i * dt
            val goal = r.update(dt, 0.3f, 0.1f, settled = true, allowed = true)
            if (r.sequence == sequence) {
                assertEquals("target wandered between opportunities", last, goal)
            } else {
                assertTrue("refixation before a quiet hold", t >= IntentionalRefixation.MIN_QUIET_SEC - dt)
                assertTrue("opportunities too frequent", t - lastEvent >= IntentionalRefixation.MIN_QUIET_SEC - dt)
                assertTrue("token twitch", hypot(goal.x - last.x, goal.y - last.y) >= IntentionalRefixation.MIN_TRAVEL)
                assertTrue("large jump", hypot(goal.x - last.x, goal.y - last.y) <= IntentionalRefixation.MAX_TRAVEL + 1e-6f)
                if (lastEvent > 0f) intervals += t - lastEvent
                lastEvent = t
                sequence = r.sequence
            }
            assertTrue("fixation escaped its small neighbourhood", hypot(goal.x, goal.y) <= IntentionalRefixation.MAX_RADIUS + 1e-6f)
            last = goal
        }
        assertTrue("no occasional life", sequence in 4..23)
        assertTrue("metronomic adjustment", intervals.max() - intervals.min() > 1f)
    }

    @Test fun aMovingRefixationDoesNotCancelItsOwnDestination() {
        val r = IntentionalRefixation(Random(41))
        val goal = firstTarget(r)
        val before = r.sequence
        repeat(120 * 3) {
            assertEquals(goal, r.update(dt, 0.3f, 0.1f, settled = false, allowed = true))
        }
        assertEquals(before, r.sequence)
    }

    @Test fun trackingAndRealAttentionWinAndGetAFreshQuietPeriod() {
        val r = IntentionalRefixation(Random(41))
        firstTarget(r)
        val before = r.sequence
        repeat(120 * 20) {
            val goal = r.update(dt, 0.3f, 0.1f, settled = true, allowed = false)
            assertEquals(IntentionalRefixation.Target(0f, 0f), goal)
        }
        repeat(120 * 4) {
            assertEquals(IntentionalRefixation.Target(0f, 0f),
                r.update(dt, -0.45f, 0.2f, settled = true, allowed = true))
        }
        assertEquals(before, r.sequence)
    }

    @Test fun yieldingRetargetsTheExistingSpringWithoutTeleportingIt() {
        val r = IntentionalRefixation(Random(41))
        val goal = firstTarget(r)
        val spring = DampedMotion()
        repeat(60) { spring.advance(goal.x, dt, 24f, 0.9f) }
        val position = spring.value
        val velocity = spring.velocity
        val cleared = r.update(dt, -0.5f, 0.1f, settled = false, allowed = false)
        assertEquals("helper reset the physical position", position, spring.value, 0f)
        assertEquals("helper discarded momentum", velocity, spring.velocity, 0f)
        spring.advance(cleared.x, dt, 24f, 0.9f)
        assertTrue("yield introduced a visible step", kotlin.math.abs(spring.value - position) < 0.01f)
    }

    @Test fun targetsStayInsideTheFaceEvenAtExtremeGazeAnchors() {
        for (ax in listOf(-1f, -0.98f, 0f, 0.98f, 1f)) for (ay in listOf(-1f, 0f, 1f)) {
            val r = IntentionalRefixation(Random(9))
            repeat(120 * 90) {
                val goal = r.update(dt, ax, ay, settled = true, allowed = true)
                assertTrue(ax + goal.x in -1f..1f)
                assertTrue(ay + goal.y in -1f..1f)
                assertTrue(hypot(goal.x, goal.y) <= IntentionalRefixation.MAX_RADIUS + 1e-6f)
            }
        }
    }
}
