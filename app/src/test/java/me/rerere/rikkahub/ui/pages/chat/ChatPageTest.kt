package me.rerere.rikkahub.ui.pages.chat

import androidx.compose.ui.unit.dp
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.datastore.DisplaySetting
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.ai.ui.MessageNode
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
        val statusBarPadding = 24.dp
        assertEquals(120.dp, chatListTopPadding(chatTopBarPlacement(Settings()), statusBarPadding))
        assertEquals(140.dp, chatListBottomPadding(chatTopBarPlacement(Settings())))

        val bottomPlacement = chatTopBarPlacement(
            Settings(displaySetting = DisplaySetting(chatToolbarAtBottom = true))
        )
        assertEquals(60.dp, chatListTopPadding(bottomPlacement, statusBarPadding))
        assertEquals(204.dp, chatListBottomPadding(bottomPlacement))
    }

    @Test
    fun contextPopupUsesTheSameToolbarClearanceAsTheChatDropdown() {
        val topPlacement = chatTopBarPlacement(Settings())
        assertEquals(64.dp, chatToolbarPopupTopPadding(topPlacement))
        assertEquals(0.dp, chatToolbarPopupBottomPadding(topPlacement))

        val bottomPlacement = chatTopBarPlacement(
            Settings(displaySetting = DisplaySetting(chatToolbarAtBottom = true))
        )
        assertEquals(0.dp, chatToolbarPopupTopPadding(bottomPlacement))
        assertEquals(72.dp, chatToolbarPopupBottomPadding(bottomPlacement))
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

    @Test
    fun characterIntroTurnsDoNotOfferRegenerate() {
        assertFalse(
            shouldOfferMessageRegenerate(
                role = me.rerere.ai.core.MessageRole.ASSISTANT,
                isLastTurn = true,
                previousGroup = null,
            )
        )
        assertTrue(
            shouldOfferMessageRegenerate(
                role = me.rerere.ai.core.MessageRole.ASSISTANT,
                isLastTurn = true,
                previousGroup = me.rerere.rikkahub.ui.components.chat.MessageTurnGroup(
                    nodes = listOf(MessageNode.of(UIMessage.user("Hi"))),
                    role = me.rerere.ai.core.MessageRole.USER,
                ),
            )
        )
    }

    @Test
    fun characterIntroMessagesAreDetectedBeforeAnyUserTurn() {
        val intro = UIMessage.assistant("Hello there")
        val user = UIMessage.user("Hi")
        val reply = UIMessage.assistant("Welcome")
        assertTrue(isCharacterIntroMessage(listOf(intro, user, reply), intro))
        assertFalse(isCharacterIntroMessage(listOf(intro, user, reply), reply))
    }

    @Test
    fun unreadDotIsNotAddedForTheConversationCurrentlyBeingViewed() {
        val viewing = Uuid.random()
        val other = Uuid.random()
        assertTrue(
            completedGenerationIdsAfterDone(
                currentCompleted = emptySet(),
                finishedConversationId = viewing,
                viewingConversationId = viewing,
            ).isEmpty()
        )
        assertEquals(
            setOf(other),
            completedGenerationIdsAfterDone(
                currentCompleted = emptySet(),
                finishedConversationId = other,
                viewingConversationId = viewing,
            )
        )
    }

    @Test
    fun latestAssistantSpeechTargetUsesSelectedRegenerationAndNeverFallsBackAcrossTurns() {
        val conversationId = Uuid.random()
        val turnAnchorId = Uuid.random()
        val olderTurnReply = UIMessage.assistant("Earlier turn reply")
        val v1ToolCall = UIMessage(
            role = me.rerere.ai.core.MessageRole.ASSISTANT,
            parts = listOf(
                UIMessagePart.ToolCall(
                    toolCallId = "call-1",
                    toolName = "search_web",
                    arguments = "{}",
                )
            ),
            versionTag = "v1",
        )
        val v1FinalReply = UIMessage.assistant("Old v1 reply").copy(versionTag = "v1")
        val v2Blank = UIMessage.assistant("").copy(versionTag = "v2")
        val v2Reply = UIMessage.assistant("New v2 reply").copy(versionTag = "v2")

        // Blank v2 right after clicking regenerate must NOT fall back to olderTurnReply or v1FinalReply.
        val blankRegenConversation = Conversation.ofId(
            id = conversationId,
            messages = listOf(
                MessageNode.of(UIMessage.user("Turn 1")),
                MessageNode.of(olderTurnReply),
                MessageNode.of(UIMessage.user("Turn 2")),
                MessageNode(id = turnAnchorId, messages = listOf(v1ToolCall, v2Blank), selectIndex = 1),
                MessageNode.of(v1FinalReply),
            ),
        )
        val blankTarget = latestAssistantSpeechTarget(blankRegenConversation)
        assertEquals(turnAnchorId, blankTarget?.identity?.turnAnchorNodeId)
        assertEquals("v2", blankTarget?.identity?.versionTag)
        assertEquals("", blankTarget?.text)

        // Once v2 has text, trailing v1FinalReply must be ignored.
        val streamedRegenConversation = Conversation.ofId(
            id = conversationId,
            messages = listOf(
                MessageNode.of(UIMessage.user("Turn 1")),
                MessageNode.of(olderTurnReply),
                MessageNode.of(UIMessage.user("Turn 2")),
                MessageNode(id = turnAnchorId, messages = listOf(v1ToolCall, v2Reply), selectIndex = 1),
                MessageNode.of(v1FinalReply),
            ),
        )
        val activeTarget = latestAssistantSpeechTarget(streamedRegenConversation)
        assertEquals("New v2 reply", activeTarget?.text)

        // When trailing node is a USER message, there is no latest assistant turn yet.
        val pendingUserConversation = Conversation.ofId(
            id = conversationId,
            messages = listOf(
                MessageNode.of(UIMessage.user("Turn 1")),
                MessageNode.of(olderTurnReply),
                MessageNode.of(UIMessage.user("Turn 2")),
            ),
        )
        assertEquals(null, latestAssistantSpeechTarget(pendingUserConversation))
    }

    @Test
    fun ttsAutoplayWhileGeneratingSpeaksOnlyRegeneratedReplyAndFlushesPreviousPlayback() {
        val state = ChatTtsAutoplayState()
        val conversationId = Uuid.random()
        val turnAnchorId = Uuid.random()
        val olderTurnReply = UIMessage.assistant("Older turn that must not play.")
        val v1Reply = UIMessage.assistant("Original reply.").copy(versionTag = "v1")
        val v2Blank = UIMessage.assistant("").copy(versionTag = "v2")
        val v2Chunk1 = v2Blank.copy(parts = listOf(UIMessagePart.Text("First new sentence. ")))
        val v2Chunk2 = v2Blank.copy(parts = listOf(UIMessagePart.Text("First new sentence. Second sentence")))

        fun conv(activeNode: MessageNode) = Conversation.ofId(
            id = conversationId,
            messages = listOf(
                MessageNode.of(UIMessage.user("First")),
                MessageNode.of(olderTurnReply),
                MessageNode.of(UIMessage.user("Second")),
                activeNode,
            ),
        )

        // Generation starts before blank v2 is seeded: baseline captures v1, nothing spoken.
        assertTrue(
            state.onUpdate(
                mode = me.rerere.rikkahub.data.datastore.TtsAutoplayMode.WHILE_GENERATING,
                isGenerating = true,
                target = latestAssistantSpeechTarget(conv(MessageNode(id = turnAnchorId, messages = listOf(v1Reply), selectIndex = 0))),
            ).isEmpty()
        )

        // Blank v2 is seeded: must NOT speak olderTurnReply.
        assertTrue(
            state.onUpdate(
                mode = me.rerere.rikkahub.data.datastore.TtsAutoplayMode.WHILE_GENERATING,
                isGenerating = true,
                target = latestAssistantSpeechTarget(conv(MessageNode(id = turnAnchorId, messages = listOf(v1Reply, v2Blank), selectIndex = 1))),
            ).isEmpty()
        )

        // First sentence of v2 arrives: flushes previous playback and speaks v2.
        assertEquals(
            listOf(TtsAutoplayCommand(text = "First new sentence.", flush = true)),
            state.onUpdate(
                mode = me.rerere.rikkahub.data.datastore.TtsAutoplayMode.WHILE_GENERATING,
                isGenerating = true,
                target = latestAssistantSpeechTarget(conv(MessageNode(id = turnAnchorId, messages = listOf(v1Reply, v2Chunk1), selectIndex = 1))),
            )
        )

        // Generation finishes with unpunctuated tail: appends remaining text without flushing.
        assertEquals(
            listOf(TtsAutoplayCommand(text = "Second sentence", flush = false)),
            state.onUpdate(
                mode = me.rerere.rikkahub.data.datastore.TtsAutoplayMode.WHILE_GENERATING,
                isGenerating = false,
                target = latestAssistantSpeechTarget(conv(MessageNode(id = turnAnchorId, messages = listOf(v1Reply, v2Chunk2), selectIndex = 1))),
            )
        )

        // Switching back to v1 while idle must NOT trigger autoplay.
        assertTrue(
            state.onUpdate(
                mode = me.rerere.rikkahub.data.datastore.TtsAutoplayMode.WHILE_GENERATING,
                isGenerating = false,
                target = latestAssistantSpeechTarget(conv(MessageNode(id = turnAnchorId, messages = listOf(v1Reply, v2Chunk2), selectIndex = 0))),
            ).isEmpty()
        )
    }

    @Test
    fun ttsAutoplayAfterGenerationSpeaksFullMultiSentenceRegeneratedReply() {
        val state = ChatTtsAutoplayState()
        val conversationId = Uuid.random()
        val turnAnchorId = Uuid.random()
        val v1Reply = UIMessage.assistant("Old reply.").copy(versionTag = "v1")
        val v2Final = UIMessage.assistant("First sentence. Second sentence! Third line").copy(versionTag = "v2")

        // Generation starts with v1 visible as baseline.
        assertTrue(
            state.onUpdate(
                mode = me.rerere.rikkahub.data.datastore.TtsAutoplayMode.AFTER_GENERATION,
                isGenerating = true,
                target = latestAssistantSpeechTarget(
                    Conversation.ofId(
                        id = conversationId,
                        messages = listOf(
                            MessageNode.of(UIMessage.user("Hi")),
                            MessageNode(id = turnAnchorId, messages = listOf(v1Reply), selectIndex = 0),
                        ),
                    )
                ),
            ).isEmpty()
        )

        // Generation finishes with v2 selected: speaks the entire regenerated text in full.
        assertEquals(
            listOf(TtsAutoplayCommand(text = "First sentence. Second sentence! Third line", flush = true)),
            state.onUpdate(
                mode = me.rerere.rikkahub.data.datastore.TtsAutoplayMode.AFTER_GENERATION,
                isGenerating = false,
                target = latestAssistantSpeechTarget(
                    Conversation.ofId(
                        id = conversationId,
                        messages = listOf(
                            MessageNode.of(UIMessage.user("Hi")),
                            MessageNode(id = turnAnchorId, messages = listOf(v1Reply, v2Final), selectIndex = 1),
                        ),
                    )
                ),
            )
        )
    }
}
