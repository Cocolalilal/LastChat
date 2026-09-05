package me.rerere.lastchat.ios.backup

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import me.rerere.lastchat.ios.IosLocalToolOption
import me.rerere.lastchat.ios.IosMemoryMode
import me.rerere.lastchat.ios.IosProviderType
import me.rerere.lastchat.ios.IosSearchProviderType
import me.rerere.lastchat.ios.IosTtsProviderType

class IosBackupImportTest {
    @Test
    fun zipReaderExtractsStoreAndDeflateEntries() {
        val settings = """{"themeId":"moonlight"}""".encodeToByteArray()
        val manifestPlain = DEFLATE_PLAINTEXT.encodeToByteArray()
        val archive = ZipWriter().apply {
            add("settings.json", method = 0, data = settings)
            add("backup_manifest.json", method = 8, data = DEFLATE_FIXTURE_HEX.hexToByteArray(), crc = Crc32.of(manifestPlain), storedSize = DEFLATE_FIXTURE_HEX.hexToByteArray().size.toLong(), uncompressedSize = manifestPlain.size.toLong())
        }.build()

        val entries = assertNotNull(IosBackupZip.read(archive, wanted = setOf("settings.json", "backup_manifest.json")))
        assertEquals(2, entries.size)
        val settingsEntry = entries.first { it.name == "settings.json" }
        assertEquals(0, settingsEntry.method)
        assertEquals(settings.decodeToString(), assertNotNull(settingsEntry.data).decodeToString())
        val manifestEntry = entries.first { it.name == "backup_manifest.json" }
        assertEquals(8, manifestEntry.method)
        assertEquals(DEFLATE_PLAINTEXT, assertNotNull(manifestEntry.data).decodeToString())
        assertEquals(Crc32.of(manifestPlain), manifestEntry.crc32)
    }

    @Test
    fun zipReaderFlagsCorruptedEntriesWithoutData() {
        val settings = """{"themeId":"moonlight"}""".encodeToByteArray()
        val archive = ZipWriter().apply {
            add("settings.json", method = 0, data = settings)
        }.build()
        val corrupted = archive.copyOf().also { bytes ->
            val nameLength = "settings.json".encodeToByteArray().size
            val dataIndex = 30 + nameLength
            bytes[dataIndex] = (bytes[dataIndex] + 1).toByte()
        }

        val entries = assertNotNull(IosBackupZip.read(corrupted, wanted = setOf("settings.json")))
        assertEquals(1, entries.size)
        assertNull(entries.single().data)
    }

    @Test
    fun zipReaderRejectsNonZipBytes() {
        assertNull(IosBackupZip.read("definitely not a zip".encodeToByteArray(), wanted = setOf("settings.json")))
    }

    @Test
    fun importPlanMapsProvidersAssistantsSearchAndTts() {
        val settings = AndroidFixtures.settingsJson()
        val plan = IosBackupImporter.buildImportPlan(settings)

        assertEquals(IosProviderType.OPENAI, plan.selectedProviderType)
        assertEquals(2, plan.providerCredentials.size)

        val openai = plan.providerCredentials.first { it.type == IosProviderType.OPENAI }
        assertEquals("https://api.openai.com/v1", openai.baseUrl)
        assertEquals("gpt-4.1-mini", openai.modelId)
        assertEquals("sk-test", openai.apiKey)

        val google = plan.providerCredentials.first { it.type == IosProviderType.GOOGLE }
        assertEquals("g-key", google.apiKey)

        assertContains(plan.skipped.first { "additional openai" in it }, "1 additional openai provider")
        assertContains(plan.skipped.first { "Vertex AI" in it }, "Vertex")

        assertEquals(2, plan.assistants.size)
        val nova = plan.assistants.first { it.name == "Nova" }
        assertEquals("33333333-3333-3333-3333-333333333333", nova.id)
        assertEquals("You are Nova", nova.systemPrompt)
        assertEquals(IosMemoryMode.SEARCHABLE, nova.memoryMode)
        assertEquals(IosProviderType.OPENAI, nova.embeddingProviderType)
        assertEquals("text-embedding-3-small", nova.embeddingModelId)
        assertEquals(0.6f, nova.ragSimilarityThreshold)
        assertEquals(7, nova.ragLimit)
        assertEquals(setOf(IosLocalToolOption.JAVASCRIPT, IosLocalToolOption.TTS), nova.localTools)
        val plain = plan.assistants.first { it.name == "Plain" }
        assertEquals(IosMemoryMode.OFF, plain.memoryMode)
        assertContains(plan.skipped.single { "Python sandbox" in it }, "Nova")

        assertEquals("33333333-3333-3333-3333-333333333333", plan.selectedAssistantId)

        assertNotNull(plan.search)
        assertEquals(true, plan.search.enabled)
        assertEquals(IosSearchProviderType.TAVILY, plan.search.provider)
        assertEquals(8, plan.search.resultSize)
        assertEquals("tvly-123", plan.searchApiKeys[IosSearchProviderType.TAVILY])

        assertNotNull(plan.tts)
        assertEquals(IosTtsProviderType.ELEVENLABS, plan.tts.type)
        assertEquals("eleven_multilingual_v2", plan.tts.model)
        assertEquals("Rachel", plan.tts.voice)
        assertEquals(true, plan.tts.enabled)
        assertEquals("el-123", plan.ttsApiKeys[IosTtsProviderType.ELEVENLABS])

        assertEquals("moonlight", assertNotNull(plan.appearance).themeId)
        assertEquals(1.25f, plan.appearance.fontSizeRatio)

        assertContains(plan.applied.single { "Chat model" in it }, "gpt-4.1-mini")
    }

    @Test
    fun manifestParsingReadsFormatVersionTwo() {
        val manifest = IosBackupImporter.parseManifest(
            """{"formatVersion":2,"includesDatabase":true,"includesFiles":true,
               "managedFileDirs":["upload","images"],"sharedPrefsStores":["rikkahub.preferences"]}""",
        )
        assertNotNull(manifest)
        assertEquals(2, manifest.formatVersion)
        assertEquals(true, manifest.includesDatabase)
        assertEquals(listOf("upload", "images"), manifest.managedFileDirs)
    }
}

private object AndroidFixtures {
    val OPENAI_PROVIDER_ID = "22222222-2222-2222-2222-222222222222"
    val OPENROUTER_PROVIDER_ID = "22222222-2222-2222-2222-222222222223"
    val GOOGLE_PROVIDER_ID = "22222222-2222-2222-2222-222222222224"
    val VERTEX_PROVIDER_ID = "22222222-2222-2222-2222-222222222225"
    val CHAT_MODEL_ID = "11111111-2222-3333-4444-555555555555"
    val EMBEDDING_MODEL_ID = "66666666-7777-8888-9999-000000000000"
    val ASSISTANT_ID = "33333333-3333-3333-3333-333333333333"
    val TTS_PROVIDER_ID = "44444444-4444-4444-4444-444444444444"

    fun settingsJson(): JsonObject = Json.parseToJsonElement(
        """
        {
          "setupCompleted": true,
          "dynamicColor": true,
          "themeId": "moonlight",
          "assistantId": "$ASSISTANT_ID",
          "chatModelId": "$CHAT_MODEL_ID",
          "providers": [
            {
              "type": "openai", "id": "$OPENAI_PROVIDER_ID", "enabled": true, "name": "OpenAI",
              "models": [
                {"modelId": "gpt-4.1-mini", "id": "$CHAT_MODEL_ID"},
                {"modelId": "text-embedding-3-small", "id": "$EMBEDDING_MODEL_ID", "type": "EMBEDDING"}
              ],
              "proxy": {"type": "none"},
              "apiKey": "sk-test",
              "baseUrl": "https://api.openai.com/v1"
            },
            {
              "type": "openai", "id": "$OPENROUTER_PROVIDER_ID", "enabled": true, "name": "OpenRouter",
              "models": [], "proxy": {"type": "none"},
              "apiKey": "sk-or", "baseUrl": "https://openrouter.ai/api/v1"
            },
            {
              "type": "google", "id": "$GOOGLE_PROVIDER_ID", "enabled": true, "name": "Google",
              "models": [{"modelId": "gemini-2.5-flash", "id": "77777777-7777-7777-7777-777777777777"}],
              "proxy": {"type": "none"},
              "apiKey": "g-key", "baseUrl": "https://generativelanguage.googleapis.com/v1beta",
              "vertexAI": false
            },
            {
              "type": "google", "id": "$VERTEX_PROVIDER_ID", "enabled": true, "name": "Vertex",
              "models": [], "proxy": {"type": "none"},
              "apiKey": "", "baseUrl": "https://aiplatform.googleapis.com/v1",
              "vertexAI": true
            }
          ],
          "assistants": [
            {
              "id": "$ASSISTANT_ID", "name": "Nova", "systemPrompt": "You are Nova",
              "enableMemory": true, "enableMemorySearchTool": true,
              "embeddingModelId": "$EMBEDDING_MODEL_ID",
              "ragSimilarityThreshold": 0.6, "ragLimit": 7,
              "localTools": [{"type": "javascript_engine"}, {"type": "tts"}, {"type": "python_engine"}]
            },
            {
              "id": "33333333-3333-3333-3333-333333333334", "name": "Plain", "systemPrompt": "Hi",
              "enableMemory": false, "localTools": []
            }
          ],
          "enableWebSearch": true,
          "searchServices": [
            {"type": "bing_local", "id": "55555555-5555-5555-5555-555555555555"},
            {"type": "tavily", "id": "55555555-5555-5555-5555-555555555556", "apiKey": "tvly-123", "depth": "advanced"}
          ],
          "searchServiceSelected": 1,
          "searchCommonOptions": {"resultSize": 8},
          "ttsProviders": [
            {
              "type": "elevenlabs", "id": "$TTS_PROVIDER_ID", "name": "ElevenLabs TTS",
              "voices": [], "apiKey": "el-123",
              "voiceId": "Rachel", "modelId": "eleven_multilingual_v2"
            }
          ],
          "selectedTTSProviderId": "$TTS_PROVIDER_ID",
          "displaySetting": {"fontSizeRatio": 1.25}
        }
        """.trimIndent(),
    ).jsonObject
}

/**
 * Raw DEFLATE stream (window bits -15, like Java's ZipOutputStream) of [DEFLATE_PLAINTEXT],
 * generated with Python zlib for a fixed compression level.
 */
private const val DEFLATE_PLAINTEXT =
    """[{"type":"openai","apiKey":"sk-test-123","baseUrl":"https://api.openai.com/v1","models":[]}]"""
private const val DEFLATE_FIXTURE_HEX =
    "8bae562aa92c4855b252ca2f48cd4bcc54d2514a2cc8f44ead048a1467eb96a41697e81a1a19038593128b53438b7280e219252505c556fafa40857a105d7ac9f9b9fa65864055b9f929a939c54a56d1b1b5b100"

private class ZipWriter {
    private val localParts = mutableListOf<ByteArray>()
    private val centralParts = mutableListOf<ByteArray>()
    private var currentOffset = 0

    fun add(name: String, method: Int, data: ByteArray, crc: Long = Crc32.of(data), storedSize: Long = data.size.toLong(), uncompressedSize: Long = data.size.toLong()) {
        val nameBytes = name.encodeToByteArray()
        val local = listOf(
            u32(0x04034b50), u16(20), u16(0), u16(method), u16(0), u16(0),
            u32(crc), u32(storedSize), u32(uncompressedSize),
            u16(nameBytes.size), u16(0),
        ).reduce(ByteAccumulator) + nameBytes + data
        localParts += local
        centralParts += listOf(
            u32(0x02014b50), u16(20), u16(20), u16(0), u16(method), u16(0), u16(0),
            u32(crc), u32(storedSize), u32(uncompressedSize),
            u16(nameBytes.size), u16(0), u16(0), u16(0), u16(0), u32(0),
            u32(currentOffset.toLong()),
        ).reduce(ByteAccumulator) + nameBytes
        currentOffset += local.size
    }

    fun build(): ByteArray {
        val centralDirectory = centralParts.reduce(ByteAccumulator)
        val eocd = listOf(
            u32(0x06054b50), u16(0), u16(0), u16(centralParts.size), u16(centralParts.size),
            u32(centralDirectory.size.toLong()), u32(currentOffset.toLong()), u16(0),
        ).reduce(ByteAccumulator)
        return (localParts + centralDirectory + eocd).reduce(ByteAccumulator)
    }

    private fun u16(value: Int): ByteArray = byteArrayOf(
        (value and 0xFF).toByte(),
        ((value shr 8) and 0xFF).toByte(),
    )

    private fun u32(value: Long): ByteArray = byteArrayOf(
        (value and 0xFFL).toByte(),
        ((value shr 8) and 0xFFL).toByte(),
        ((value shr 16) and 0xFFL).toByte(),
        ((value shr 24) and 0xFFL).toByte(),
    )

    private companion object {
        val ByteAccumulator: (ByteArray, ByteArray) -> ByteArray = { a, b -> a + b }
    }
}
