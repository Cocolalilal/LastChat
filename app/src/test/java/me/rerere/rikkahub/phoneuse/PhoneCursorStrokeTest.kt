package me.rerere.rikkahub.phoneuse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PhoneCursorStrokeTest {
    @Test fun `tap feedback and actual stroke share clamped physical pixel coordinates`() {
        val tap = PhoneCursorStroke.withinScreen(-2f, 2400f, -2f, 2400f, width = 1080, height = 2340)
        assertEquals(PhoneCursorStroke(0f, 2339f, 0f, 2339f), tap)
        val swipe = PhoneCursorStroke.withinScreen(300.5f, 1800.25f, 1081f, -10f, width = 1080, height = 2340)
        assertEquals(PhoneCursorStroke(300.5f, 1800.25f, 1079f, 0f), swipe)
    }

    @Test fun `nonfinite coordinates cannot diverge between native gesture and feedback`() {
        for (invalid in listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)) {
            assertThrows(IllegalArgumentException::class.java) {
                PhoneCursorStroke.withinScreen(invalid, 10f, 20f, 30f, width = 100, height = 100)
            }
            assertThrows(IllegalArgumentException::class.java) {
                PhoneCursorStroke.withinScreen(10f, 20f, 30f, invalid, width = 100, height = 100)
            }
        }
    }
}
