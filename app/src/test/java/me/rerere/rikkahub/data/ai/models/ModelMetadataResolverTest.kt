package me.rerere.rikkahub.data.ai.models

import me.rerere.ai.provider.Modality
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ModelAbility
import me.rerere.ai.provider.ProviderSetting
import org.junit.Assert.assertEquals
import org.junit.Test

class ModelMetadataResolverTest {
    @Test
    fun resolvesExactModelIdMatch() {
        val resolver = resolverFor(
            """
            {
              "sample_spec": {},
              "gpt-5-mini": {
                "litellm_provider": "openai",
                "mode": "chat",
                "supports_function_calling": true,
                "supports_reasoning": true,
                "supports_vision": true
              }
            }
            """.trimIndent()
        )

        val resolved = resolver.applyToModel(Model(modelId = "gpt-5-mini"))

        assertEquals("gpt-5-mini", resolved.canonicalModelId)
        assertEquals(listOf(Modality.TEXT, Modality.IMAGE), resolved.inputModalities)
        assertEquals(listOf(Modality.TEXT), resolved.outputModalities)
        assertEquals(listOf(ModelAbility.TOOL, ModelAbility.REASONING), resolved.abilities)
    }

    @Test
    fun resolvesExactCanonicalKeyWhenStoredCanonicalIdExists() {
        val resolver = resolverFor(
            """
            {
              "sample_spec": {},
              "gpt-5-mini": {
                "litellm_provider": "openai",
                "mode": "chat",
                "supports_function_calling": true
              }
            }
            """.trimIndent()
        )

        val resolved = resolver.applyToModel(
            model = Model(
                modelId = "openrouter/openai/gpt-5-mini-preview",
                canonicalModelId = "gpt-5-mini",
            )
        )

        assertEquals("gpt-5-mini", resolved.canonicalModelId)
        assertEquals(listOf(ModelAbility.TOOL), resolved.abilities)
    }

    @Test
    fun skipsAmbiguousCanonicalBucketWithoutHint() {
        val resolver = resolverFor(
            """
            {
              "sample_spec": {},
              "openai/custom-model": {
                "litellm_provider": "openai",
                "mode": "chat",
                "supports_function_calling": true
              },
              "azure/custom-model": {
                "litellm_provider": "azure",
                "mode": "chat",
                "supports_function_calling": false
              }
            }
            """.trimIndent()
        )

        val resolved = resolver.applyToModel(Model(modelId = "custom-model"))

        assertEquals("custom-model", resolved.canonicalModelId)
        assertEquals(emptyList<ModelAbility>(), resolved.abilities)
        assertEquals(listOf(Modality.TEXT), resolved.inputModalities)
    }

    @Test
    fun resolvesAmbiguousCanonicalBucketWithProviderHint() {
        val resolver = resolverFor(
            """
            {
              "sample_spec": {},
              "openai/custom-model": {
                "litellm_provider": "openai",
                "mode": "chat",
                "supports_function_calling": true
              },
              "azure/custom-model": {
                "litellm_provider": "azure",
                "mode": "chat",
                "supports_function_calling": false
              }
            }
            """.trimIndent()
        )

        val resolved = resolver.applyToModel(
            model = Model(modelId = "custom-model"),
            providerHint = ProviderSetting.OpenAI(baseUrl = "https://api.openai.com/v1"),
        )

        assertEquals(listOf(ModelAbility.TOOL), resolved.abilities)
    }

    @Test
    fun preservesApiDisplayNameWhenRequested() {
        val resolver = resolverFor(
            """
            {
              "sample_spec": {},
              "gpt-5-mini": {
                "litellm_provider": "openai",
                "mode": "chat",
                "supports_function_calling": true,
                "supports_reasoning": true
              }
            }
            """.trimIndent()
        )

        val resolved = resolver.applyToModel(
            model = Model(
                modelId = "gpt-5-mini",
                displayName = "GPT-5 mini from API",
            ),
            providerHint = ProviderSetting.OpenAI(baseUrl = "https://api.openai.com/v1"),
            options = ModelResolutionOptions(
                preserveDisplayName = true,
                preserveExistingCapabilities = true,
                preserveExistingType = true,
            ),
        )

        assertEquals("GPT-5 mini from API", resolved.displayName)
        assertEquals(listOf(ModelAbility.TOOL, ModelAbility.REASONING), resolved.abilities)
    }

    @Test
    fun doesNotFuzzyMatchDifferentModelIds() {
        val resolver = resolverFor(
            """
            {
              "sample_spec": {},
              "gpt-5-mini": {
                "litellm_provider": "openai",
                "mode": "chat",
                "supports_function_calling": true
              }
            }
            """.trimIndent()
        )

        val resolved = resolver.applyToModel(Model(modelId = "gpt-5-mini-custom"))

        assertEquals("gpt-5-mini-custom", resolved.canonicalModelId)
        assertEquals(emptyList<ModelAbility>(), resolved.abilities)
    }

    private fun resolverFor(rawJson: String): ModelMetadataResolver {
        val snapshot = ModelCatalogParser.parse(rawJson)
        return ModelMetadataResolver(snapshotProvider = { snapshot })
    }
}
