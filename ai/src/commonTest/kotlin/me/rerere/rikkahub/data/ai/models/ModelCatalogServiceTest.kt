package me.rerere.rikkahub.data.ai.models

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import me.rerere.ai.provider.ProviderSetting
import me.rerere.common.platform.PlatformFileStore
import me.rerere.common.platform.PlatformHttpClient
import me.rerere.common.platform.PlatformHttpRequest
import me.rerere.common.platform.PlatformHttpResponse
import me.rerere.common.platform.PlatformServerEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class ModelCatalogServiceTest {
    @Test
    fun warmUpUsesBundledCatalogAndRefreshPersistsDownload() = runBlocking {
        val files = MemoryCatalogFileStore()
        val client = MapHttpClient(
            mapOf(
                MODEL_CATALOG_URL to PlatformHttpResponse(
                    statusCode = 200,
                    body = DOWNLOADED_CATALOG.encodeToByteArray(),
                ),
            ),
        )
        val service = ModelCatalogService(
            httpClient = client,
            fileStore = files,
            bundledCatalogReader = { BUNDLED_CATALOG },
        )

        val warmed = service.warmUp().let { service.status.value }
        assertEquals(ModelCatalogSource.BUNDLED, warmed.source)
        assertEquals(1, warmed.providerCount)

        val refreshed = service.refreshCatalog()
        assertEquals(ModelCatalogSource.DOWNLOADED, refreshed.source)
        assertEquals(1, refreshed.providerCount)
        assertTrue(files.exists(MODEL_CATALOG_FILE_PATH))
        assertEquals(DOWNLOADED_CATALOG, files.readBytes(MODEL_CATALOG_FILE_PATH)?.decodeToString())
    }

    @Test
    fun mergeAddsMissingCatalogProvidersWhenRequested() {
        val snapshot = ModelCatalogParser.parse(BUNDLED_CATALOG)
        val resolver = ModelMetadataResolver { snapshot }
        val merged = mergeCatalogIntoProviders(
            providers = emptyList(),
            snapshot = snapshot,
            resolver = resolver,
            includeMissingCatalogProviders = true,
        )
        assertEquals(1, merged.size)
        val openai = merged.single() as ProviderSetting.OpenAI
        assertEquals(Uuid.parse(CATALOG_PROVIDER_ID), openai.id)
        assertEquals("https://api.openai.com/v1", openai.baseUrl)
        assertTrue(openai.customIconUri.orEmpty().contains("icons/openai.svg"))
    }

    @Test
    fun mergeAppliesCatalogIconToMatchingExistingProvider() {
        val snapshot = ModelCatalogParser.parse(BUNDLED_CATALOG)
        val resolver = ModelMetadataResolver { snapshot }
        val existing = ProviderSetting.OpenAI(
            id = Uuid.parse(CATALOG_PROVIDER_ID),
            name = "OpenAI",
            baseUrl = "https://api.openai.com/v1",
        )
        val merged = mergeCatalogIntoProviders(
            providers = listOf(existing),
            snapshot = snapshot,
            resolver = resolver,
        )
        assertEquals(1, merged.size)
        assertTrue((merged.single() as ProviderSetting.OpenAI).customIconUri.orEmpty().contains("icons/openai.svg"))
    }
}

private const val CATALOG_PROVIDER_ID = "11111111-1111-1111-1111-111111111111"

private const val BUNDLED_CATALOG = """
{
  "schema_version": 2,
  "updated_at": "2026-01-01",
  "providers": [{
    "id": "$CATALOG_PROVIDER_ID",
    "name": "OpenAI",
    "type": "openai",
    "base_url": "https://api.openai.com/v1",
    "icon": "icons/openai.svg",
    "built_in": true,
    "preset": true
  }],
  "model_families": [],
  "global_rules": [],
  "model_overrides": []
}
"""

private const val DOWNLOADED_CATALOG = """
{
  "schema_version": 2,
  "updated_at": "2026-09-01",
  "providers": [{
    "id": "$CATALOG_PROVIDER_ID",
    "name": "OpenAI",
    "type": "openai",
    "base_url": "https://api.openai.com/v1",
    "icon": "icons/openai.svg",
    "built_in": true,
    "preset": true
  }],
  "model_families": [],
  "global_rules": [],
  "model_overrides": []
}
"""

private class MapHttpClient(
    private val responses: Map<String, PlatformHttpResponse>,
) : PlatformHttpClient {
    override suspend fun execute(request: PlatformHttpRequest): PlatformHttpResponse {
        return responses[request.url] ?: PlatformHttpResponse(statusCode = 404)
    }

    override fun streamEvents(request: PlatformHttpRequest): Flow<PlatformServerEvent> = emptyFlow()
}

private class MemoryCatalogFileStore : PlatformFileStore {
    private val files = mutableMapOf<String, ByteArray>()
    private val modified = mutableMapOf<String, Long>()

    override suspend fun readBytes(path: String): ByteArray? = files[path.trimStart('/')]

    override suspend fun writeBytes(path: String, bytes: ByteArray) {
        val key = path.trimStart('/')
        files[key] = bytes
        modified[key] = (modified[key] ?: 0L) + 1L
    }

    override suspend fun delete(path: String): Boolean {
        val key = path.trimStart('/')
        modified.remove(key)
        return files.remove(key) != null
    }

    override suspend fun exists(path: String): Boolean = files.containsKey(path.trimStart('/'))

    override suspend fun lastModified(path: String): Long? = modified[path.trimStart('/')]
}
