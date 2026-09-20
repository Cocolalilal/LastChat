package me.rerere.common.runtime.local

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import me.rerere.common.platform.PlatformFileStore
import me.rerere.common.platform.PlatformHttpClient
import me.rerere.common.platform.PlatformHttpRequest
import me.rerere.common.platform.PlatformHttpResponse
import me.rerere.common.platform.PlatformServerEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PortableOnDeviceModelManagerTest {
    @Test
    fun downloadsPersistAndDeleteThroughSharedIndex() = runBlocking {
        val files = MemoryDownloadFileStore()
        val payload = "litert-bytes".encodeToByteArray()
        val client = object : PlatformHttpClient {
            override suspend fun execute(request: PlatformHttpRequest): PlatformHttpResponse {
                return PlatformHttpResponse(statusCode = 200, body = payload)
            }

            override fun streamEvents(request: PlatformHttpRequest): Flow<PlatformServerEvent> = emptyFlow()
        }
        val manager = PortableOnDeviceModelManager(
            httpClient = client,
            fileStore = files,
            bundledLlmCatalog = { LLM_CATALOG },
            bundledSttCatalog = { STT_CATALOG },
            scope = this,
        )
        manager.warmUp()
        assertEquals(1, manager.llmCatalog.value.models.size)
        assertEquals(1, manager.sttCatalog.value.models.size)

        manager.downloadLlm(manager.llmCatalog.value.models.single())
        withTimeout(5_000) {
            manager.installedLlm.first { it.size == 1 }
        }
        assertEquals(1, manager.installedLlm.value.size)
        val installed = manager.installedLlm.value.single()
        assertEquals("gemma-test", installed.id)
        assertEquals(payload.size.toLong(), installed.sizeInBytes)
        assertTrue(files.exists(installed.filePath))

        manager.deleteLlm(installed.id)
        assertTrue(manager.installedLlm.value.isEmpty())
        assertTrue(!files.exists(installed.filePath))
    }

    @Test
    fun sttDownloadExtractsRequiredTarBz2Layout() = runBlocking {
        val files = MemoryDownloadFileStore()
        val archive = STT_ARCHIVE_HEX.hexToByteArray()
        val client = object : PlatformHttpClient {
            override suspend fun execute(request: PlatformHttpRequest): PlatformHttpResponse {
                return PlatformHttpResponse(statusCode = 200, body = archive)
            }

            override fun streamEvents(request: PlatformHttpRequest): Flow<PlatformServerEvent> = emptyFlow()
        }
        val manager = PortableOnDeviceModelManager(
            httpClient = client,
            fileStore = files,
            bundledLlmCatalog = { LLM_CATALOG },
            bundledSttCatalog = { STT_CATALOG },
            scope = this,
        )
        manager.warmUp()
        manager.downloadStt(manager.sttCatalog.value.models.single())
        withTimeout(5_000) {
            manager.installedStt.first { it.size == 1 }
        }
        val installed = manager.installedStt.value.single()
        assertEquals("moonshine-test", installed.id)
        assertEquals("on_device_models/stt/moonshine-test", installed.directoryPath)
        assertEquals("onnx-bytes", files.readBytes(installed.files.getValue("encoder"))?.decodeToString())
        assertEquals("hello-tokens", files.readBytes(installed.files.getValue("tokens"))?.decodeToString())
        assertTrue(!files.exists("on_device_models/stt/moonshine-test.tar.bz2"))

        manager.deleteStt(installed.id)
        assertTrue(manager.installedStt.value.isEmpty())
        assertTrue(files.listFiles(installed.directoryPath).isEmpty())
    }
}

private const val LLM_CATALOG = """
{
  "schema_version": 1,
  "allowlist_version": "test",
  "models": [{
    "id": "gemma-test",
    "name": "Gemma Test",
    "kind": "llm",
    "hfRepo": "org/gemma",
    "modelFile": "model.bin",
    "commitHash": "abc",
    "sizeInBytes": 12,
    "minDeviceMemoryInGb": 4
  }]
}
"""

private const val STT_CATALOG = """
{
  "schemaVersion": 1,
  "models": [{
    "id": "moonshine-test",
    "name": "Moonshine Test",
    "family": "moonshine",
    "archiveUrl": "https://example.test/stt.tar.bz2",
    "archiveSizeBytes": 8,
    "revision": "1",
    "files": {"encoder": "encoder.onnx", "tokens": "tokens.txt"}
  }]
}
"""

private const val STT_ARCHIVE_HEX =
    "425a6839314159265359442fd3990000d1ff80cb8010004003fd80000103207e6fdf6028283000b882554f5369a1ea434f48c9ea64c99069e9a8f50c69a0d0032680c8d343134605514d46483d4d347a04c65320f4134efd39637e732c9318a9508facd10885da6cbb1285a2635682b33110909867fdefaede7e7a299359e27ac90f224437df861942eaea2faa265f192670a69a55a8dea58576f816246e38e38387bbde9e1e48915abe8f6218c0d0c9bf16b609b46933615093328a2008b52a3f81f5045d4ee8820870e7706c0fe2ee48a70a120885fa7320"

private class MemoryDownloadFileStore : PlatformFileStore {
    private val files = mutableMapOf<String, ByteArray>()

    override suspend fun readBytes(path: String): ByteArray? = files[path.trimStart('/')]

    override suspend fun writeBytes(path: String, bytes: ByteArray) {
        files[path.trimStart('/')] = bytes
    }

    override suspend fun delete(path: String): Boolean = files.remove(path.trimStart('/')) != null

    override suspend fun exists(path: String): Boolean = files.containsKey(path.trimStart('/'))

    override suspend fun lastModified(path: String): Long? = if (exists(path)) 1L else null

    override suspend fun listFiles(path: String): List<String> {
        val prefix = path.replace('\\', '/').trimStart('/').trimEnd('/')
        return files.keys.filter { key ->
            key == prefix || key.startsWith("$prefix/")
        }
    }
}
