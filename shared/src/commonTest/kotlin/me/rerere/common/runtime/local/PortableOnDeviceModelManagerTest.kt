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
    "revision": "1"
  }]
}
"""

private class MemoryDownloadFileStore : PlatformFileStore {
    private val files = mutableMapOf<String, ByteArray>()

    override suspend fun readBytes(path: String): ByteArray? = files[path.trimStart('/')]

    override suspend fun writeBytes(path: String, bytes: ByteArray) {
        files[path.trimStart('/')] = bytes
    }

    override suspend fun delete(path: String): Boolean = files.remove(path.trimStart('/')) != null

    override suspend fun exists(path: String): Boolean = files.containsKey(path.trimStart('/'))

    override suspend fun lastModified(path: String): Long? = if (exists(path)) 1L else null
}
