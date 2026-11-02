package me.rerere.rikkahub.ui.components.avatar.animated

import org.junit.Assert.assertEquals
import org.junit.Test

class ChannelVisibilityTest {
    @Test fun recenterGateIgnoresSaturatedAuthoredAnglesButStillDetectsAVisibleReturn() {
        fun recenter(start: Float, end: Float): Float {
            val trace = ChannelTrace()
            trace.poke(0.2f)
            repeat(61) { i ->
                val t = i / 60f
                val yaw = start + (end - start) * ((t - 0.2f) / 0.3f).coerceIn(0f, 1f)
                trace.add(ChannelTrace.fromFrame(t, MotionFrame(body = BodyPose(yaw = yaw))))
            }
            return ChannelMetrics.compute(trace)["recenter_before_react"]
        }
        assertEquals("raw 166→159° is essentially stationary after render capping", 0f, recenter(166f, 159f), 0f)
        assertEquals("a real return toward centre must still fail", 1f, recenter(12f, 5f), 0f)
    }
}
