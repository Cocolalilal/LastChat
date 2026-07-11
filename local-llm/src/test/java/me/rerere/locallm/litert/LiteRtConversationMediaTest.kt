package me.rerere.locallm.litert

import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class LiteRtConversationMediaTest {

    @Test
    fun `historical images move to live user message`() {
        val image = UIMessagePart.Image("file:///earlier.png")
        val current = UIMessage(
            role = MessageRole.USER,
            parts = listOf(UIMessagePart.Text("Continue the description")),
        )

        val prepared = prepareLiteRtConversationMessages(
            messages = listOf(
                UIMessage(
                    role = MessageRole.USER,
                    parts = listOf(UIMessagePart.Text("Describe this"), image),
                ),
                UIMessage(
                    role = MessageRole.ASSISTANT,
                    parts = listOf(UIMessagePart.Text("It shows a landscape")),
                ),
                current,
            ),
            supportsImage = true,
            supportsAudio = false,
        )

        val historyImages = prepared.messages.dropLast(1)
            .flatMap(UIMessage::parts)
            .filterIsInstance<UIMessagePart.Image>()
        assertTrue(historyImages.isEmpty())

        val sentImages = prepared.sendable.parts.filterIsInstance<UIMessagePart.Image>()
        assertEquals(listOf(image), sentImages)
        assertTrue(
            prepared.sendable.parts.filterIsInstance<UIMessagePart.Text>()
                .any { it.text == "Continue the description" }
        )
    }

    @Test
    fun `current media is not duplicated`() {
        val currentImage = UIMessagePart.Image("file:///current.png")
        val prepared = prepareLiteRtConversationMessages(
            messages = listOf(
                UIMessage(
                    role = MessageRole.USER,
                    parts = listOf(UIMessagePart.Text("Inspect"), currentImage),
                )
            ),
            supportsImage = true,
            supportsAudio = false,
        )

        assertSame(currentImage, prepared.sendable.parts.filterIsInstance<UIMessagePart.Image>().single())
        assertEquals(prepared.sendable, prepared.messages.single())
    }

    @Test
    fun `unsupported historical media remains for normal filtering`() {
        val image = UIMessagePart.Image("file:///earlier.png")
        val prepared = prepareLiteRtConversationMessages(
            messages = listOf(
                UIMessage(role = MessageRole.USER, parts = listOf(image)),
                UIMessage(role = MessageRole.USER, parts = listOf(UIMessagePart.Text("Continue"))),
            ),
            supportsImage = false,
            supportsAudio = false,
        )

        assertTrue(prepared.messages.first().parts.contains(image))
        assertFalse(prepared.sendable.parts.contains(image))
    }

    @Test
    fun `historical media is removed safely when live tool message cannot carry it`() {
        val prepared = prepareLiteRtConversationMessages(
            messages = listOf(
                UIMessage(
                    role = MessageRole.USER,
                    parts = listOf(UIMessagePart.Image("file:///earlier.png")),
                ),
                UIMessage(
                    role = MessageRole.TOOL,
                    parts = listOf(UIMessagePart.Text("tool result")),
                ),
            ),
            supportsImage = true,
            supportsAudio = false,
        )

        assertTrue(
            prepared.messages.flatMap(UIMessage::parts)
                .filterIsInstance<UIMessagePart.Image>()
                .isEmpty()
        )
        assertTrue(
            prepared.messages.first().parts.filterIsInstance<UIMessagePart.Text>()
                .single().text.contains("unavailable during this tool turn")
        )
    }
}
