package me.rerere.rikkahub.data.datastore

import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ProviderSetting
import me.rerere.rikkahub.data.ai.local.LOCAL_PROVIDER_ID
import me.rerere.rikkahub.utils.JsonInstant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.uuid.Uuid

class LocalProviderSettingsNormalizationTest {
    @Test
    fun `local provider serializes and deserializes as its own type`() {
        val provider: ProviderSetting = ProviderSetting.Local(
            id = LOCAL_PROVIDER_ID,
            models = listOf(
                Model(
                    id = Uuid.random(),
                    modelId = "gemma-4-E2B-it",
                    displayName = "Gemma 4 E2B"
                )
            )
        )

        val json = JsonInstant.encodeToString(ProviderSetting.serializer(), provider)
        val decoded = JsonInstant.decodeFromString(ProviderSetting.serializer(), json)

        assertTrue(decoded is ProviderSetting.Local)
        assertEquals(LOCAL_PROVIDER_ID, decoded.id)
    }

    @Test
    fun `ensureBuiltInProviders reinserts local provider and pins it first`() {
        val settings = Settings(
            init = true,
            providers = listOf(
                ProviderSetting.OpenAI(
                    id = Uuid.random(),
                    name = "Custom OpenAI",
                    baseUrl = "https://example.com/v1"
                )
            )
        )

        val normalized = settings.ensureBuiltInProviders()

        assertTrue(normalized.providers.first() is ProviderSetting.Local)
        assertEquals(LOCAL_PROVIDER_ID, normalized.providers.first().id)
        assertNotNull(normalized.providers.find { it.name == "Custom OpenAI" })
    }

    @Test
    fun `withLocalProviderModels updates provider models and clears stale model selections`() {
        val readyLocalModel = Model(
            id = Uuid.random(),
            modelId = "gemma-4-E2B-it",
            displayName = "Gemma 4 E2B"
        )
        val staleModelId = Uuid.random()
        val assistantId = Uuid.random()
        val assistant = DEFAULT_ASSISTANTS.first().copy(
            id = assistantId,
            chatModelId = staleModelId,
            backgroundModelId = staleModelId,
        )

        val settings = Settings(
            init = true,
            chatModelId = staleModelId,
            titleModelId = staleModelId,
            translateModeId = staleModelId,
            suggestionModelId = staleModelId,
            providers = listOf(
                ProviderSetting.Local(
                    id = LOCAL_PROVIDER_ID,
                    models = emptyList(),
                )
            ),
            assistants = listOf(assistant),
        )

        val normalized = settings.withLocalProviderModels(listOf(readyLocalModel))
        val localProvider = normalized.providers.first() as ProviderSetting.Local

        assertEquals(listOf(readyLocalModel), localProvider.models)
        assertEquals(readyLocalModel.id, normalized.chatModelId)
        assertEquals(readyLocalModel.id, normalized.titleModelId)
        assertEquals(readyLocalModel.id, normalized.translateModeId)
        assertEquals(readyLocalModel.id, normalized.suggestionModelId)
        assertEquals(null, normalized.assistants.single().chatModelId)
        assertEquals(null, normalized.assistants.single().backgroundModelId)
    }

    @Test
    fun `clearMissingModelReferences preserves intentionally disabled optional model selections`() {
        val chatModel = Model(
            id = Uuid.random(),
            modelId = "chat-model",
            displayName = "Chat Model",
        )
        val embeddingModel = Model(
            id = Uuid.random(),
            modelId = "embedding-model",
            displayName = "Embedding Model",
            type = me.rerere.ai.provider.ModelType.EMBEDDING,
        )
        val settings = Settings(
            init = true,
            chatModelId = chatModel.id,
            titleModelId = chatModel.id,
            translateModeId = chatModel.id,
            suggestionModelId = DISABLED_MODEL_ID,
            embeddingModelId = DISABLED_MODEL_ID,
            providers = listOf(
                ProviderSetting.OpenAI(
                    id = Uuid.random(),
                    name = "Provider",
                    models = listOf(chatModel, embeddingModel),
                )
            ),
        )

        val normalized = settings.clearMissingModelReferences()

        assertEquals(DISABLED_MODEL_ID, normalized.suggestionModelId)
        assertEquals(DISABLED_MODEL_ID, normalized.embeddingModelId)
    }
}
