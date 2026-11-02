package me.rerere.rikkahub.ui.components.avatar.animated

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BusyNodTest {
    @Test fun acknowledgementHasOneDipAndOneSmallSettleWithoutSidewaysShakes() {
        val samples = (0..1000).map { MotionDirector.busyNodPose(it / 1000f, 7f) }
        assertTrue(samples.all { it.yaw == 0f && it.roll == 0f })
        assertEquals(0f, samples.first().pitch, 0f)
        assertEquals(0f, samples.last().pitch, 0f)
        assertTrue(samples.minOf { it.pitch } >= -0.281f)
        val directions = samples.zipWithNext().map { (a, b) -> b.pitch - a.pitch }
            .filter { kotlin.math.abs(it) > 0.00001f }.map { if (it > 0f) 1 else -1 }
        assertEquals("repeated bounce after acknowledgement", 2, directions.zipWithNext().count { it.first != it.second })
    }
}
