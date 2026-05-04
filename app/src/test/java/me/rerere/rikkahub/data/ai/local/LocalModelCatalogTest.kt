package me.rerere.rikkahub.data.ai.local

import me.rerere.ai.provider.Modality
import me.rerere.ai.provider.ModelAbility
import me.rerere.ai.provider.ModelType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalModelCatalogTest {
    @Test
    fun `catalog keeps gated tiny gemma import-only and text-only`() {
        val entry = LocalModelCatalog.getById("gemma-3-270m-it-litert")
            ?: error("Missing Gemma 3 270M entry")

        assertEquals(ModelType.CHAT, entry.type)
        assertEquals(LocalModelDownloadAccess.AUTH_REQUIRED, entry.downloadAccess)
        assertEquals(listOf(Modality.TEXT), entry.inputModalities)
    }

    @Test
    fun `catalog keeps gemma 4 multimodal entries capability aware`() {
        val entry = LocalModelCatalog.getById("gemma-4-e2b-it-litert")
            ?: error("Missing Gemma 4 E2B entry")

        assertEquals(ModelType.CHAT, entry.type)
        assertTrue(Modality.IMAGE in entry.inputModalities)
        assertTrue("audio" in entry.featureTags)
        assertTrue(ModelAbility.REASONING in entry.abilities)
    }

    @Test
    fun `catalog includes embedding-specific local models`() {
        val entry = LocalModelCatalog.getById("embeddinggemma-300m-litert")
            ?: error("Missing embedding entry")

        assertEquals(ModelType.EMBEDDING, entry.type)
        assertEquals(listOf(Modality.TEXT), entry.inputModalities)
    }

    @Test
    fun `import inference reuses known catalog metadata when file names match`() {
        val entry = inferImportedCatalogEntry(
            fileName = "gemma-3n-E2B-it.litertlm",
            fileSizeBytes = 1234L,
        )

        assertEquals(LocalModelProvenance.IMPORTED, entry.provenance)
        assertTrue(Modality.IMAGE in entry.inputModalities)
        assertTrue(ModelAbility.REASONING in entry.abilities)
    }

    @Test
    fun `import inference falls back to embedding heuristics for unknown files`() {
        val entry = inferImportedCatalogEntry(
            fileName = "my-embedding-model.litertlm",
            fileSizeBytes = 4096L,
        )

        assertEquals(ModelType.EMBEDDING, entry.type)
        assertTrue(entry.displayName.contains("Embedding", ignoreCase = true))
    }
}
