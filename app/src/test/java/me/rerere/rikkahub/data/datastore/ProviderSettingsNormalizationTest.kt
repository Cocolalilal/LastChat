package me.rerere.rikkahub.data.datastore

import androidx.datastore.preferences.core.preferencesOf
import kotlinx.coroutines.runBlocking
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ModelType
import me.rerere.ai.provider.ProviderSetting
import me.rerere.rikkahub.data.datastore.migration.PreferenceStoreV1Migration
import me.rerere.rikkahub.utils.JsonInstant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.uuid.Uuid

class ProviderSettingsNormalizationTest {
    @Test
    fun `default providers do not include on-device provider`() {
        assertFalse(DEFAULT_PROVIDERS.any { it.name == "On-device" })
    }

    @Test
    fun `preference migration strips serialized local providers before decode`() = runBlocking {
        val remoteProvider = ProviderSetting.OpenAI(
            id = Uuid.parse("d5734028-d39b-4d41-9841-fd648d65440e"),
            name = "OpenRouter",
            baseUrl = "https://openrouter.ai/api/v1",
        )
        val localProviderJson = """
            {
              "type": "local",
              "id": "11111111-1111-1111-1111-111111111111",
              "enabled": true,
              "name": "On-device",
              "models": [],
              "proxy": { "type": "none" },
              "balanceOption": {},
              "tags": [],
              "customIconUri": null
            }
        """.trimIndent()
        val providersJson = "[${JsonInstant.encodeToString(ProviderSetting.serializer(), remoteProvider)},$localProviderJson]"
        val preferences = preferencesOf(SettingsStore.PROVIDERS to providersJson)

        val migrated = PreferenceStoreV1Migration().migrate(preferences)
        val providers = JsonInstant.decodeFromString<List<ProviderSetting>>(
            migrated[SettingsStore.PROVIDERS] ?: "[]"
        )

        assertEquals(listOf(remoteProvider.id), providers.map { it.id })
    }

    @Test
    fun `clearMissingModelReferences clears removed local model ids`() {
        val chatModel = Model(
            id = Uuid.parse("22222222-2222-2222-2222-222222222222"),
            modelId = "remote-chat",
            displayName = "Remote Chat",
            type = ModelType.CHAT,
        )
        val embeddingModel = Model(
            id = Uuid.parse("33333333-3333-3333-3333-333333333333"),
            modelId = "remote-embedding",
            displayName = "Remote Embedding",
            type = ModelType.EMBEDDING,
        )
        val removedLocalModelId = Uuid.parse("44444444-4444-4444-4444-444444444444")
        val settings = Settings(
            providers = listOf(
                ProviderSetting.OpenAI(
                    id = Uuid.parse("55555555-5555-5555-5555-555555555555"),
                    models = listOf(chatModel, embeddingModel),
                )
            ),
            chatModelId = removedLocalModelId,
            titleModelId = removedLocalModelId,
            translateModeId = removedLocalModelId,
            suggestionModelId = removedLocalModelId,
            embeddingModelId = removedLocalModelId,
            favoriteModels = listOf(removedLocalModelId, chatModel.id),
        )

        val normalized = settings.clearMissingModelReferences()

        assertEquals(chatModel.id, normalized.chatModelId)
        assertEquals(chatModel.id, normalized.titleModelId)
        assertEquals(chatModel.id, normalized.translateModeId)
        assertEquals(chatModel.id, normalized.suggestionModelId)
        assertEquals(embeddingModel.id, normalized.embeddingModelId)
        assertEquals(listOf(chatModel.id), normalized.favoriteModels)
        assertTrue(normalized.providers.none { it.name == "On-device" })
    }

}
