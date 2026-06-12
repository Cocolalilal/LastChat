package me.rerere.rikkahub.ui.pages.setting

import me.rerere.ai.provider.Model
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelPickerMatchingTest {
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
}
