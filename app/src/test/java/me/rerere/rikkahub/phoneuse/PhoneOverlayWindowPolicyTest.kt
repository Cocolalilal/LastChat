package me.rerere.rikkahub.phoneuse

import org.junit.Assert.*
import org.junit.Test
import kotlin.uuid.Uuid

class PhoneOverlayWindowPolicyTest {
    private val task = PhoneUseTask(Uuid.random(), Uuid.random(), true, "finish")

    @Test fun `system completion restores full assistant input and approvals keep their existing UI`() {
        val done = task.copy(running = false)
        assertTrue(shouldReturnToAssistant(done))
        assertFalse(shouldReturnToAssistant(done.copy(resultPresented = true)))
        assertFalse(shouldReturnToAssistant(done.copy(systemAssistant = false)))
        assertTrue(shouldShowPhoneOverlay(done, appVisible = false, capturing = false))
        assertTrue(shouldReturnToAssistant(task.copy(awaitingReply = true)))
        assertFalse(shouldReturnToAssistant(task.copy(systemAssistant = false, awaitingReply = true)))
    }

    @Test fun `small popup can move across the available screen`() {
        assertEquals(500 to 1800, clampPhoneOverlayOffset(500, 1800, 1080, 2200, 360, 112))
        assertEquals(720 to 2088, clampPhoneOverlayOffset(10000, 10000, 1080, 2200, 360, 112))
        assertEquals(0 to 0, clampPhoneOverlayOffset(-10, -10, 1080, 2200, 360, 112))
    }

    @Test fun `reply growth and rotation cannot leave a popup outside its usable frame`() {
        assertEquals(720 to 1560, clampPhoneOverlayOffset(720, 2088, 1080, 2200, 360, 640))
        assertEquals(720 to 440, clampPhoneOverlayOffset(720, 1560, 2200, 1080, 360, 640))
        assertEquals(0 to 0, clampPhoneOverlayOffset(720, 1560, 320, 480, 360, 640))
    }
}
