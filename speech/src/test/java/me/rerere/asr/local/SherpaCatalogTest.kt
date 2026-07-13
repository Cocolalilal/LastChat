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
                    assertTrue(model.onlineModelType.isNotBlank())
                    assertTrue(model.files.containsKey(SherpaFileRole.ENCODER))
                    assertTrue(model.files.containsKey(SherpaFileRole.DECODER))
                    assertTrue(model.files.containsKey(SherpaFileRole.JOINER))
                }
            }
        }
    }

    @Test
    fun `streaming zipformer models use their published file names`() {
        val english = catalog.models.first { it.id == "sherpa-streaming-zipformer-en-2023-06-26" }
        assertEquals("zipformer2", english.onlineModelType)
        assertEquals("encoder-epoch-99-avg-1-chunk-16-left-128.int8.onnx", english.files[SherpaFileRole.ENCODER])
        assertEquals("decoder-epoch-99-avg-1-chunk-16-left-128.onnx", english.files[SherpaFileRole.DECODER])
        assertEquals("joiner-epoch-99-avg-1-chunk-16-left-128.onnx", english.files[SherpaFileRole.JOINER])

        val compact = catalog.models.first { it.id == "sherpa-streaming-zipformer-en-20m-2023-02-17" }
        assertEquals("zipformer", compact.onlineModelType)
        assertEquals("decoder-epoch-99-avg-1.onnx", compact.files[SherpaFileRole.DECODER])
    }

    @Test
    fun `archive download avoids invalid range retries`() {
        assertEquals(ArchiveDownloadPlan.REUSE_COMPLETE, archiveDownloadPlan(existingBytes = 100, expectedBytes = 100))
        assertEquals(ArchiveDownloadPlan.RESUME, archiveDownloadPlan(existingBytes = 37, expectedBytes = 100))
        assertEquals(ArchiveDownloadPlan.RESTART, archiveDownloadPlan(existingBytes = 101, expectedBytes = 100))
        assertEquals(ArchiveDownloadPlan.RESTART, archiveDownloadPlan(existingBytes = 0, expectedBytes = 100))
    }
}
