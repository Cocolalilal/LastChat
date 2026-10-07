package me.rerere.rikkahub.ui.components.avatar.animated

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EyeStyleLerpTest {
    @Test
    fun lerp_midpoint() {
        val a = EyeParams(0f, 0f, 10f, 20f, 4f, rotation = 0f)
        val b = EyeParams(10f, 10f, 20f, 40f, 8f, rotation = 10f)
        val mid = EyeStyle.lerp(a, b, 0.5f)
        assertEquals(5f, mid.cx, 0.001f)
        assertEquals(5f, mid.cy, 0.001f)
        assertEquals(15f, mid.width, 0.001f)
        assertEquals(30f, mid.height, 0.001f)
        assertEquals(6f, mid.rx, 0.001f)
        assertEquals(5f, mid.rotation, 0.001f)
    }

    @Test
    fun neutralLeftEye_matchesGenericalSvg() {
        val eye = EyeStyle.neutralLeftEyeUnit()
        assertEquals(167f, eye.cx, 0.1f)
        assertEquals(113f, eye.width, 0.1f)
        assertEquals(148f, eye.height, 0.1f)
        assertEquals(40.5f, eye.rx, 0.1f)
    }

    @Test
    fun resolveGenericalExpression_aliases() {
        // Without assets loaded, resolve falls back to Neutral string still
        assertEquals("Neutral", EyeStyle.resolveGenericalExpressionId("neutral"))
        assertEquals("Happy", EyeStyle.resolveGenericalExpressionId("happy"))
        assertEquals("Sleeping", EyeStyle.resolveGenericalExpressionId("sleeping"))
    }
}
