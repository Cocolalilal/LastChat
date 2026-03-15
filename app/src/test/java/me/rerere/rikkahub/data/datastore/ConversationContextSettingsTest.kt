package me.rerere.rikkahub.data.datastore

import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ProviderSetting
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.AssistantSearchMode
import me.rerere.rikkahub.data.model.AssistantUISettings
import me.rerere.rikkahub.data.model.Conversation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import kotlin.uuid.Uuid

class ConversationContextSettingsTest {
    @Test
    fun resolveConversationContextUsesConversationAssistantInsteadOfGlobalSelection() {
        val globalModel = Model(
            id = Uuid.parse("00000000-0000-0000-0000-000000000101"),
            modelId = "global-model",
            displayName = "Global Model",
        )
        val conversationModel = Model(
            id = Uuid.parse("00000000-0000-0000-0000-000000000102"),
            modelId = "conversation-model",
            displayName = "Conversation Model",
        )
        val globalAssistant = Assistant(
            id = Uuid.parse("00000000-0000-0000-0000-000000000201"),
            name = "Global",
            chatModelId = globalModel.id,
            searchMode = AssistantSearchMode.Off,
        )
        val conversationAssistant = Assistant(
            id = Uuid.parse("00000000-0000-0000-0000-000000000202"),
            name = "Conversation",
            chatModelId = conversationModel.id,
            searchMode = AssistantSearchMode.Provider(1),
            uiSettings = AssistantUISettings(
                newChatHeaderStyle = NewChatHeaderStyle.BIG_ICON,
                newChatShowAvatar = false,
            ),
        )
        val settings = Settings(
            assistantId = globalAssistant.id,
            chatModelId = globalModel.id,
            assistants = listOf(globalAssistant, conversationAssistant),
            providers = listOf(
                ProviderSetting.OpenAI(models = listOf(globalModel, conversationModel))
            ),
            displaySetting = DisplaySetting(
                newChatHeaderStyle = NewChatHeaderStyle.GREETING,
                newChatShowAvatar = true,
            ),
        )
        val conversation = Conversation.ofId(
            id = Uuid.parse("00000000-0000-0000-0000-000000000301"),
            assistantId = conversationAssistant.id,
        )

        val context = settings.resolveConversationContext(conversation)

        assertEquals(conversationAssistant.id, context.assistantId)
        assertEquals(conversationAssistant.id, context.assistant.id)
        assertEquals(conversationModel.id, context.chatModel?.id)
        assertEquals(AssistantSearchMode.Provider(1), context.searchMode)
        assertEquals(NewChatHeaderStyle.BIG_ICON, context.displaySetting.newChatHeaderStyle)
        assertFalse(context.displaySetting.newChatShowAvatar)
    }
}
