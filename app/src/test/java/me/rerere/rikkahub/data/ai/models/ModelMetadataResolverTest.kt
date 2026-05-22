package me.rerere.rikkahub.data.ai.models

import kotlinx.serialization.json.JsonPrimitive
import me.rerere.ai.core.ReasoningLevel
import me.rerere.ai.provider.Modality
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ModelAbility
import me.rerere.ai.provider.ProviderSetting
import me.rerere.rikkahub.data.datastore.Settings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ModelMetadataResolverTest {
    @Test
    fun resolvesExactModelIdMatch() {
        val resolver = resolverFor(
            """
            {
              "schema_version": 1,
              "models": [{
                "id": "gpt-5-mini",
                "canonical_model_id": "gpt-5-mini",
                "type": "CHAT",
                "input_modalities": ["TEXT", "IMAGE"],
                "output_modalities": ["TEXT"],
                "abilities": ["TOOL", "REASONING"]
              }]
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
              "schema_version": 1,
              "models": [{
                "id": "gpt-5-mini",
                "canonical_model_id": "gpt-5-mini",
                "type": "CHAT",
                "abilities": ["TOOL"]
              }]
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
    fun resolvesApiAliasesThroughCatalogIndex() {
        val resolver = resolverFor(
            """
            {
              "schema_version": 1,
              "models": [{
                "id": "foo-model",
                "canonical_model_id": "foo-model",
                "api_aliases": ["models/foo-model", "provider/foo-model-2026-01-01-preview"],
                "type": "CHAT",
                "abilities": ["TOOL", "REASONING"]
              }]
            }
            """.trimIndent()
        )

        val prefixed = resolver.applyToModel(Model(modelId = "models/foo-model"))
        val dated = resolver.applyToModel(Model(modelId = "provider/foo-model-2026-01-01-preview"))

        assertEquals("foo-model", prefixed.canonicalModelId)
        assertEquals(listOf(ModelAbility.TOOL, ModelAbility.REASONING), prefixed.abilities)
        assertEquals("foo-model", dated.canonicalModelId)
        assertEquals(listOf(ModelAbility.TOOL, ModelAbility.REASONING), dated.abilities)
    }

    @Test
    fun skipsAmbiguousCanonicalBucketWithoutHint() {
        val resolver = resolverFor(
            """
            {
              "schema_version": 1,
              "models": [
                {
                  "id": "openai/custom-model",
                  "canonical_model_id": "custom-model",
                  "provider_slug": "openai",
                  "type": "CHAT",
                  "abilities": ["TOOL"]
                },
                {
                  "id": "azure/custom-model",
                  "canonical_model_id": "custom-model",
                  "provider_slug": "azure",
                  "type": "CHAT"
                }
              ]
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
              "schema_version": 1,
              "models": [
                {
                  "id": "openai/custom-model",
                  "canonical_model_id": "custom-model",
                  "provider_slug": "openai",
                  "type": "CHAT",
                  "abilities": ["TOOL"]
                },
                {
                  "id": "azure/custom-model",
                  "canonical_model_id": "custom-model",
                  "provider_slug": "azure",
                  "type": "CHAT"
                }
              ]
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
              "schema_version": 1,
              "models": [{
                "id": "gpt-5-mini",
                "canonical_model_id": "gpt-5-mini",
                "display_name": "GPT-5 mini catalog",
                "type": "CHAT",
                "abilities": ["TOOL", "REASONING"]
              }]
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
              "schema_version": 1,
              "models": [{
                "id": "gpt-5-mini",
                "canonical_model_id": "gpt-5-mini",
                "type": "CHAT",
                "abilities": ["TOOL"]
              }]
            }
            """.trimIndent()
        )

        val resolved = resolver.applyToModel(Model(modelId = "gpt-5-mini-custom"))

        assertEquals("gpt-5-mini-custom", resolved.canonicalModelId)
        assertEquals(emptyList<ModelAbility>(), resolved.abilities)
    }

    @Test
    fun usesOnlyCatalogProviderSlugForIcons() {
        val resolver = resolverFor(
            """
            {
              "schema_version": 1,
              "models": [{
                "id": "my-custom-google-model",
                "canonical_model_id": "my-custom-google-model",
                "provider_slug": "vertex_ai",
                "type": "CHAT"
              }]
            }
            """.trimIndent()
        )

        val resolved = resolver.applyToModel(Model(modelId = "my-custom-google-model"))

        assertEquals("google", resolved.providerSlug)
    }

    @Test
    fun doesNotInferProviderSlugFromApiModelId() {
        val resolver = resolverFor(
            """
            {
              "schema_version": 1,
              "models": []
            }
            """.trimIndent()
        )

        val resolved = resolver.applyToModel(Model(modelId = "anthropic/claude-sonnet-4.5"))

        assertNull(resolved.providerSlug)
        assertNull(resolved.iconUrl)
    }

    @Test
    fun infersModelCapabilitiesFromFamilyRules() {
        val resolver = resolverFor(
            """
            {
              "schema_version": 1,
              "model_families": [{
                "id": "gemini",
                "display_name": "Gemini",
                "match_patterns": ["gemini"],
                "icon": "icons/gemini.svg",
                "input_modalities": ["TEXT", "IMAGE"],
                "output_modalities": ["TEXT"],
                "abilities": ["TOOL", "REASONING"],
                "provider_slug": "google"
              }],
              "models": []
            }
            """.trimIndent()
        )

        val resolved = resolver.applyToModel(Model(modelId = "models/gemini-3-flash-preview"))

        assertEquals("gemini-3-flash", resolved.canonicalModelId)
        assertEquals(listOf(Modality.TEXT, Modality.IMAGE), resolved.inputModalities)
        assertEquals(listOf(ModelAbility.TOOL, ModelAbility.REASONING), resolved.abilities)
        assertEquals("google", resolved.providerSlug)
        assertNotNull(resolved.iconUrl)
    }

    @Test
    fun familyVersionsRefineBaseCapabilities() {
        val resolver = resolverFor(
            """
            {
              "schema_version": 1,
              "model_families": [{
                "id": "qwen",
                "display_name": "Qwen",
                "match_patterns": ["qwen"],
                "input_modalities": ["TEXT"],
                "output_modalities": ["TEXT"],
                "abilities": ["TOOL"],
                "versions": [
                  {
                    "id": "qwen3",
                    "match_patterns": ["qwen3"],
                    "abilities": ["TOOL", "REASONING"]
                  },
                  {
                    "id": "qwen-vl",
                    "match_patterns": ["vl"],
                    "input_modalities": ["TEXT", "IMAGE"]
                  }
                ]
              }],
              "models": []
            }
            """.trimIndent()
        )

        val resolved = resolver.applyToModel(Model(modelId = "Qwen/Qwen3-VL-235B-A22B-Instruct"))

        assertEquals(listOf(Modality.TEXT, Modality.IMAGE), resolved.inputModalities)
        assertEquals(listOf(ModelAbility.TOOL, ModelAbility.REASONING), resolved.abilities)
    }

    @Test
    fun exactModelEntryOverridesFamilyInference() {
        val resolver = resolverFor(
            """
            {
              "schema_version": 1,
              "model_families": [{
                "id": "gemini",
                "display_name": "Gemini",
                "match_patterns": ["gemini"],
                "input_modalities": ["TEXT", "IMAGE"],
                "abilities": ["TOOL", "REASONING"]
              }],
              "models": [{
                "id": "gemini-embedding-001",
                "canonical_model_id": "gemini-embedding-001",
                "family_id": "gemini",
                "type": "EMBEDDING",
                "input_modalities": ["TEXT"],
                "output_modalities": ["TEXT"],
                "abilities": []
              }]
            }
            """.trimIndent()
        )

        val resolved = resolver.applyToModel(Model(modelId = "gemini-embedding-001"))

        assertEquals(me.rerere.ai.provider.ModelType.EMBEDDING, resolved.type)
        assertEquals(listOf(Modality.TEXT), resolved.inputModalities)
        assertEquals(emptyList<ModelAbility>(), resolved.abilities)
    }

    @Test
    fun parsesLegacyGroupsAndGroupIdsAsFamilies() {
        val resolver = resolverFor(
            """
            {
              "schema_version": 1,
              "model_groups": [{
                "id": "claude",
                "display_name": "Claude",
                "match_patterns": ["claude"],
                "icon": "icons/claude.svg"
              }],
              "models": [{
                "id": "claude-sonnet-4-5",
                "group_id": "claude",
                "type": "CHAT",
                "input_modalities": ["TEXT", "IMAGE"],
                "abilities": ["TOOL", "REASONING"]
              }]
            }
            """.trimIndent()
        )

        val resolved = resolver.applyToModel(Model(modelId = "claude-sonnet-4-5"))

        assertEquals(listOf(Modality.TEXT, Modality.IMAGE), resolved.inputModalities)
        assertNotNull(resolved.iconUrl)
    }

    @Test
    fun parsesUnknownKeysAndReasoningBehavior() {
        val snapshot = snapshotFor(
            """
            {
              "schema_version": 1,
              "unexpected": "ignored",
              "models": [{
                "id": "qwen3-max",
                "canonical_model_id": "qwen3-max",
                "type": "CHAT",
                "abilities": ["REASONING"],
                "reasoning_behavior": {
                  "off": [{ "key": "enable_thinking", "value": false }]
                }
              }]
            }
            """.trimIndent()
        )

        val entry = snapshot.exactEntries["qwen3-max"]
        assertNotNull(entry)
        assertEquals(
            JsonPrimitive(false),
            entry?.reasoningBehavior?.bodiesFor(ReasoningLevel.OFF)?.single()?.value,
        )
    }

    @Test
    fun safeMergePreservesSecretsAndDoesNotAddCatalogModels() {
        val snapshot = snapshotFor(
            """
            {
              "schema_version": 1,
              "providers": [{
                "id": "d5734028-d39b-4d41-9841-fd648d65440e",
                "name": "OpenRouter",
                "type": "openai",
                "base_url": "https://openrouter.ai/api/v1",
                "preset": true,
                "built_in": true
              }],
              "models": [{
                "id": "openai/gpt-5-mini",
                "canonical_model_id": "gpt-5-mini",
                "provider_ids": ["d5734028-d39b-4d41-9841-fd648d65440e"],
                "provider_slug": "openai",
                "type": "CHAT",
                "abilities": ["TOOL"]
              }]
            }
            """.trimIndent()
        )
        val resolver = ModelMetadataResolver { snapshot }
        val existing = ProviderSetting.OpenAI(
            id = kotlin.uuid.Uuid.parse("d5734028-d39b-4d41-9841-fd648d65440e"),
            name = "My OpenRouter",
            apiKey = "secret",
            enabled = false,
            models = emptyList(),
        )

        val merged = mergeCatalogIntoSettings(
            settings = Settings(providers = listOf(existing)),
            snapshot = snapshot,
            resolver = resolver,
        )

        val provider = merged.providers.single() as ProviderSetting.OpenAI
        assertEquals("secret", provider.apiKey)
        assertEquals(false, provider.enabled)
        assertEquals("My OpenRouter", provider.name)
        assertEquals(emptyList<String>(), provider.models.map { it.modelId })
    }

    private fun resolverFor(rawJson: String): ModelMetadataResolver {
        val snapshot = snapshotFor(rawJson)
        return ModelMetadataResolver(snapshotProvider = { snapshot })
    }

    private fun snapshotFor(rawJson: String): ModelCatalogSnapshot {
        return ModelCatalogParser.parse(rawJson)
    }
}
