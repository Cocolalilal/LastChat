package me.rerere.rikkahub.ui.activity

import android.app.Application
import android.os.Looper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowChoreographer
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@LooperMode(LooperMode.Mode.PAUSED)
class OverlayOpacityTest {
    @Before fun pauseDisplayFrames() {
        // Pausing the Looper alone does not pause Robolectric's display receiver: by
        // default each scheduled vsync advances time itself until the animator finishes.
        ShadowChoreographer.setPaused(true)
        ShadowChoreographer.setFrameDelay(Duration.ofMillis(16))
    }

    @After fun restoreDisplayFrames() { ShadowChoreographer.setPaused(false) }

    private fun advance(milliseconds: Long) {
        var remaining = milliseconds
        while (remaining > 0) {
            val step = minOf(16L, remaining)
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(step))
            remaining -= step
        }
    }

    @Test fun `modal return from a hidden surface has intermediate frames before becoming opaque`() {
        var opacity = 0f
        val fade = OverlayOpacity(0f) { opacity = it }
        fade.animateTo(1f)
        assertEquals(0f, opacity, 0f)
        advance(64)
        assertTrue("Picker return must glide through an intermediate opacity: $opacity", opacity > 0f && opacity < 1f)
        advance(300)
        assertEquals(1f, opacity, 0f)
    }

    @Test fun `reversing a hide preserves its visual position and never runs stale removal`() {
        var removed = false
        val fade = OverlayOpacity(1f) {}
        fade.animateTo(0f) { removed = true }
        advance(64)
        val before = fade.value
        assertTrue(before > 0f && before < 1f)
        fade.animateTo(1f)
        assertEquals(before, fade.value, 0f)
        advance(300)
        assertEquals(1f, fade.value, 0f)
        assertFalse(removed)
    }

    @Test fun `repeated render events do not restart the fade deadline`() {
        var complete = false
        val fade = OverlayOpacity(1f) {}
        fade.animateTo(0f) { complete = true }
        repeat(8) {
            advance(16)
            fade.animateTo(0f) { complete = true }
        }
        advance(96)
        assertEquals(0f, fade.value, 0f)
        assertTrue(complete)
    }

    @Test fun `capture hides synchronously and cancelling a host cannot remove its successor`() {
        var staleRemoval = false
        val fade = OverlayOpacity(1f) {}
        fade.animateTo(0f) { staleRemoval = true }
        advance(32)
        fade.cancel()
        advance(300)
        assertFalse(staleRemoval)
        fade.animateTo(0f, immediate = true)
        assertEquals(0f, fade.value, 0f)
        fade.animateTo(1f, immediate = true)
        assertEquals(1f, fade.value, 0f)
    }

    @Test fun `replacement can synchronously revoke the completion of a pending close`() {
        var finishedOldActivity = false
        var retiredForReplacement = false
        val fade = OverlayOpacity(1f) {}
        fade.animateTo(0f) { finishedOldActivity = true }
        advance(64)
        assertTrue(fade.value > 0f && fade.value < 1f)
        fade.animateTo(0f, immediate = true) { retiredForReplacement = true }
        assertEquals(0f, fade.value, 0f)
        assertTrue(retiredForReplacement)
        advance(300)
        assertFalse(finishedOldActivity)
    }
}
