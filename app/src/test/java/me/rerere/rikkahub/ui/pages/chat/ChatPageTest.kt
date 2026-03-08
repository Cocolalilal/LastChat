package me.rerere.rikkahub.ui.pages.chat

import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.MessageNode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.uuid.Uuid

class ChatPageTest {
    @Test
    fun hasConversationMessagesTreatsAssistantOnlyDraftAsConversation() {
        val conversation = Conversation.ofId(
            id = Uuid.random(),
            messages = listOf(MessageNode.of(UIMessage.assistant("Hey there"))),
        )

        assertTrue(hasConversationMessages(conversation))
    }

    @Test
    fun shouldShowNewChatContentOnlyForTrulyEmptyChats() {
        assertTrue(
            shouldShowNewChatContent(
                isTemporaryChat = false,
                hasConversationMessages = false,
                hasAnyPresetMessages = false,
                showNewChatContent = true,
                hasTextInput = false,
                isKeyboardOpen = false,
            )
        )

        assertFalse(
            shouldShowNewChatContent(
                isTemporaryChat = false,
                hasConversationMessages = true,
                hasAnyPresetMessages = false,
                showNewChatContent = true,
                hasTextInput = false,
                isKeyboardOpen = false,
            )
        )
    }
}
