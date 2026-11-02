package me.rerere.rikkahub.ui.components.avatar.animated

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random

class BusyFocusTest {
    @Test fun aLookArrivesPromptlyAndItsFocusDoesNotKeepChanging() {
        val look = FocusedLook(
            LeanPhrase.Knot(0f, 2f, -3f, -0.2f, 0.05f),
            LeanPhrase.Knot(0f, 7f, 10f, 0.3f, 0.08f), 1.9f,
        )
        assertTrue("arrival too slow", look.sample(0.2f).yaw > 9f)
        for (t in listOf(0.3f, 0.6f, 0.9f, 1.2f)) {
            assertEquals(10f, look.sample(t).yaw, 0f)
            assertEquals(0.3f, look.sample(t).gazeX, 0f)
        }
        val h = 0.0001f
        for (t in listOf(look.attack, look.duration - look.release, look.duration)) {
            val a = look.sample(t - h).yaw; val b = look.sample(t).yaw; val c = look.sample(t + h).yaw
            assertTrue("focus entry/exit velocity cut", abs((b-a)/h - (c-b)/h) < 0.2f)
        }
    }

    @Test fun aTrackedBusyFixationDoesNotHaveUnrequestedMicroCorrections() {
        val r = Random(19)
        val temper = Temperament(r)
        val gaze = GazeDirector(r, IdlePlanner(r), temper)
        val xs = ArrayList<Float>(); val ys = ArrayList<Float>()
        repeat(720) { i ->
            gaze.track(0.35f, 0.08f, 1)
            gaze.update(1f / 120f, MarkLifecycle.Streaming, false, allowIdle = false)
            if (i >= 240) { xs += gaze.eyeX; ys += gaze.eyeY }
        }
        assertTrue("eye x keeps correcting itself", xs.max() - xs.min() < 0.0001f)
        assertTrue("eye y keeps correcting itself", ys.max() - ys.min() < 0.0001f)
        assertTrue("incidental seating motion still active", gaze.incidentalMotion < 0.0001f)
    }

    @Test fun aLongHeldLookReinspectsOnceThenHoldsAndYieldsPromptly() {
        val r = Random(41)
        val gaze = GazeDirector(r, IdlePlanner(r), Temperament(r))
        gaze.focus(0.3f, 0.1f, 60f, 2, force = true)
        var ticks = 0
        while (gaze.refixationSequence == 0 && ticks < 120 * 45) {
            gaze.update(1f / 120f, MarkLifecycle.Idle, false, allowIdle = false)
            ticks++
        }
        assertTrue("adjusted before a quiet hold", ticks >= 120 * 5)
        assertTrue("held look has no occasional life", gaze.refixationSequence > 0)
        repeat(120) { gaze.update(1f / 120f, MarkLifecycle.Idle, false, allowIdle = false) }
        val heldX = gaze.eyeX; val heldY = gaze.eyeY
        repeat(120) {
            gaze.update(1f / 120f, MarkLifecycle.Idle, false, allowIdle = false, quietEyes = false)
            assertEquals("eye keeps adjusting after arrival", heldX, gaze.eyeX, 0.0001f)
            assertEquals("blink pause pulls the look back", heldY, gaze.eyeY, 0.0001f)
        }
        val before = gaze.eyeX
        gaze.focus(-0.4f, 0.1f, 2f, 6, withHead = false, force = true)
        assertEquals("new attention teleported the eye", before, gaze.eyeX, 0f)
        repeat(36) { gaze.update(1f / 120f, MarkLifecycle.Idle, false, allowIdle = false) }
        assertEquals("old adjustment obstructed attention", -0.4f, gaze.eyeX, 0.003f)
    }
}
