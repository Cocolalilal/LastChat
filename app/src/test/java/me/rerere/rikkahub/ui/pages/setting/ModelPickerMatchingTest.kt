package me.rerere.rikkahub.ui.pages.setting

import me.rerere.ai.provider.ImageGenerationMethod
import me.rerere.ai.provider.Modality
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ModelAbility
import me.rerere.ai.provider.ModelType
import me.rerere.ai.provider.ProviderSetting
import me.rerere.rikkahub.data.ai.models.ModelCatalogParser
import me.rerere.rikkahub.data.ai.models.ModelMetadataResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelPickerMatchingTest {
    @Test
    fun endpointModelUsesCatalogMetadataBeforeItIsAdded() {
        val snapshot = ModelCatalogParser.parse(
            """
            {
              "schema_version": 1,
              "model_families": [{
                "id": "gpt",
                "match_patterns": ["gpt-5"],
                "icon": "icons/openai.svg",
                "type": "CHAT",
                "input_modalities": ["TEXT", "IMAGE"],
                "output_modalities": ["TEXT"],
                "abilities": ["TOOL", "REASONING"],
                "provider_slug": "openai"
              }]
            }
            """.trimIndent()
        )
        val endpointModel = Model(
            modelId = "gpt-5-mini",
            type = ModelType.IMAGE,
            inputModalities = listOf(Modality.TEXT),
            outputModalities = listOf(Modality.IMAGE),
            abilities = emptyList(),
        )

        val resolved = resolveProviderModel(
            resolver = ModelMetadataResolver { snapshot },
            provider = ProviderSetting.OpenAI(),
            model = endpointModel,
        )

        assertEquals(ModelType.CHAT, resolved.type)
        assertEquals(listOf(Modality.TEXT, Modality.IMAGE), resolved.inputModalities)
        assertEquals(listOf(Modality.TEXT), resolved.outputModalities)
        assertEquals(listOf(ModelAbility.TOOL, ModelAbility.REASONING), resolved.abilities)
        assertEquals("openai", resolved.providerSlug)
        assertTrue(resolved.iconUrl?.endsWith("/catalog/icons/openai.svg") == true)
    }

    @Test
    fun endpointMetadataRemainsAvailableWhenCatalogHasNoMatch() {
        val snapshot = ModelCatalogParser.parse("""{ "schema_version": 1 }""")
        val endpointModel = Model(
            modelId = "vendor-new-image-model",
            type = ModelType.IMAGE,
            inputModalities = listOf(Modality.TEXT, Modality.IMAGE),
            outputModalities = listOf(Modality.IMAGE),
            abilities = listOf(ModelAbility.REASONING),
            imageGenerationMethod = ImageGenerationMethod.DIFFUSION,
        )

        val resolved = resolveProviderModel(
            resolver = ModelMetadataResolver { snapshot },
            provider = ProviderSetting.OpenAI(),
            model = endpointModel,
        )

        assertEquals(endpointModel.type, resolved.type)
        assertEquals(endpointModel.inputModalities, resolved.inputModalities)
        assertEquals(endpointModel.outputModalities, resolved.outputModalities)
        assertEquals(endpointModel.abilities, resolved.abilities)
        assertEquals(endpointModel.imageGenerationMethod, resolved.imageGenerationMethod)
    }

    @Test
    fun matchesExactModelId() {
        assertTrue(
            modelsReferToSameApiModel(
                Model(modelId = "gpt-5-mini"),
                Model(modelId = "gpt-5-mini"),
            )
        )
    }

    @Test
    fun matchesCanonicalEquivalentModelIds() {
        assertTrue(
            modelsReferToSameApiModel(
                Model(modelId = "gpt-5-mini", canonicalModelId = "gpt-5-mini"),
                Model(modelId = "models/gpt-5-mini"),
            )
        )
    }

    @Test
    fun rejectsDifferentProviderSlugForSameCanonicalId() {
        assertFalse(
            modelsReferToSameApiModel(
                Model(modelId = "provider-a/custom-model", canonicalModelId = "custom-model", providerSlug = "provider-a"),
                Model(modelId = "provider-b/custom-model", canonicalModelId = "custom-model", providerSlug = "provider-b"),
            )
        )
    }

    @Test
    fun rejectsDifferentRemovableQualifiers() {
        // e.g. kimi k2.6 vs kimi k2.6:(free) should not match
        assertFalse(
            modelsReferToSameApiModel(
                Model(modelId = "kimi-k2.6"),
                Model(modelId = "kimi-k2.6:(free)"),
            )
        )
        assertFalse(
            modelsReferToSameApiModel(
                Model(modelId = "kimi-k2.6"),
                Model(modelId = "kimi-k2.6-free"),
            )
        )
        assertFalse(
            modelsReferToSameApiModel(
                Model(modelId = "gemini-2.5-pro"),
                Model(modelId = "gemini-2.5-pro-preview"),
            )
        )
    }

    @Test
    fun matchesSameBaseModelWithDateSuffix() {
        // Date suffixes (no qualifiers) should match general models if needed
        assertTrue(
            modelsReferToSameApiModel(
                Model(modelId = "gpt-4o"),
                Model(modelId = "gpt-4o-2024-05-13"),
            )
        )
    }

    @Test
    fun refreshingApiMetadataPreservesSavedUserConfiguration() {
        val saved = Model(
            modelId = "gpt-5-mini",
            displayName = "My model",
            type = ModelType.CHAT,
            inputModalities = listOf(Modality.TEXT),
            outputModalities = listOf(Modality.TEXT),
            abilities = emptyList(),
            imageGenerationMethod = null,
        )
        val fresh = Model(
            modelId = "gpt-5-mini",
            displayName = "GPT-5 Mini",
            type = ModelType.IMAGE,
            inputModalities = listOf(Modality.TEXT, Modality.IMAGE),
            outputModalities = listOf(Modality.IMAGE),
            abilities = listOf(ModelAbility.TOOL, ModelAbility.REASONING),
            imageGenerationMethod = ImageGenerationMethod.DIFFUSION,
            iconUrl = "https://example.com/model.svg",
            providerSlug = "openai",
        )
        val provider = ProviderSetting.OpenAI(models = listOf(saved))

        val synced = syncFreshModelMetadata(
            freshModels = listOf(fresh),
            currentProvider = provider,
        ) as ProviderSetting.OpenAI

        val model = synced.models.single()
        assertEquals("My model", model.displayName)
        assertEquals(ModelType.CHAT, model.type)
        assertEquals(listOf(Modality.TEXT), model.inputModalities)
        assertEquals(listOf(Modality.TEXT), model.outputModalities)
        assertEquals(emptyList<ModelAbility>(), model.abilities)
        assertNull(model.imageGenerationMethod)
        assertEquals(fresh.iconUrl, model.iconUrl)
        assertEquals(fresh.providerSlug, model.providerSlug)
    }
}
