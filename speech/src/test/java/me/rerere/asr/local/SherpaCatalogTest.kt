package me.rerere.asr.local

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SherpaCatalogTest {
    private val catalog: SherpaModelCatalog by lazy {
        val file = File("src/main/assets/sherpa_stt_catalog.json")
        Json { ignoreUnknownKeys = true }.decodeFromString(file.readText())
    }

    @Test
    fun `catalog ids and URLs are unique`() {
        assertTrue(catalog.models.isNotEmpty())
        assertEquals(catalog.models.size, catalog.models.map { it.id }.distinct().size)
        assertEquals(catalog.models.size, catalog.models.map { it.archiveUrl }.distinct().size)
        assertTrue(catalog.models.all { it.archiveUrl.startsWith("https://github.com/k2-fsa/sherpa-onnx/") })
        assertTrue(catalog.models.all { it.archiveSizeBytes > 0 })
    }

    @Test
    fun `each family declares the runtime files it needs`() {
        catalog.models.forEach { model ->
            assertTrue(model.files.containsKey(SherpaFileRole.TOKENS))
            when (model.family) {
                SherpaModelFamily.WHISPER,
                SherpaModelFamily.MOONSHINE -> {
                    assertTrue(model.files.containsKey(SherpaFileRole.ENCODER))
                    assertTrue(model.files.containsKey(SherpaFileRole.DECODER))
                }

                SherpaModelFamily.SENSE_VOICE ->
                    assertTrue(model.files.containsKey(SherpaFileRole.MODEL))

                SherpaModelFamily.ONLINE_TRANSDUCER -> {
                    assertTrue(model.streaming)
                    assertTrue(model.files.containsKey(SherpaFileRole.ENCODER))
                    assertTrue(model.files.containsKey(SherpaFileRole.DECODER))
                    assertTrue(model.files.containsKey(SherpaFileRole.JOINER))
                }
            }
        }
    }
}
