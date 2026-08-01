package me.rerere.rikkahub.data.ai.models

import me.rerere.ai.provider.Model
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ModelContextMetadataTest {
    @Test
    fun catalogContextWindow_isNotUsedAsDeploymentLimit() {
        val snapshot = ModelCatalogParser.parse(
            """
            {
              "schema_version": 1,
              "model_families": [{
                "id": "test-family",
                "match_patterns": ["test-model"],
                "context_window": 65536,
                "max_images_in_context": 6
              }]
            }
            """.trimIndent()
        )
        val resolved = ModelMetadataResolver { snapshot }.applyToModel(Model(modelId = "test-model"))

        assertNull(resolved.contextWindowTokens)
        assertEquals(6, resolved.maxImagesInContext)
    }
}
