package me.rerere.rikkahub.data.ai

import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import org.junit.Assert.*
import org.junit.Test

class PhoneUseContinuationTest {
    private fun phone(action: String) = UIMessage(role = MessageRole.ASSISTANT, parts = listOf(
        UIMessagePart.ToolCall("phone", "phone_use", """{"action":"$action"}"""),
    ))

    @Test fun `an intermediate tool followed by silence or premature text needs continuation`() {
        val history = listOf(UIMessage.user("Find the address"), phone("open_app"))
        assertTrue(phoneUseNeedsCompletion(history + UIMessage.assistant("")))
        assertTrue(phoneUseNeedsCompletion(history + UIMessage.assistant("Opened Maps")))
    }

    @Test fun `finish requires a final reply`() {
        val history = listOf(UIMessage.user("Find the address"), phone("finish"))
        assertTrue(phoneUseNeedsCompletion(history + UIMessage.assistant("")))
        assertFalse(phoneUseNeedsCompletion(history + UIMessage.assistant("The address is 12 Main Street")))
    }

    @Test fun `old phone tasks cannot restart an unrelated turn`() {
        assertFalse(phoneUseNeedsCompletion(listOf(phone("tap"), UIMessage.user("Hello"), UIMessage.assistant("Hi"))))
        assertFalse(phoneUseNeedsCompletion(listOf(UIMessage.user("Hello"), UIMessage.assistant("Hi"))))
    }
}
