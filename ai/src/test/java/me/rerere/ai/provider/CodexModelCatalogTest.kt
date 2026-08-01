package me.rerere.ai.provider

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CodexModelCatalogTest {
    @Test
    fun `listed gpt 5_6 models are exposed while hidden internal models stay hidden`() {
        val models = parseCodexModelCatalog(
            Json.parseToJsonElement(
                """
                {
                  "models": [
                    {
                      "slug": "gpt-5.6-sol",
                      "display_name": "GPT-5.6-Sol",
                      "visibility": "list",
                      "input_modalities": ["text", "image", "audio"],
                      "supported_reasoning_levels": [{"effort": "high"}],
                      "context_window": 372000
                    },
                    {
                      "slug": "codex-auto-review",
                      "display_name": "Codex Auto Review",
                      "visibility": "hide",
                      "context_window": 272000
                    }
                  ]
                }
                """.trimIndent()
            ).jsonObject
        )

        assertEquals(listOf("gpt-5.6-sol"), models.map(Model::modelId))
        val model = models.single()
        assertEquals(ModelType.CHAT, model.type)
        assertEquals(listOf(Modality.TEXT, Modality.IMAGE, Modality.AUDIO), model.inputModalities)
        assertEquals(372_000, model.contextWindowTokens)
        assertEquals(ContextLimitSource.PROVIDER, model.contextLimitSource)
        assertTrue(ModelAbility.TOOL in model.abilities)
        assertTrue(ModelAbility.REASONING in model.abilities)
        assertFalse(models.any { it.modelId == "codex-auto-review" })
    }

    @Test
    fun `legacy manifests retain text and image chat defaults`() {
        val model = parseCodexModelCatalog(
            Json.parseToJsonElement(
                """{"models":[{"slug":"gpt-5.5","visibility":"list","max_context_window":272000}]}"""
            ).jsonObject
        ).single()

        assertEquals(ModelType.CHAT, model.type)
        assertEquals(listOf(Modality.TEXT, Modality.IMAGE), model.inputModalities)
        assertEquals(272_000, model.contextWindowTokens)
    }
}
