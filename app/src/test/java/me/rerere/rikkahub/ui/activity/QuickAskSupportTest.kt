package me.rerere.rikkahub.ui.activity

import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.MessageNode
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.model.Conversation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.uuid.Uuid

class QuickAskSupportTest {
    @Test
    fun `buildQuickAskMessageParts creates a plain text user message`() {
        val parts = buildQuickAskMessageParts(
            text = "Explain this paragraph",
            attachments = emptyList()
        )

        assertEquals(1, parts.size)
        assertEquals("Explain this paragraph", (parts.single() as UIMessagePart.Text).text)
    }

    @Test
    fun `buildQuickAskMessageParts keeps text and attachments together`() {
        val parts = buildQuickAskMessageParts(
            text = "What is in this file",
            attachments = listOf(
                QuickAskAttachment(
                    uri = "file:///tmp/doc.pdf",
                    fileName = "doc.pdf",
                    mimeType = "application/pdf"
                )
            )
        )

        assertEquals(2, parts.size)
        assertTrue(parts[0] is UIMessagePart.Text)
        assertTrue(parts[1] is UIMessagePart.Document)
        assertEquals("doc.pdf", (parts[1] as UIMessagePart.Document).fileName)
    }

    @Test
    fun `buildQuickAskTextContent appends custom prompt context`() {
        val text = buildQuickAskTextContent(
            text = "Source text",
            customPrompt = "Translate to Japanese"
        )

        assertEquals("Source text\n\nQuestion: Translate to Japanese", text)
    }

    @Test
    fun `continuation data preserves attachments and assistant choice`() {
        val continuation = QuickAskContinuationData(
            text = "Hello",
            attachments = listOf(
                QuickAskAttachment(
                    uri = "file:///tmp/image.png",
                    fileName = "image.png",
                    mimeType = "image/png"
                )
            ),
            aiResponse = "Hi",
            userPrompt = "Summarize it",
            assistantId = "assistant-123"
        )

        assertEquals("Hello", continuation.text)
        assertEquals(1, continuation.attachments.size)
        assertEquals("assistant-123", continuation.assistantId)
        assertEquals("Summarize it", continuation.userPrompt)
    }

    @Test
    fun openInAppSeedsSelectedTextAndReplyWithoutBlankAssistantTurn() {
        val nodes = buildQuickAskContinuationNodes(
            QuickAskContinuationData(
                text = "Selected paragraph",
                aiResponse = "  Here is an explanation.  ",
            )
        )

        assertEquals(2, nodes.size)
        assertEquals(MessageRole.USER, nodes[0].role)
        assertEquals("Selected paragraph", nodes[0].currentMessage.toContentText())
        assertEquals(MessageRole.ASSISTANT, nodes[1].role)
        assertEquals("Here is an explanation.", nodes[1].currentMessage.toContentText())
    }

    @Test
    fun openInAppDropsBlankAssistantReply() {
        val nodes = buildQuickAskContinuationNodes(
            QuickAskContinuationData(
                text = "Selected paragraph",
                aiResponse = "   ",
            )
        )

        assertEquals(1, nodes.size)
        assertEquals(MessageRole.USER, nodes.single().role)
        assertEquals("Selected paragraph", nodes.single().currentMessage.toContentText())
    }

    @Test
    fun openInAppReplacesEmptyGenericalDraftInsteadOfKeepingBlankBubble() {
        val draftId = Uuid.random()
        val emptyDraft = Conversation.ofId(
            id = draftId,
            messages = listOf(MessageNode.of(UIMessage.assistant(""))),
        )
        val seed = seedQuickAskChat(
            data = QuickAskContinuationData(
                text = "What does this mean?",
                aiResponse = "It means hello.",
            ),
            existingConversation = emptyDraft,
            introNodes = listOf(MessageNode.of(UIMessage.assistant(""))),
        )

        assertEquals(draftId, seed.reuseConversationId)
        assertEquals(2, seed.messageNodes.size)
        assertEquals(MessageRole.USER, seed.messageNodes.first().role)
        assertTrue(seed.messageNodes.none { node ->
            node.role == MessageRole.ASSISTANT && !node.hasDisplayableChatContent()
        })
    }

    @Test
    fun openInAppWithoutExchangeUsesRealIntroNotBlankBubble() {
        val intro = MessageNode.of(UIMessage.assistant("Hey, I'm Generical."))
        val seed = seedQuickAskChat(
            data = QuickAskContinuationData(),
            existingConversation = null,
            introNodes = listOf(MessageNode.of(UIMessage.assistant("")), intro),
        )

        assertNull(seed.reuseConversationId)
        assertEquals(1, seed.messageNodes.size)
        assertEquals("Hey, I'm Generical.", seed.messageNodes.single().currentMessage.toContentText())
    }
}
