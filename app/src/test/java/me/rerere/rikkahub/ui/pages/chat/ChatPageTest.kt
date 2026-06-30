package me.rerere.rikkahub.ui.pages.chat

import androidx.compose.ui.unit.dp
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.datastore.DisplaySetting
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.model.Assistant
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
    fun assistantResponseTurnKeepsSameKeyFromPendingPlaceholderToRealReply() {
        val userNode = MessageNode.of(UIMessage.user("Hello"))
        val userGroup = me.rerere.rikkahub.ui.components.chat.MessageTurnGroup(
            nodes = listOf(userNode),
            role = me.rerere.ai.core.MessageRole.USER,
        )
        val pendingGroup = me.rerere.rikkahub.ui.components.chat.MessageTurnGroup(
            nodes = listOf(MessageNode.of(UIMessage.assistant(""))),
            role = me.rerere.ai.core.MessageRole.ASSISTANT,
        )
        val assistantNode = MessageNode.of(UIMessage.assistant("Thinking"))
        val assistantGroup = me.rerere.rikkahub.ui.components.chat.MessageTurnGroup(
            nodes = listOf(assistantNode),
            role = me.rerere.ai.core.MessageRole.ASSISTANT,
        )

        val pendingKey = chatListTurnKey(
            group = pendingGroup,
            index = 1,
            previousGroup = userGroup,
            isPendingAssistantTurn = true,
        )
        val realReplyKey = chatListTurnKey(
            group = assistantGroup,
            index = 1,
            previousGroup = userGroup,
            isPendingAssistantTurn = false,
        )

        assertEquals("assistant_response:${userNode.id}", pendingKey)
        assertEquals(pendingKey, realReplyKey)
    }

    @Test
    fun presetAssistantMessageKeepsStableInitialTurnKey() {
        val node = MessageNode.of(UIMessage.assistant("Hey there"))
        val group = me.rerere.rikkahub.ui.components.chat.MessageTurnGroup(
            nodes = listOf(node),
            role = me.rerere.ai.core.MessageRole.ASSISTANT,
        )

        assertEquals(
            "assistant_initial:0",
            chatListTurnKey(
                group = group,
                index = 0,
                previousGroup = null,
                isPendingAssistantTurn = false,
            )
        )
        assertEquals(
            "assistant_initial:0",
            chatListTurnKey(
                group = group,
                index = 0,
                previousGroup = null,
                isPendingAssistantTurn = true,
            )
        )
    }

    @Test
    fun assistantPresetDefinitionAloneDoesNotCountAsVisibleConversationPreset() {
        val preset = UIMessage.assistant("Hey there")
        val assistant = Assistant(presetMessages = listOf(preset))

        assertFalse(
            hasConversationPresetMessages(
                conversation = Conversation.ofId(Uuid.random()),
                assistant = assistant,
            )
        )
        assertTrue(
            hasConversationPresetMessages(
                conversation = Conversation.ofId(
                    id = Uuid.random(),
                    messages = listOf(MessageNode.of(preset)),
                ),
                assistant = assistant,
            )
        )
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
    fun buildAssistantSwitchNavigationKeepsDraftOutOfRoute() {
        val navigation = buildAssistantSwitchNavigation(
            persistenceMode = ChatPersistenceMode.TEMPORARY,
        )

        assertEquals(null, navigation.initText)
        assertTrue(navigation.initFiles.isEmpty())
        assertEquals(ChatPersistenceMode.TEMPORARY.routeValue, navigation.persistenceMode)
    }

    @Test
    fun buildAssistantSwitchNavigationDropsNormalPersistenceMode() {
        val navigation = buildAssistantSwitchNavigation(
            persistenceMode = ChatPersistenceMode.NORMAL,
        )

        assertEquals(null, navigation.initText)
        assertTrue(navigation.initFiles.isEmpty())
        assertEquals(null, navigation.persistenceMode)
    }

    @Test
    fun decodeChatRouteTextIgnoresInvalidBase64() {
        assertEquals("", decodeChatRouteText("draft text"))
    }

    @Test
    fun chatSessionDraftStoreMovesDraftWithoutEditingState() {
        ChatSessionDraftStore.clear()
        val fromId = Uuid.random()
        val toId = Uuid.random()
        val editingMessageId = Uuid.random()
        val draft = ChatInputDraft(
            text = "draft text",
            messageContent = listOf(UIMessagePart.Image("file:///tmp/image.png")),
            editingMessage = editingMessageId,
        )

        ChatSessionDraftStore.put(fromId, draft)
        ChatSessionDraftStore.moveDraft(fromId, toId, draft)

        assertEquals(null, ChatSessionDraftStore.get(fromId))
        assertEquals(
            draft.copy(editingMessage = null),
            ChatSessionDraftStore.get(toId)
        )
        ChatSessionDraftStore.clear()
    }
}
