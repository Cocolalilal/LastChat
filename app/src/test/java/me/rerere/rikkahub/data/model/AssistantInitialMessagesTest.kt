package me.rerere.rikkahub.data.model

import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantInitialMessagesTest {
    @Test
    fun blankPresetIntrosDoNotCreateEmptyAssistantBubbles() {
        val assistant = Assistant(
            presetMessages = listOf(
                UIMessage(
                    role = me.rerere.ai.core.MessageRole.ASSISTANT,
                    parts = listOf(UIMessagePart.Text("   ")),
                ),
            ),
            alternateGreetings = listOf("", "  "),
        )

        assertTrue(assistant.getInitialMessageNodes().isEmpty())
    }

    @Test
    fun realIntrosSurviveBlankEntries() {
        val assistant = Assistant(
            presetMessages = listOf(
                UIMessage.assistant("   "),
                UIMessage.assistant("Hello from Generical"),
            ),
            alternateGreetings = listOf("", "Another greeting"),
        )
        val nodes = assistant.getInitialMessageNodes()

        assertEquals(1, nodes.size)
        assertEquals(
            listOf("Hello from Generical", "Another greeting"),
            nodes.single().messages.map { it.toContentText() },
        )
    }
}
