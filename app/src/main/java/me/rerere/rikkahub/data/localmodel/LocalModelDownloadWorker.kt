package me.rerere.rikkahub.data.localmodel

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.rerere.ai.provider.LocalModelDownloadState
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.db.dao.LocalModelInstallDao
import me.rerere.rikkahub.utils.JsonInstant
import okhttp3.OkHttpClient
import okhttp3.Request
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.File

class LocalModelDownloadWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params), KoinComponent {
    private val client: OkHttpClient by inject()
    private val dao: LocalModelInstallDao by inject()
    private val settingsStore: SettingsStore by inject()

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val entryJson = inputData.getString(KEY_ENTRY_JSON) ?: return@withContext Result.failure()
        val entry = runCatching { JsonInstant.decodeFromString<LocalModelCatalogEntry>(entryJson) }.getOrNull()
            ?: return@withContext Result.failure()
        val update = inputData.getBoolean(KEY_UPDATE, false)
        val targetRevision = if (update && entry.updateRevision.isNotBlank()) entry.updateRevision else entry.revision
        val targetFileName = if (update && entry.updateFileName.isNotBlank()) entry.updateFileName else entry.fileName
        val targetDir = applicationContext.filesDir.resolve("local_models").resolve(entry.catalogId).apply { mkdirs() }
        val targetFile = targetDir.resolve(targetFileName)
        val tempFile = targetDir.resolve("$targetFileName.part")
        val url = "https://huggingface.co/${entry.repoId}/resolve/$targetRevision/$targetFileName?download=true"
        val startedEntity = entry.toInstallEntity(
            status = if (update) LocalModelDownloadState.UPDATING else LocalModelDownloadState.DOWNLOADING,
            localPath = targetFile.canonicalPath,
            selectedAccelerator = entry.defaultAccelerator(),
        )
        dao.upsert(startedEntity)
        runCatching {
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    error("HTTP ${response.code}")
                }
                val body = response.body
                val total = body.contentLength().takeIf { it > 0 } ?: entry.sizeBytes
                body.byteStream().use { input ->
                    tempFile.outputStream().use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var downloaded = 0L
                        var lastProgress = -1
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            downloaded += read
                            val progress = ((downloaded * 100) / total.coerceAtLeast(1L)).toInt().coerceIn(0, 99)
                            if (progress != lastProgress && progress % 5 == 0) {
                                lastProgress = progress
                                dao.upsert(
                                    startedEntity.copy(
                                        progressPercent = progress,
                                        bytesDownloaded = downloaded,
                                        bytesTotal = total,
                                        updatedAt = System.currentTimeMillis(),
                                    )
                                )
                            }
                        }
                    }
                }
            }
            if (targetFile.exists()) {
                targetFile.delete()
            }
            tempFile.renameTo(targetFile)
            dao.upsert(
                startedEntity.copy(
                    revision = targetRevision,
                    fileName = targetFileName,
                    localPath = targetFile.canonicalPath,
                    status = LocalModelDownloadState.DOWNLOADED.name,
                    progressPercent = 100,
                    bytesDownloaded = targetFile.length(),
                    bytesTotal = targetFile.length().coerceAtLeast(entry.sizeBytes),
                    lastError = "",
                    updatedAt = System.currentTimeMillis(),
                )
            )
            syncProviderModels()
            Result.success()
        }.getOrElse { error ->
            tempFile.delete()
            dao.upsert(
                startedEntity.copy(
                    status = LocalModelDownloadState.FAILED.name,
                    lastError = error.message.orEmpty(),
                    updatedAt = System.currentTimeMillis(),
                )
            )
            syncProviderModels()
            Result.failure()
        }
    }

    private suspend fun syncProviderModels() {
        val installs = dao.getAll()
        settingsStore.update(LocalModelSettingsSync.apply(settingsStore.settingsFlow.value, installs))
    }

    companion object {
        const val KEY_ENTRY_JSON = "entry_json"
        const val KEY_UPDATE = "update"
    }
}
