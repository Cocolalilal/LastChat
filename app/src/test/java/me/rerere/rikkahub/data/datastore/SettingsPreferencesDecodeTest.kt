package me.rerere.rikkahub.data.datastore

import me.rerere.ai.provider.ProviderSetting
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.utils.JsonInstant
import me.rerere.search.SearchServiceOptions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.uuid.Uuid

class SettingsPreferencesDecodeTest {
    @Test
    fun `unknown provider type is skipped without dropping other providers`() {
        val kept = ProviderSetting.OpenAI(
            id = Uuid.parse("d5734028-d39b-4d41-9841-fd648d65440e"),
            name = "OpenRouter",
            baseUrl = "https://openrouter.ai/api/v1",
        )
        val json = """
            [
              ${JsonInstant.encodeToString(ProviderSetting.serializer(), kept)},
              {
                "type": "codex",
                "id": "11111111-1111-1111-1111-111111111111",
                "enabled": true,
                "name": "Codex",
                "models": [],
                "proxy": { "type": "none" },
                "balanceOption": {},
                "tags": []
              }
            ]
        """.trimIndent()

        val decoded = decodePreferenceList<ProviderSetting>(json)

        assertEquals(listOf(kept.id), decoded?.map { it.id })
    }

    @Test
    fun `invalid search service does not wipe assistants`() {
        val assistantId = Uuid.parse("0950e2dc-9bd5-4801-afa3-aa887aa36b4e")
        val assistantsJson = JsonInstant.encodeToString(
            listOf(Assistant(id = assistantId, name = "Generical"))
        )
        val searchJson = """
            [
              { "type": "bing_local", "id": "22222222-2222-2222-2222-222222222222" },
              { "type": "does_not_exist", "id": "33333333-3333-3333-3333-333333333333" }
            ]
        """.trimIndent()

        val assistants = decodePreferenceList<Assistant>(assistantsJson)
        val search = decodePreferenceList<SearchServiceOptions>(searchJson)

        assertEquals(listOf(assistantId), assistants?.map { it.id })
        assertEquals(1, search?.size)
        assertTrue(search?.single() is SearchServiceOptions.BingLocalOptions)
    }

    @Test
    fun `orphaned conversation assistant ids are restored as stubs`() {
        val existing = Assistant(
            id = Uuid.parse("0950e2dc-9bd5-4801-afa3-aa887aa36b4e"),
            name = "Generical",
        )
        val orphan = Uuid.parse("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")
        val settings = Settings(
            init = false,
            assistants = listOf(existing),
            assistantId = existing.id,
        )

        val recovered = settings.withRecoveredAssistantsFromConversations(
            listOf(existing.id.toString(), orphan.toString()),
        )

        assertEquals(setOf(existing.id, orphan), recovered.assistants.map { it.id }.toSet())
        assertEquals("Recovered", recovered.assistants.single { it.id == orphan }.name)
        assertEquals(existing.id, recovered.assistantId)
    }

    @Test
    fun `selected assistant is switched when it disappeared but chats remain`() {
        val orphan = Uuid.parse("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb")
        val settings = Settings(
            init = false,
            assistants = DEFAULT_ASSISTANTS,
            assistantId = Uuid.parse("cccccccc-cccc-cccc-cccc-cccccccccccc"),
        )

        val recovered = settings.withRecoveredAssistantsFromConversations(listOf(orphan.toString()))

        assertTrue(recovered.assistants.any { it.id == orphan })
        assertEquals(orphan, recovered.assistantId)
    }
}
