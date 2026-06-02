package me.rerere.rikkahub.data.localmodel

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import me.rerere.ai.provider.LocalModelAccelerator
import me.rerere.ai.provider.LocalModelDownloadState
import me.rerere.ai.provider.Model
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.db.dao.LocalModelInstallDao
import me.rerere.rikkahub.utils.JsonInstant
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import kotlin.uuid.Uuid

class LocalModelRepository(
    private val context: Context,
    private val client: OkHttpClient,
    private val dao: LocalModelInstallDao,
    private val settingsStore: SettingsStore,
    private val workManager: WorkManager,
) {
    private val _catalog = MutableStateFlow(BUNDLED_LOCAL_MODEL_CATALOG)
    val catalog: StateFlow<List<LocalModelCatalogEntry>> = _catalog
    val installs = dao.observeAll()

    suspend fun refreshCatalog() = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(GALLERY_ALLOWLIST_URL)
            .build()
        val response = client.newCall(request).execute()
        response.use {
            if (!it.isSuccessful) return@withContext
            val body = it.body.string()
            val parsed = parseGalleryAllowlist(body)
            if (parsed.isNotEmpty()) {
                _catalog.value = mergeCatalog(parsed)
                syncCatalogMetadata(_catalog.value)
            }
        }
    }

    fun download(entry: LocalModelCatalogEntry, update: Boolean = false) {
        val request = OneTimeWorkRequestBuilder<LocalModelDownloadWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setInputData(
                workDataOf(
                    LocalModelDownloadWorker.KEY_ENTRY_JSON to JsonInstant.encodeToString(entry),
                    LocalModelDownloadWorker.KEY_UPDATE to update,
                )
            )
            .build()
        workManager.enqueueUniqueWork(
            "local_model_${entry.catalogId}",
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    suspend fun importModel(uri: Uri): Unit = withContext(Dispatchers.IO) {
        val fileName = queryDisplayName(uri).takeIf { it.endsWith(".litertlm", ignoreCase = true) }
            ?: "imported-${Uuid.random()}.litertlm"
        val targetDir = localModelDirectory().resolve("imported").apply { mkdirs() }
        val target = uniqueFile(targetDir, fileName)
        context.contentResolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        } ?: return@withContext

        val entity = BUNDLED_LOCAL_MODEL_CATALOG.firstOrNull { it.fileName.equals(fileName, ignoreCase = true) }
            ?.toInstallEntity(
                status = LocalModelDownloadState.DOWNLOADED,
                localPath = target.canonicalPath,
            )
            ?: me.rerere.rikkahub.data.db.entity.LocalModelInstallEntity(
                catalogId = "imported-${target.nameWithoutExtension}-${target.length()}",
                repoId = "imported",
                revision = "imported",
                modelId = target.nameWithoutExtension,
                displayName = target.nameWithoutExtension,
                fileName = target.name,
                localPath = target.canonicalPath,
                status = LocalModelDownloadState.DOWNLOADED.name,
                progressPercent = 100,
                bytesDownloaded = target.length(),
                bytesTotal = target.length(),
                sizeBytes = target.length(),
                minRamGb = 0,
                maxTokens = 1024,
                contextWindowTokens = 4096,
                acceleratorsCsv = "gpu,cpu",
                selectedAccelerator = "auto",
                imported = true,
                updatedAt = System.currentTimeMillis(),
            )
        dao.upsert(entity)
        syncInstalledModels()
    }

    suspend fun delete(model: Model): Unit = withContext(Dispatchers.IO) {
        val catalogId = model.localCatalogId ?: return@withContext
        dao.getByCatalogId(catalogId)?.localPath?.takeIf { it.isNotBlank() }?.let { path ->
            val target = runCatching { File(path).canonicalFile }.getOrNull()
            if (target != null && target.isFile && target.isInside(localModelDirectory())) {
                target.delete()
            }
        }
        dao.deleteByCatalogId(catalogId)
        syncInstalledModels()
    }

    suspend fun updateModelConfig(model: Model): Unit = withContext(Dispatchers.IO) {
        val catalogId = model.localCatalogId ?: return@withContext
        val install = dao.getByCatalogId(catalogId) ?: return@withContext
        val defaults = model.localSamplerDefaults
        dao.upsert(
            install.copy(
                topK = defaults?.topK ?: install.topK,
                topP = defaults?.topP ?: install.topP,
                temperature = defaults?.temperature ?: install.temperature,
                maxTokens = defaults?.maxTokens ?: install.maxTokens,
                contextWindowTokens = defaults?.maxContextLength ?: model.contextWindowTokens ?: install.contextWindowTokens,
                selectedAccelerator = (model.localAccelerator ?: LocalModelAccelerator.AUTO).name.lowercase(),
                updatedAt = System.currentTimeMillis(),
            )
        )
        syncInstalledModels()
    }

    suspend fun syncInstalledModels() {
        val installs = withContext(Dispatchers.IO) { dao.getAll() }
        settingsStore.update(LocalModelSettingsSync.apply(settingsStore.settingsFlow.value, installs))
    }

    private suspend fun syncCatalogMetadata(catalog: List<LocalModelCatalogEntry>) {
        val byId = catalog.associateBy { it.catalogId }
        dao.getAll().forEach { install ->
            val entry = byId[install.catalogId] ?: return@forEach
            dao.upsert(
                install.copy(
                    repoId = entry.repoId,
                    updateRevision = entry.updateRevision,
                    updateFileName = entry.updateFileName,
                    updateInfo = entry.updateInfo,
                    sizeBytes = entry.sizeBytes,
                    minRamGb = entry.minRamGb,
                    updatedAt = System.currentTimeMillis(),
                )
            )
        }
        syncInstalledModels()
    }

    private fun mergeCatalog(remote: List<LocalModelCatalogEntry>): List<LocalModelCatalogEntry> {
        val remoteById = remote.associateBy { it.catalogId }
        return BUNDLED_LOCAL_MODEL_CATALOG.map { remoteById[it.catalogId] ?: it }
    }

    private fun localModelDirectory(): File = context.filesDir.resolve("local_models").apply { mkdirs() }

    private fun queryDisplayName(uri: Uri): String {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && it.moveToFirst()) {
                return it.getString(index).orEmpty()
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/').orEmpty()
    }

    private fun uniqueFile(directory: File, name: String): File {
        val base = name.substringBeforeLast('.', name)
        val extension = name.substringAfterLast('.', "")
        var candidate = directory.resolve(name)
        var counter = 1
        while (candidate.exists()) {
            val suffix = if (extension.isBlank()) "-$counter" else "-$counter.$extension"
            candidate = directory.resolve(base + suffix)
            counter++
        }
        return candidate
    }
}

const val GALLERY_ALLOWLIST_URL = "https://raw.githubusercontent.com/google-ai-edge/gallery/main/model_allowlists/1_0_14.json"

internal fun File.isInside(root: File): Boolean {
    val canonicalRoot = runCatching { root.canonicalFile }.getOrNull() ?: return false
    val canonicalFile = runCatching { canonicalFile }.getOrNull() ?: return false
    val rootPath = canonicalRoot.path
    val filePath = canonicalFile.path
    return filePath == rootPath || filePath.startsWith(rootPath + File.separator)
}
