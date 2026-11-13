package me.rerere.rikkahub.phoneuse

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.uuid.Uuid

class PhoneReturnOwnershipTest {
    @Test fun `delayed automatic return cannot reopen a retired replaced or resumed task`() {
        val result = PhoneUseTask(Uuid.random(), Uuid.random(), true, "finish", running = false)
        assertTrue(canReturnPhoneConversation(result, result.copy(returnRequested = true), automatic = true))
        assertFalse(canReturnPhoneConversation(result, null, automatic = true))
        assertFalse(canReturnPhoneConversation(result, result.copy(conversationId = Uuid.random()), automatic = true))
        assertFalse(canReturnPhoneConversation(result, result.copy(running = true, action = "tap"), automatic = true))
        assertFalse(canReturnPhoneConversation(result, result.copy(resultPresented = true), automatic = true))
        assertFalse(canReturnPhoneConversation(result, result.copy(systemAssistant = false), automatic = true))
        assertTrue(canReturnPhoneConversation(result, result.copy(running = true, paused = true), automatic = false))
    }
}
