package me.rerere.rikkahub.data.datastore

import androidx.datastore.preferences.core.preferencesOf
import kotlinx.coroutines.runBlocking
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ModelType
import me.rerere.ai.provider.ProviderSetting
import me.rerere.rikkahub.data.datastore.migration.PreferenceStoreV1Migration
import me.rerere.rikkahub.data.model.Assistant
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
    fun `preference migration keeps providers json when strip parse fails`() = runBlocking {
        val garbage = "{not-json"
        val preferences = preferencesOf(SettingsStore.PROVIDERS to garbage)

        val migrated = PreferenceStoreV1Migration().migrate(preferences)

        assertEquals(garbage, migrated[SettingsStore.PROVIDERS])
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

    @Test
    fun `missing embedding selection becomes explicitly disabled when no embedding model exists`() {
        val chatModel = Model(
            id = Uuid.parse("66666666-6666-6666-6666-666666666666"),
            modelId = "chat-only",
            type = ModelType.CHAT,
        )
        val normalized = Settings(
            providers = listOf(ProviderSetting.OpenAI(models = listOf(chatModel))),
            embeddingModelId = Uuid.parse("77777777-7777-7777-7777-777777777777"),
        ).clearMissingModelReferences()

        assertEquals(DISABLED_MODEL_ID, normalized.embeddingModelId)
    }

    @Test
    fun `advanced memory normalizes hidden states that disable recall`() {
        val normalized = Settings(
            assistants = listOf(
                Assistant(
                    enableMemory = false,
                    useRagMemoryRetrieval = false,
                    enableMemoryConsolidation = true,
                    enableRecentChatsReference = false,
                    ragIncludeCore = false,
                    ragIncludeEpisodes = false,
                    ragLimit = 0,
                    ragSimilarityThreshold = Float.NaN,
                )
            )
        ).normalizeMemorySettings().assistants.single()

        assertTrue(normalized.enableMemory)
        assertTrue(normalized.useRagMemoryRetrieval)
        assertTrue(normalized.enableRecentChatsReference)
        assertTrue(normalized.ragIncludeCore)
        assertTrue(normalized.ragIncludeEpisodes)
        assertEquals(1, normalized.ragLimit)
        assertEquals(0.45f, normalized.ragSimilarityThreshold)
    }

    @Test
    fun `local provider is created when any on-device model exists`() {
        val stt = Model(modelId = "whisper-local", displayName = "Whisper", type = ModelType.STT)
        val settings = Settings(providers = listOf(ProviderSetting.OpenAI()))

        val created = settings.withSyncedLocalProviderModels(
            llmAndEmbeddingModels = emptyList(),
            sttModels = listOf(stt),
        )

        val local = created.providers.filterIsInstance<ProviderSetting.LiteRtLocal>().single()
        assertEquals(listOf(stt.modelId), local.models.map { it.modelId })
    }

    @Test
    fun `empty install list does not recreate a deleted local provider`() {
        val settings = Settings(providers = listOf(ProviderSetting.OpenAI()))
        val unchanged = settings.withSyncedLocalProviderModels(
            llmAndEmbeddingModels = emptyList(),
            sttModels = emptyList(),
        )
        assertTrue(unchanged.providers.none { it is ProviderSetting.LiteRtLocal })
    }

    @Test
    fun `existing local provider stays in place while models sync`() {
        val existing = ProviderSetting.LiteRtLocal(name = "Local")
        val remote = ProviderSetting.OpenAI()
        val chat = Model(modelId = "gemma-local", displayName = "Gemma", type = ModelType.CHAT)
        val settings = Settings(providers = listOf(remote, existing))

        val synced = settings.withSyncedLocalProviderModels(
            llmAndEmbeddingModels = listOf(chat),
            sttModels = emptyList(),
        )

        assertEquals(listOf(remote.id, existing.id), synced.providers.map { it.id })
        val local = synced.providers.filterIsInstance<ProviderSetting.LiteRtLocal>().single()
        assertEquals(listOf(chat.modelId), local.models.map { it.modelId })
    }

    @Test
    fun `bing search services migrate to keyless and enable Generical search`() {
        val bing = me.rerere.search.SearchServiceOptions.BingLocalOptions()
        val settings = Settings(
            searchServices = listOf(bing),
            assistants = listOf(
                Assistant(
                    id = DEFAULT_ASSISTANT_ID,
                    name = "Generical",
                    searchMode = me.rerere.rikkahub.data.model.AssistantSearchMode.Off,
                )
            ),
        )

        val migrated = settings.normalizeSearchServices()

        assertTrue(migrated.searchServices.single() is me.rerere.search.SearchServiceOptions.KeylessOptions)
        assertEquals(bing.id, migrated.searchServices.single().id)
        assertTrue(
            migrated.assistants.single().searchMode is me.rerere.rikkahub.data.model.AssistantSearchMode.Provider
        )
    }

    @Test
    fun `keyless search services are left unchanged`() {
        val keyless = me.rerere.search.SearchServiceOptions.KeylessOptions()
        val settings = Settings(searchServices = listOf(keyless))
        assertEquals(settings.searchServices, settings.normalizeSearchServices().searchServices)
    }
}
