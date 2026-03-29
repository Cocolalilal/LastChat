package me.rerere.rikkahub.data.datastore

import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ProviderSetting
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.AssistantSearchMode
import me.rerere.rikkahub.data.model.DEFAULT_TEXT_SELECTION_ACTIONS
import me.rerere.rikkahub.data.model.TextSelectionConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.uuid.Uuid

class TextSelectionActionModelSettingsTest {

    @Test
    fun getTextSelectionActionModel_prefersActionSpecificModel() {
        val assistantModel = chatModel(
            id = "00000000-0000-0000-0000-000000000101",
            displayName = "Assistant Model",
        )
        val actionModel = chatModel(
            id = "00000000-0000-0000-0000-000000000102",
            displayName = "Action Model",
        )
        val assistant = assistant(
            id = "00000000-0000-0000-0000-000000000201",
            chatModelId = assistantModel.id,
        )
        val settings = settings(
            assistant = assistant,
            models = listOf(assistantModel, actionModel),
            textSelectionConfig = TextSelectionConfig(
                assistantId = assistant.id,
                actions = DEFAULT_TEXT_SELECTION_ACTIONS.map { action ->
                    if (action.id == "translate") {
                        action.copy(modelId = actionModel.id)
                    } else {
                        action
                    }
                }
            )
        )

        val resolved = settings.getTextSelectionActionModel("translate")

        assertEquals(actionModel.id, resolved?.id)
    }

    @Test
    fun getTextSelectionActionModel_usesAssistantModelWhenNoActionSpecificModelIsSet() {
        val assistantModel = chatModel(
            id = "00000000-0000-0000-0000-000000000103",
            displayName = "Assistant Model",
        )
        val translateModel = chatModel(
            id = "00000000-0000-0000-0000-000000000104",
            displayName = "Translate Model",
        )
        val assistant = assistant(
            id = "00000000-0000-0000-0000-000000000202",
            chatModelId = assistantModel.id,
        )
        val settings = settings(
            assistant = assistant,
            models = listOf(assistantModel, translateModel),
            translateModelId = translateModel.id,
            textSelectionConfig = TextSelectionConfig(
                assistantId = assistant.id,
                actions = DEFAULT_TEXT_SELECTION_ACTIONS
            )
        )

        val resolved = settings.getTextSelectionActionModel("translate")

        assertEquals(assistantModel.id, resolved?.id)
    }

    @Test
    fun getTextSelectionActionModel_usesConfiguredAssistantModelForGeneralActions() {
        val globalModel = chatModel(
            id = "00000000-0000-0000-0000-000000000105",
            displayName = "Global Model",
        )
        val quickAskModel = chatModel(
            id = "00000000-0000-0000-0000-000000000106",
            displayName = "Quick Ask Model",
        )
        val globalAssistant = assistant(
            id = "00000000-0000-0000-0000-000000000203",
            chatModelId = globalModel.id,
        )
        val quickAskAssistant = assistant(
            id = "00000000-0000-0000-0000-000000000204",
            chatModelId = quickAskModel.id,
        )
        val settings = Settings(
            assistantId = globalAssistant.id,
            chatModelId = globalModel.id,
            assistants = listOf(globalAssistant, quickAskAssistant),
            providers = listOf(ProviderSetting.OpenAI(models = listOf(globalModel, quickAskModel))),
            textSelectionConfig = TextSelectionConfig(
                assistantId = quickAskAssistant.id,
                actions = DEFAULT_TEXT_SELECTION_ACTIONS
            )
        )

        val resolved = settings.getTextSelectionActionModel("explain")

        assertEquals(quickAskModel.id, resolved?.id)
    }

    @Test
    fun getTextSelectionActionModel_usesAssistantFallbackForSummarize() {
        val assistantModel = chatModel(
            id = "00000000-0000-0000-0000-000000000109",
            displayName = "Assistant Model",
        )
        val summarizerModel = chatModel(
            id = "00000000-0000-0000-0000-000000000110",
            displayName = "Summarizer Model",
        )
        val assistant = assistant(
            id = "00000000-0000-0000-0000-000000000206",
            chatModelId = assistantModel.id,
        )
        val settings = Settings(
            assistantId = assistant.id,
            chatModelId = assistantModel.id,
            assistants = listOf(assistant),
            providers = listOf(ProviderSetting.OpenAI(models = listOf(assistantModel, summarizerModel))),
            summarizerModelId = summarizerModel.id,
            textSelectionConfig = TextSelectionConfig(
                assistantId = assistant.id,
                actions = DEFAULT_TEXT_SELECTION_ACTIONS
            )
        )

        val resolved = settings.getTextSelectionActionModel("summarize")

        assertEquals(assistantModel.id, resolved?.id)
    }

    @Test
    fun sanitize_clearsOrphanedTextSelectionActionModels() {
        val assistantModel = chatModel(
            id = "00000000-0000-0000-0000-000000000107",
            displayName = "Assistant Model",
        )
        val orphanedModelId = Uuid.parse("00000000-0000-0000-0000-000000000108")
        val assistant = assistant(
            id = "00000000-0000-0000-0000-000000000205",
            chatModelId = assistantModel.id,
        )
        val settings = settings(
            assistant = assistant,
            models = listOf(assistantModel),
            textSelectionConfig = TextSelectionConfig(
                assistantId = assistant.id,
                actions = DEFAULT_TEXT_SELECTION_ACTIONS.map { action ->
                    if (action.id == "summarize") {
                        action.copy(modelId = orphanedModelId)
                    } else {
                        action
                    }
                }
            )
        )

        val (sanitized, cleanup) = settings.sanitize()

        assertNull(sanitized.findTextSelectionAction("summarize")?.modelId)
        assertEquals(1, cleanup.orphanedModelReferences)
    }

    private fun settings(
        assistant: Assistant,
        models: List<Model>,
        translateModelId: Uuid = assistant.chatModelId ?: models.first().id,
        textSelectionConfig: TextSelectionConfig,
    ): Settings {
        return Settings(
            assistantId = assistant.id,
            chatModelId = assistant.chatModelId ?: models.first().id,
            assistants = listOf(assistant),
            providers = listOf(ProviderSetting.OpenAI(models = models)),
            translateModeId = translateModelId,
            textSelectionConfig = textSelectionConfig,
        )
    }

    private fun assistant(
        id: String,
        chatModelId: Uuid,
    ): Assistant {
        return Assistant(
            id = Uuid.parse(id),
            name = "Assistant-$id",
            chatModelId = chatModelId,
            searchMode = AssistantSearchMode.Off,
        )
    }

    private fun chatModel(
        id: String,
        displayName: String,
    ): Model {
        return Model(
            id = Uuid.parse(id),
            modelId = displayName.lowercase().replace(" ", "-"),
            displayName = displayName,
        )
    }
}
