package me.rerere.rikkahub.data.ai.models

import kotlinx.serialization.json.JsonPrimitive
import me.rerere.ai.core.ReasoningLevel
import me.rerere.ai.provider.Modality
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ModelAbility
import me.rerere.ai.provider.ModelType
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
    fun ignoresCatalogDisplayNameWhenApiNameIsNotPreserved() {
        val resolver = resolverFor(
            """
            {
              "schema_version": 1,
              "models": [{
                "id": "gpt-5-mini",
                "canonical_model_id": "gpt-5-mini",
                "display_name": "GPT-5 mini catalog",
                "type": "CHAT",
                "abilities": ["TOOL"]
              }]
            }
            """.trimIndent()
        )

        val resolved = resolver.applyToModel(Model(modelId = "gpt-5-mini"))

        assertEquals("GPT-5 Mini", resolved.displayName)
        assertEquals(listOf(ModelAbility.TOOL), resolved.abilities)
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
    fun globalRulesInferCapabilitiesWithoutFamilyOrModelEntry() {
        val resolver = resolverFor(
            """
            {
              "schema_version": 2,
              "global_rules": [
                {
                  "id": "vision",
                  "match_patterns": ["vision", "(^|[/._-])vl($|[/._-])"],
                  "input_modalities": ["TEXT", "IMAGE"]
                },
                {
                  "id": "thinking",
                  "match_patterns": ["thinking"],
                  "abilities": ["TOOL", "REASONING"]
                }
              ],
              "models": []
            }
            """.trimIndent()
        )

        val resolved = resolver.applyToModel(Model(modelId = "acme-thinking-vl"))

        assertEquals(listOf(Modality.TEXT, Modality.IMAGE), resolved.inputModalities)
        assertEquals(listOf(ModelAbility.TOOL, ModelAbility.REASONING), resolved.abilities)
    }

    @Test
    fun modelOverridesWinAfterFamilyInference() {
        val resolver = resolverFor(
            """
            {
              "schema_version": 2,
              "model_families": [{
                "id": "qwen",
                "display_name": "Qwen",
                "match_patterns": ["qwen"],
                "abilities": ["TOOL", "REASONING"]
              }],
              "model_overrides": [{
                "id": "qwen3-embed",
                "type": "EMBEDDING",
                "input_modalities": ["TEXT"],
                "output_modalities": ["TEXT"],
                "abilities": []
              }],
              "models": []
            }
            """.trimIndent()
        )

        val resolved = resolver.applyToModel(Model(modelId = "qwen3-embed"))

        assertEquals(ModelType.EMBEDDING, resolved.type)
        assertEquals(emptyList<ModelAbility>(), resolved.abilities)
    }

    @Test
    fun providerSpecificOverridesUseProviderHints() {
        val resolver = resolverFor(
            """
            {
              "schema_version": 2,
              "model_families": [{
                "id": "foo",
                "display_name": "Foo",
                "match_patterns": ["foo-model"],
                "abilities": ["TOOL"]
              }],
              "model_overrides": [{
                "match_patterns": ["foo-model"],
                "provider_slugs": ["openrouter"],
                "abilities": ["TOOL", "REASONING"]
              }],
              "models": []
            }
            """.trimIndent()
        )

        val generic = resolver.applyToModel(Model(modelId = "foo-model"))
        val openRouter = resolver.applyToModel(
            model = Model(modelId = "foo-model"),
            providerHint = ProviderSetting.OpenAI(baseUrl = "https://openrouter.ai/api/v1"),
        )

        assertEquals(listOf(ModelAbility.TOOL), generic.abilities)
        assertEquals(listOf(ModelAbility.TOOL, ModelAbility.REASONING), openRouter.abilities)
    }

    @Test
    fun catalogManagedModelIconDoesNotBlockFreshFamilyIcon() {
        val resolver = resolverFor(
            """
            {
              "schema_version": 2,
              "model_families": [{
                "id": "gemini",
                "display_name": "Gemini",
                "match_patterns": ["gemini"],
                "icon": "icons/gemini-new.svg"
              }],
              "models": []
            }
            """.trimIndent()
        )

        val resolved = resolver.applyToModel(
            Model(
                modelId = "gemini-3-flash",
                customIconUri = "https://raw.githubusercontent.com/Cocolalilal/LastChat/main/catalog/icons/gemini-old.svg",
            )
        )

        assertNull(resolved.customIconUri)
        assertEquals(
            "https://raw.githubusercontent.com/Cocolalilal/LastChat/main/catalog/icons/gemini-new.svg",
            resolved.iconUrl,
        )
    }

    @Test
    fun userSelectedModelIconSurvivesCatalogResolution() {
        val resolver = resolverFor(
            """
            {
              "schema_version": 2,
              "model_families": [{
                "id": "gemini",
                "display_name": "Gemini",
                "match_patterns": ["gemini"],
                "icon": "icons/gemini-new.svg"
              }],
              "models": []
            }
            """.trimIndent()
        )

        val resolved = resolver.applyToModel(
            Model(
                modelId = "gemini-3-flash",
                customIconUri = "content://user/icon.png",
            )
        )

        assertEquals("content://user/icon.png", resolved.customIconUri)
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

    @Test
    fun mergeCatalogMatchesExistingProvidersByBaseUrlAndRefreshesCatalogIcons() {
        val snapshot = snapshotFor(
            """
            {
              "schema_version": 1,
              "providers": [
                {
                  "id": "d5734028-d39b-4d41-9841-fd648d65440e",
                  "name": "OpenRouter",
                  "type": "openai",
                  "base_url": "https://openrouter.ai/api/v1",
                  "icon": "icons/openrouter-new.svg",
                  "preset": true
                },
                {
                  "id": "c1734028-d39b-4d41-9841-fd648d65440e",
                  "name": "Custom API",
                  "type": "openai",
                  "base_url": "https://example.com/v1",
                  "icon": "icons/example.svg",
                  "preset": true
                }
              ],
              "models": []
            }
            """.trimIndent()
        )
        val resolver = ModelMetadataResolver { snapshot }
        val catalogManagedIconProvider = ProviderSetting.OpenAI(
            id = kotlin.uuid.Uuid.parse("11111111-1111-4111-8111-111111111111"),
            name = "My OpenRouter",
            apiKey = "secret",
            baseUrl = "https://openrouter.ai/api/v1/",
            customIconUri = "https://raw.githubusercontent.com/Cocolalilal/LastChat/main/catalog/icons/openrouter-old.svg",
        )
        val userIconProvider = ProviderSetting.OpenAI(
            id = kotlin.uuid.Uuid.parse("22222222-2222-4222-8222-222222222222"),
            name = "Custom API",
            baseUrl = "https://example.com/v1",
            customIconUri = "file:///data/user/0/me.rerere.rikkahub/files/custom_icons/custom.png",
        )

        val merged = mergeCatalogIntoSettings(
            settings = Settings(providers = listOf(catalogManagedIconProvider, userIconProvider)),
            snapshot = snapshot,
            resolver = resolver,
        )

        assertEquals(2, merged.providers.size)
        val openRouter = merged.providers[0] as ProviderSetting.OpenAI
        assertEquals("secret", openRouter.apiKey)
        assertEquals("My OpenRouter", openRouter.name)
        assertEquals(
            "https://raw.githubusercontent.com/Cocolalilal/LastChat/main/catalog/icons/openrouter-new.svg",
            openRouter.customIconUri,
        )
        assertEquals(
            "file:///data/user/0/me.rerere.rikkahub/files/custom_icons/custom.png",
            merged.providers[1].customIconUri,
        )
    }

    @Test
    fun mergeCatalogAddsProviderIconWhenExistingProviderHasNone() {
        val snapshot = snapshotFor(
            """
            {
              "schema_version": 2,
              "providers": [{
                "id": "d5734028-d39b-4d41-9841-fd648d65440e",
                "name": "OpenRouter",
                "type": "openai",
                "base_url": "https://openrouter.ai/api/v1",
                "icon": "icons/openrouter-new.svg",
                "preset": true
              }],
              "models": []
            }
            """.trimIndent()
        )
        val resolver = ModelMetadataResolver { snapshot }
        val existing = ProviderSetting.OpenAI(
            id = kotlin.uuid.Uuid.parse("d5734028-d39b-4d41-9841-fd648d65440e"),
            name = "OpenRouter",
            baseUrl = "https://openrouter.ai/api/v1",
            customIconUri = null,
        )

        val merged = mergeCatalogIntoSettings(
            settings = Settings(providers = listOf(existing)),
            snapshot = snapshot,
            resolver = resolver,
        )

        assertEquals(
            "https://raw.githubusercontent.com/Cocolalilal/LastChat/main/catalog/icons/openrouter-new.svg",
            merged.providers.single().customIconUri,
        )
    }

    @Test
    fun mergeCatalogCanRefreshExistingProviderIconsWithoutAddingMissingPresets() {
        val snapshot = snapshotFor(
            """
            {
              "schema_version": 2,
              "providers": [
                {
                  "id": "d5734028-d39b-4d41-9841-fd648d65440e",
                  "name": "OpenRouter",
                  "type": "openai",
                  "base_url": "https://openrouter.ai/api/v1",
                  "icon": "icons/openrouter-new.svg",
                  "preset": true
                },
                {
                  "id": "8f9d0c75-8f29-4a27-9c2b-f8d4fd5f3e91",
                  "name": "OpenAI",
                  "type": "openai",
                  "base_url": "https://api.openai.com/v1",
                  "icon": "icons/openai.svg",
                  "preset": true
                }
              ],
              "models": []
            }
            """.trimIndent()
        )
        val resolver = ModelMetadataResolver { snapshot }
        val existing = ProviderSetting.OpenAI(
            id = kotlin.uuid.Uuid.parse("11111111-1111-4111-8111-111111111111"),
            name = "My Router",
            baseUrl = "https://openrouter.ai/api/v1/",
            customIconUri = null,
        )

        val merged = mergeCatalogIntoSettings(
            settings = Settings(providers = listOf(existing)),
            snapshot = snapshot,
            resolver = resolver,
        )

        assertEquals(1, merged.providers.size)
        assertEquals(
            "https://raw.githubusercontent.com/Cocolalilal/LastChat/main/catalog/icons/openrouter-new.svg",
            merged.providers.single().customIconUri,
        )
    }

    private fun resolverFor(rawJson: String): ModelMetadataResolver {
        val snapshot = snapshotFor(rawJson)
        return ModelMetadataResolver(snapshotProvider = { snapshot })
    }

    private fun snapshotFor(rawJson: String): ModelCatalogSnapshot {
        return ModelCatalogParser.parse(rawJson)
    }
}
