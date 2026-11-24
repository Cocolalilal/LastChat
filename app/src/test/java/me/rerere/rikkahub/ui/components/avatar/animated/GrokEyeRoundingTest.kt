package me.rerere.rikkahub.ui.components.avatar.animated

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.hypot

class GrokEyeRoundingTest {
    @Test fun closingAndSquintingKeepCircularCapsAcrossExpressions() {
        for (expression in listOf("neutral", "happy", "sad", "angry", "focused", "closed", "sleepy")) {
            for (openness in listOf(1f, 0.85f, 0.55f, 0.25f, 0.02f)) {
                for (eye in WhSphereEyes.expressionPair(expression, openness)) {
                    val outline = WhSphereEyes.stadiumOutline(eye)
                    val n = eye.pts.size
                    // End cap: the last left offset, eleven arc samples, and first right offset.
                    val cap = outline.subList(n - 1, n + 12)
                    val a = cap.first(); val b = cap.last()
                    val cx = (a[0] + b[0]) / 2f
                    val cy = (a[1] + b[1]) / 2f
                    val radius = hypot(a[0] - cx, a[1] - cy)
                    assertTrue("$expression openness=$openness must retain a visible rounded cap", radius > 0.015f)
                    for (p in cap) assertEquals("$expression openness=$openness cap became elliptical",
                        radius, hypot(p[0] - cx, p[1] - cy), 0.00002f)
                }
            }
        }
    }

    @Test fun blinkMovesContinuouslyFromTheOriginalOpenEyeToARoundedLid() {
        val eye = WhSphereEyes.neutralPair().first()
        val open = WhSphereEyes.stadiumOutline(eye)
        var previous = open
        for (step in 1..100) {
            val outline = WhSphereEyes.stadiumOutline(eye.copy(closeK = step / 100f))
            assertEquals(open.size, outline.size)
            fun distance(a: List<FloatArray>, b: List<FloatArray>): Float = a.maxOf { p ->
                b.minOf { q -> hypot(p[0] - q[0], p[1] - q[1]) }
            }
            val movement = maxOf(distance(outline, previous), distance(previous, outline))
            assertTrue("Blink jumped at step $step: $movement", movement < 0.025f)
            assertTrue(outline.all { it[0].isFinite() && it[1].isFinite() })
            previous = outline
        }
        val height = previous.maxOf { it[1] } - previous.minOf { it[1] }
        val width = previous.maxOf { it[0] } - previous.minOf { it[0] }
        assertTrue("Closed eye should read as a lid", width > height * 2f)
        assertTrue(abs(height) > 0.05f)
    }
}
