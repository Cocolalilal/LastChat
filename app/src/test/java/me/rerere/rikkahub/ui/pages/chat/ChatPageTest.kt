package me.rerere.rikkahub.ui.pages.chat

import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.data.datastore.DisplaySetting
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.MessageNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import androidx.compose.ui.unit.dp
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

    @Test
    fun chatToolbarPlacementDefaultsToTop() {
        assertEquals(
            "Top",
            chatTopBarPlacement(Settings()).name
        )
    }

    @Test
    fun chatToolbarPlacementUsesBottomWhenEnabled() {
        val settings = Settings(
            displaySetting = DisplaySetting(chatToolbarAtBottom = true)
        )

        assertEquals(
            "Bottom",
            chatTopBarPlacement(settings).name
        )
    }

    @Test
    fun chatListPaddingSwitchesWithToolbarPlacement() {
        assertEquals(72.dp, chatListTopPadding(chatTopBarPlacement(Settings())))
        assertEquals(140.dp, chatListBottomPadding(chatTopBarPlacement(Settings())))

        val bottomPlacement = chatTopBarPlacement(
            Settings(displaySetting = DisplaySetting(chatToolbarAtBottom = true))
        )
        assertEquals(16.dp, chatListTopPadding(bottomPlacement))
        assertEquals(204.dp, chatListBottomPadding(bottomPlacement))
    }
}
