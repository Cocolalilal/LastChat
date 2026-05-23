package me.rerere.rikkahub.ui.pages.chat

import androidx.compose.ui.unit.dp
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.datastore.DisplaySetting
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.MessageNode
import me.rerere.rikkahub.service.ChatPersistenceMode
import org.junit.Assert.assertEquals
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
        assertEquals(88.dp, chatListTopPadding(chatTopBarPlacement(Settings())))
        assertEquals(140.dp, chatListBottomPadding(chatTopBarPlacement(Settings())))

        val bottomPlacement = chatTopBarPlacement(
            Settings(displaySetting = DisplaySetting(chatToolbarAtBottom = true))
        )
        assertEquals(16.dp, chatListTopPadding(bottomPlacement))
        assertEquals(204.dp, chatListBottomPadding(bottomPlacement))
    }

    @Test
    fun wideChatLayoutRequiresTabletHeight() {
        assertFalse(shouldUseWideChatLayout(windowWidth = 920.dp, windowHeight = 430.dp))
        assertTrue(shouldUseWideChatLayout(windowWidth = 900.dp, windowHeight = 600.dp))
    }

    @Test
    fun canPreserveAssistantSwitchDraftOnlyForAssistantSeededDrafts() {
        val presetOnlyConversation = Conversation.ofId(
            id = Uuid.random(),
            messages = listOf(MessageNode.of(UIMessage.assistant("Preset"))),
        )
        val activeConversation = Conversation.ofId(
            id = Uuid.random(),
            messages = listOf(
                MessageNode.of(UIMessage.assistant("Preset")),
                MessageNode.of(UIMessage.user("Hello")),
            ),
        )

        assertTrue(canPreserveAssistantSwitchDraft(presetOnlyConversation))
        assertFalse(canPreserveAssistantSwitchDraft(activeConversation))
    }

    @Test
    fun buildAssistantSwitchNavigationCarriesDraftOnlyForPresetOnlyConversation() {
        val navigation = buildAssistantSwitchNavigation(
            conversation = Conversation.ofId(
                id = Uuid.random(),
                messages = listOf(MessageNode.of(UIMessage.assistant("Preset"))),
            ),
            inputText = "draft text",
            inputFiles = listOf("file:///tmp/image.png"),
            persistenceMode = ChatPersistenceMode.TEMPORARY,
        )

        assertEquals("draft text", navigation.initText)
        assertEquals(listOf("file:///tmp/image.png"), navigation.initFiles)
        assertEquals(ChatPersistenceMode.TEMPORARY.routeValue, navigation.persistenceMode)
    }

    @Test
    fun buildAssistantSwitchNavigationDropsDraftForActiveConversation() {
        val navigation = buildAssistantSwitchNavigation(
            conversation = Conversation.ofId(
                id = Uuid.random(),
                messages = listOf(
                    MessageNode.of(UIMessage.assistant("Preset")),
                    MessageNode.of(UIMessage.user("Hello")),
                ),
            ),
            inputText = "draft text",
            inputFiles = listOf("file:///tmp/image.png"),
            persistenceMode = ChatPersistenceMode.NORMAL,
        )

        assertEquals(null, navigation.initText)
        assertTrue(navigation.initFiles.isEmpty())
        assertEquals(null, navigation.persistenceMode)
    }

    @Test
    fun extractDraftFileUrlsKeepsMediaPartsOnly() {
        val urls = extractDraftFileUrls(
            listOf(
                UIMessagePart.Text("ignore me"),
                UIMessagePart.Image("file:///tmp/image.png"),
                UIMessagePart.Document("file:///tmp/file.pdf", "file.pdf", "application/pdf"),
            )
        )

        assertEquals(
            listOf(
                "file:///tmp/image.png",
                "file:///tmp/file.pdf",
            ),
            urls
        )
    }
}
