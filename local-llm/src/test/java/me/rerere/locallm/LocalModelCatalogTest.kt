package me.rerere.locallm

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class LocalModelCatalogTest {
    private val json = Json { ignoreUnknownKeys = true }

    private fun bundled(): LocalModelCatalog = json.decodeFromString(
        File("src/main/assets/litert_catalog.json").readText()
    )

    @Test
    fun `new embedder is installable as a self-contained embedding bundle`() {
        val catalog = bundled()
        val model = catalog.models.single { it.id == "EmbeddingGemma-2-Text-270M" }

        assertEquals(LocalModelKind.EMBEDDING, model.kind)
        assertTrue(model.modelFile.endsWith(".litertlm"))
        assertNull(model.tokenizerDownloadUrl)
        assertFalse(model.requiresLicense)
        assertEquals(768, model.embeddingDimension)
        assertEquals(8192, model.defaultConfig.maxContextLength)
        assertTrue(model.downloadUrl.contains("/resolve/${model.commitHash}/"))
        assertEquals(40, model.commitHash.length)
        assertEquals(catalog.models.size, catalog.models.distinctBy { it.id }.size)
    }

    @Test
    fun `old cached catalog cannot hide the new embedder offline`() {
        val bundled = bundled()
        val oldModels = bundled.models.filterNot { it.id == "EmbeddingGemma-2-Text-270M" }
        val cached = bundled.copy(models = oldModels)

        assertEquals(bundled.models.map { it.id }, mergeCachedLocalCatalog(bundled, cached).models.map { it.id })
    }

    @Test
    fun `cached updates remain available alongside new bundled models`() {
        val bundled = bundled()
        val original = bundled.models.single { it.id == "EmbeddingGemma-300M" }
        val updated = original.copy(commitHash = "updated", updateInfo = "New revision")
        val result = mergeCachedLocalCatalog(bundled, bundled.copy(models = listOf(updated)))

        assertEquals(updated, result.models.single { it.id == original.id })
        assertTrue(result.models.any { it.id == "EmbeddingGemma-2-Text-270M" })
    }

    @Test
    fun `cache for a different model file cannot replace bundled metadata`() {
        val bundled = bundled()
        val original = bundled.models.first()
        val stale = original.copy(modelFile = "stale.tflite")

        assertEquals(bundled, mergeCachedLocalCatalog(bundled, bundled.copy(models = listOf(stale))))
    }
}
