package me.rerere.rikkahub.data.ai.models

import java.io.File
import me.rerere.ai.provider.*
import org.junit.Assert.*
import org.junit.Test

class CurrentCatalogTest {
    @Test fun bundledCatalogResolvesNewVisionAndReasoningModelsWithoutGrantingVisionToPro() {
        val file = sequenceOf(File("../catalog/lastchat_catalog.json"), File("catalog/lastchat_catalog.json")).first { it.exists() }
        val snapshot = ModelCatalogParser.parse(file.readText())
        val resolver = ModelMetadataResolver { snapshot }
        for (id in listOf("deepseek-flash", "deepseek-v4.1-flash", "deepseek-v4.1-flash-free", "gpt-6.1-sol", "gpt-6-astra", "gemini-4-argon")) {
            val model = resolver.applyToModel(Model(modelId = id))
            assertTrue("$id vision", model.inputModalities.contains(Modality.IMAGE))
            assertTrue("$id reasoning", model.abilities.contains(ModelAbility.REASONING))
            assertTrue("$id tools", model.abilities.contains(ModelAbility.TOOL))
            assertTrue("$id context", (model.contextWindowTokens ?: 0) >= 1_000_000)
        }
        assertEquals(listOf(Modality.TEXT), resolver.applyToModel(Model(modelId = "deepseek-v4-pro")).inputModalities)
        assertEquals(1_048_576, resolver.applyToModel(Model(modelId = "deepseek-flash")).contextWindowTokens)
        assertEquals(1_048_576, resolver.applyToModel(Model(modelId = "gemini-4-argon")).contextWindowTokens)
        assertFalse(resolver.applyToModel(Model(modelId = "gpt-6.1-sol")).reasoningConfig!!.supportedLevels.contains("off"))
    }

    @Test fun bundledCatalogOmitsDiscontinuedPoolsideProviderPreset() {
        val file = sequenceOf(File("../catalog/lastchat_catalog.json"), File("catalog/lastchat_catalog.json")).first { it.exists() }
        val snapshot = ModelCatalogParser.parse(file.readText())
        assertTrue(
            "Poolside provider preset should be removed from catalog providers",
            snapshot.providers.none {
                it.id.equals("d1f92a0e-c1d4-45aa-9b24-78fdbe4a3203", ignoreCase = true) ||
                    it.name.equals("Poolside", ignoreCase = true) ||
                    it.baseUrl.contains("poolside.ai", ignoreCase = true)
            },
        )
    }
}
