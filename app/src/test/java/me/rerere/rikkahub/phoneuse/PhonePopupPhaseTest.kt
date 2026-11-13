package me.rerere.rikkahub.phoneuse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.uuid.Uuid

class PhonePopupPhaseTest {
    private val task = PhoneUseTask(Uuid.random(), Uuid.random(), false, "observe")

    @Test fun `reply and next phone step have separate presentation phases`() {
        val result = task.copy(running = false, reply = "Done")
        assertEquals(PhonePopupPhase.Result, phonePopupPhase(result, false))
        assertTrue(phonePopupDefaultsExpanded(phonePopupPhase(result, false), floating = true))
        val resumed = result.copy(running = true, action = "tap", reply = "")
        assertEquals(PhonePopupPhase.Working, phonePopupPhase(resumed, false))
        assertFalse(phonePopupDefaultsExpanded(phonePopupPhase(resumed, false), floating = true))
        assertEquals(phonePopupPhase(task, false), phonePopupPhase(task.copy(action = "scroll"), false))
    }

    @Test fun `input and paused work remain available while active work stays compact`() {
        assertEquals(PhonePopupPhase.Paused, phonePopupPhase(task.copy(paused = true), false))
        assertTrue(phonePopupDefaultsExpanded(PhonePopupPhase.Paused, floating = true))
        assertEquals(PhonePopupPhase.NeedsInput, phonePopupPhase(task.copy(awaitingReply = true), false))
        assertEquals(PhonePopupPhase.NeedsInput, phonePopupPhase(task, true))
        assertEquals(PhonePopupPhase.NeedsInput, phonePopupPhase(task.copy(error = "Stopped"), false))
        assertTrue(phonePopupDefaultsExpanded(PhonePopupPhase.NeedsInput, floating = true))
        assertEquals(PhonePopupPhase.Working, phonePopupPhase(task.copy(action = "reply"), false))
        assertFalse(phonePopupDefaultsExpanded(PhonePopupPhase.Working, floating = true))
        assertTrue(phonePopupDefaultsExpanded(PhonePopupPhase.Working, floating = false))
    }
}
