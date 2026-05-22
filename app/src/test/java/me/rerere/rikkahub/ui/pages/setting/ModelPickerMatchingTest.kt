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
}
