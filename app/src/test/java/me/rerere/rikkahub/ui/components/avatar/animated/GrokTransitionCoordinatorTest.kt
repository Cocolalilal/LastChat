package me.rerere.rikkahub.ui.components.avatar.animated

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GrokTransitionCoordinatorTest {
    private val happy = listOf("Happy" to 1f)

    @Test
    fun happyToNeutralWaitsForAFullBlinkAndCommitsExactlyOnce() {
        val c = GrokTransitionCoordinator()
        assertTrue(c.request(happy, "Happy", "Neutral"))
        assertTrue(c.awaitingBlink)
        assertNull(c.update(1f, true)) // no confirmed mask blink yet
        assertTrue(c.beginMask())
        assertFalse(c.beginMask()) // never starts a second blink
        for (closure in listOf(0f, 0.4f, 0.85f, 0.97f)) assertNull(c.update(closure, true))
        assertEquals("Neutral", c.update(1f, true))
        assertNull(c.update(1f, true))
        assertTrue(c.maskActive)
        assertNull(c.update(0.3f, true))
        assertTrue(c.maskActive) // retain the common closed-shape lane while reopening
        c.update(0f, false)
        assertFalse(c.maskActive)
        assertFalse(c.awaitingBlink)
    }

    @Test
    fun newestIntentReplacesTheHiddenSwapWithoutAnotherBlink() {
        val c = GrokTransitionCoordinator()
        c.request(happy, "Happy", "Neutral")
        c.beginMask()
        assertTrue(c.request(happy, "Happy", "Surprised"))
        assertFalse(c.awaitingBlink)
        assertEquals("Surprised", c.update(1f, true))
    }

    @Test
    fun cancelledAndUrgentChangesDoNotAbruptlyDropAnInFlightMask() {
        for (urgent in listOf(false, true)) {
            val c = GrokTransitionCoordinator()
            c.request(happy, "Happy", "Neutral")
            c.beginMask()
            assertFalse(c.request(happy, "Happy", if (urgent) "Surprised" else "Happy", urgent))
            assertNull(c.pendingTarget)
            assertTrue(c.maskActive)
            assertNull(c.update(1f, true))
            c.update(0f, false)
            assertFalse(c.maskActive)
        }
    }

    @Test
    fun missedClosureRetainsTheRequestInsteadOfExposingTheMorph() {
        val c = GrokTransitionCoordinator()
        c.request(happy, "Happy", "Neutral")
        c.beginMask()
        c.update(0.7f, true)
        c.update(0f, false)
        assertTrue(c.awaitingBlink)
        assertEquals("Neutral", c.pendingTarget)
        c.beginMask()
        assertEquals("Neutral", c.update(1f, true))
    }

    @Test
    fun ordinaryFacesAndClosedTargetsDoNotAddBlinkRequests() {
        val c = GrokTransitionCoordinator()
        for (target in listOf("Happy", "Eyes closed", "Sleeping", "hurt or cute")) {
            assertFalse(c.request(happy, "Happy", target))
        }
        assertFalse(c.request(listOf("Neutral" to 1f), "Neutral", "Sad"))
        assertFalse(c.request(listOf("Neutral" to 0.99f, "Happy" to 0.01f), "Happy", "Neutral"))
        assertFalse(c.awaitingBlink)
    }
}
