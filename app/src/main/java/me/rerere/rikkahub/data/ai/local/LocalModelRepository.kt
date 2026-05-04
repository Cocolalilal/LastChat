package me.rerere.rikkahub.data.ai.local

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import me.rerere.ai.provider.Model
import me.rerere.rikkahub.data.datastore.SecureStore
import me.rerere.rikkahub.AppScope
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.ensureBuiltInProviders
import me.rerere.rikkahub.data.datastore.withLocalProviderModels
import me.rerere.rikkahub.data.db.dao.LocalModelInstallDao
import me.rerere.rikkahub.data.db.entity.LocalModelInstallEntity
import me.rerere.rikkahub.utils.JsonInstant
import okhttp3.OkHttpClient
import okhttp3.Request

private const val TAG = "LocalModelRepository"
private const val PROGRESS_UPDATE_INTERVAL_MS = 250L
private const val HUGGING_FACE_TOKEN_KEY = "local_model_huggingface_token"

data class LocalInstallProgressSnapshot(
    val catalogId: String,
    val displayName: String,
    val status: LocalModelStatus,
    val bytesDownloaded: Long,
    val bytesTotal: Long,
    val progressPercent: Int,
    val bytesPerSecond: Long,
    val etaSeconds: Long,
    val currentFile: String,
    val lastError: String,
)

class LocalModelRepository(
    private val context: Context,
    private val appScope: AppScope,
    private val installDao: LocalModelInstallDao,
    private val settingsStore: SettingsStore,
    private val secureStore: SecureStore,
    private val client: OkHttpClient,
    private val compatibilityEstimator: LocalCompatibilityEstimator,
) {
    private val installsState = MutableStateFlow<List<LocalModelInstallEntity>>(emptyList())

    init {
        appScope.launch(Dispatchers.IO) {
            seedCatalogEntriesIfMissing()
            installDao.observeAll().collectLatest { installs ->
                val reconciled = reconcileMissingFiles(installs)
                installsState.value = reconciled.sortedWith(
                    compareByDescending<LocalModelInstallEntity> { it.status == LocalModelStatus.READY.name }
                        .thenBy { it.displayName.lowercase() }
                )
                syncLocalProviderModels(reconciled)
            }
        }
    }

    fun observeCatalogStates(): Flow<List<LocalModelCatalogState>> {
        return installsState.asStateFlow().map { installs ->
            installs.mapNotNull { install ->
                val entry = install.toCatalogEntryOrNull() ?: return@mapNotNull null
                LocalModelCatalogState(
                    entry = entry,
                    install = install,
                    compatibility = compatibilityEstimator.estimate(entry),
                )
            }
        }
    }

    suspend fun getReadyModels(): List<Model> {
        return installsState.value
            .filter { it.status == LocalModelStatus.READY.name }
            .mapNotNull { install -> install.toCatalogEntryOrNull()?.toModel() }
            .sortedBy { it.displayName.lowercase() }
    }

    suspend fun getReadyInstallForModel(model: Model): LocalModelInstallEntity? {
        return installsState.value.firstOrNull {
            it.modelId == model.modelId && it.status == LocalModelStatus.READY.name
        }
    }

    suspend fun markQueued(catalogId: String) = withContext(Dispatchers.IO) {
        val existing = installDao.getByCatalogId(catalogId) ?: error("Unknown local model: $catalogId")
        val entry = existing.toCatalogEntryOrNull() ?: error("Missing local model metadata for $catalogId")
        installDao.upsert(
            existing.mergeCatalogEntry(entry).copy(
                status = LocalModelStatus.QUEUED.name,
                bytesDownloaded = 0L,
                bytesTotal = entry.estimatedDownloadBytes,
                progressPercent = 0,
                bytesPerSecond = 0L,
                etaSeconds = 0L,
                currentFile = "",
                lastError = "",
                updatedAt = System.currentTimeMillis(),
            )
        )
    }

    suspend fun queueHuggingFaceModel(entry: LocalModelCatalogEntry) = withContext(Dispatchers.IO) {
        val existing = installDao.getByCatalogId(entry.id)
        installDao.upsert(
            (existing?.mergeCatalogEntry(entry) ?: entry.toEntity(LocalModelStatus.QUEUED)).copy(
                status = LocalModelStatus.QUEUED.name,
                bytesDownloaded = 0L,
                bytesTotal = entry.estimatedDownloadBytes,
                progressPercent = 0,
                bytesPerSecond = 0L,
                etaSeconds = 0L,
                currentFile = "",
                lastError = "",
                updatedAt = System.currentTimeMillis(),
            )
        )
    }

    suspend fun prepareImport(uri: Uri): String = withContext(Dispatchers.IO) {
        val fileName = queryDisplayName(uri) ?: "imported-model"
        val format = detectLocalPackageFormat(fileName)
        check(format == LocalPackageFormat.LITERT_LM || format == LocalPackageFormat.GGUF) {
            "Only LiteRT .litertlm and llama.cpp .gguf packages are supported right now."
        }

        val fileSize = queryFileSize(uri).coerceAtLeast(0L)
        val entry = inferImportedCatalogEntry(fileName = fileName, fileSizeBytes = fileSize)
        val existing = installDao.getByCatalogId(entry.id)
        val compatibility = compatibilityEstimator.estimate(entry)
        val status = if (compatibility.canDownload) LocalModelStatus.QUEUED else LocalModelStatus.INCOMPATIBLE
        val entity = (existing?.mergeCatalogEntry(entry) ?: entry.toEntity(status = status)).copy(
            provenance = LocalModelProvenance.IMPORTED.name,
            downloadAccess = LocalModelDownloadAccess.IMPORT_ONLY.name,
            sourceUri = uri.toString(),
            currentFile = fileName,
            bytesDownloaded = 0L,
            bytesTotal = fileSize,
            progressPercent = 0,
            bytesPerSecond = 0L,
            etaSeconds = 0L,
            lastError = if (compatibility.canDownload) "" else compatibility.reasons.joinToString(" "),
            updatedAt = System.currentTimeMillis(),
        )
        installDao.upsert(entity)
        entry.id
    }

    suspend fun downloadModel(
        catalogId: String,
        onProgress: suspend (LocalInstallProgressSnapshot) -> Unit = {},
    ) = withContext(Dispatchers.IO) {
        val existing = installDao.getByCatalogId(catalogId) ?: error("Unknown local model: $catalogId")
        var entry = existing.toCatalogEntryOrNull() ?: error("Missing local model metadata for $catalogId")
        check(entry.downloadAccess != LocalModelDownloadAccess.IMPORT_ONLY) {
            "This model has to be imported manually."
        }
        val authToken = getHuggingFaceToken()
        check(entry.downloadAccess != LocalModelDownloadAccess.AUTH_REQUIRED || authToken.isNotBlank()) {
            "Add a Hugging Face token and accept the model terms first."
        }

        entry = resolveDownloadFiles(entry)
        val compatibility = compatibilityEstimator.estimate(entry)
        if (!compatibility.canDownload) {
            publish(
                existing.mergeCatalogEntry(entry).copy(
                    status = LocalModelStatus.INCOMPATIBLE.name,
                    lastError = compatibility.reasons.joinToString(" "),
                    currentFile = "",
                    bytesPerSecond = 0L,
                    etaSeconds = 0L,
                    updatedAt = System.currentTimeMillis(),
                ),
                onProgress,
            )
            error(compatibility.reasons.joinToString("\n"))
        }

        val installDir = modelDirectory(entry.id).apply { mkdirs() }
        try {
            publish(
                existing.mergeCatalogEntry(entry).copy(
                    status = LocalModelStatus.DOWNLOADING.name,
                    bytesDownloaded = 0L,
                    bytesTotal = entry.files.sumOf { it.sizeBytes },
                    progressPercent = 0,
                    bytesPerSecond = 0L,
                    etaSeconds = 0L,
                    currentFile = "",
                    lastError = "",
                    updatedAt = System.currentTimeMillis(),
                ),
                onProgress,
            )

            val downloadedFiles = mutableListOf<File>()
            val totalBytes = entry.files.sumOf { it.sizeBytes }
            var completedBytes = 0L
            entry.files.forEach { file ->
                val downloaded = downloadFile(
                    entry = entry,
                    file = file,
                    installDir = installDir,
                    totalBytes = totalBytes,
                    completedBytesBeforeFile = completedBytes,
                    onProgress = { bytesDownloaded, bytesPerSecond, etaSeconds, currentFile ->
                        val percent = if (totalBytes > 0) {
                            ((bytesDownloaded * 100L) / totalBytes).toInt().coerceIn(0, 100)
                        } else {
                            0
                        }
                        publish(
                            installDao.getByCatalogId(catalogId)?.mergeCatalogEntry(entry)?.copy(
                                status = LocalModelStatus.DOWNLOADING.name,
                                currentFile = currentFile,
                                bytesDownloaded = bytesDownloaded,
                                bytesTotal = totalBytes,
                                progressPercent = percent,
                                bytesPerSecond = bytesPerSecond,
                                etaSeconds = etaSeconds,
                                lastError = "",
                                updatedAt = System.currentTimeMillis(),
                            ) ?: existing,
                            onProgress,
                        )
                    },
                )
                downloadedFiles += downloaded
                completedBytes += downloaded.length()
            }

            publish(
                installDao.getByCatalogId(catalogId)?.mergeCatalogEntry(entry)?.copy(
                    status = LocalModelStatus.VERIFYING.name,
                    filePathsJson = JsonInstant.encodeToString(downloadedFiles.map { it.absolutePath }),
                    installedSizeBytes = downloadedFiles.sumOf { it.length() },
                    currentFile = "",
                    bytesDownloaded = completedBytes,
                    bytesTotal = totalBytes,
                    progressPercent = if (totalBytes > 0) 100 else 0,
                    bytesPerSecond = 0L,
                    etaSeconds = 0L,
                    updatedAt = System.currentTimeMillis(),
                ) ?: existing,
                onProgress,
            )

            entry.files.zip(downloadedFiles).forEach { (file, downloadedFile) ->
                if (!file.sha256.isNullOrBlank()) {
                    val actual = sha256(downloadedFile)
                    if (!actual.equals(file.sha256, ignoreCase = true)) {
                        throw IOException("Checksum verification failed for ${file.relativePath}")
                    }
                }
            }

            publish(
                installDao.getByCatalogId(catalogId)?.mergeCatalogEntry(entry)?.copy(
                    status = LocalModelStatus.INSTALLING.name,
                    currentFile = downloadedFiles.firstOrNull()?.name.orEmpty(),
                    bytesPerSecond = 0L,
                    etaSeconds = 0L,
                    updatedAt = System.currentTimeMillis(),
                ) ?: existing,
                onProgress,
            )

            publish(
                installDao.getByCatalogId(catalogId)?.mergeCatalogEntry(entry)?.copy(
                    status = LocalModelStatus.READY.name,
                    filePathsJson = JsonInstant.encodeToString(downloadedFiles.map { it.absolutePath }),
                    installedSizeBytes = downloadedFiles.sumOf { it.length() },
                    currentFile = "",
                    bytesDownloaded = totalBytes,
                    bytesTotal = totalBytes,
                    progressPercent = if (totalBytes > 0) 100 else 0,
                    bytesPerSecond = 0L,
                    etaSeconds = 0L,
                    lastError = "",
                    updatedAt = System.currentTimeMillis(),
                ) ?: existing,
                onProgress,
            )
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            Log.i(TAG, "Download cancelled for $catalogId")
            publish(
                installDao.getByCatalogId(catalogId)?.mergeCatalogEntry(entry)?.copy(
                    status = LocalModelStatus.CANCELED.name,
                    currentFile = "",
                    bytesPerSecond = 0L,
                    etaSeconds = 0L,
                    lastError = "Download canceled.",
                    updatedAt = System.currentTimeMillis(),
                ) ?: existing,
                onProgress,
            )
            throw cancelled
        } catch (throwable: Throwable) {
            Log.w(TAG, "Failed to download local model $catalogId", throwable)
            val installedFiles = listInstalledFiles(installDir)
            publish(
                installDao.getByCatalogId(catalogId)?.mergeCatalogEntry(entry)?.copy(
                    status = LocalModelStatus.FAILED.name,
                    filePathsJson = JsonInstant.encodeToString(installedFiles.map { it.absolutePath }),
                    installedSizeBytes = installedFiles.sumOf { it.length() },
                    currentFile = "",
                    bytesPerSecond = 0L,
                    etaSeconds = 0L,
                    lastError = throwable.message ?: "Download failed",
                    updatedAt = System.currentTimeMillis(),
                ) ?: existing,
                onProgress,
            )
            throw throwable
        }
    }

    suspend fun importModel(
        catalogId: String,
        uri: Uri,
        onProgress: suspend (LocalInstallProgressSnapshot) -> Unit = {},
    ) = withContext(Dispatchers.IO) {
        val existing = installDao.getByCatalogId(catalogId) ?: error("Unknown imported local model: $catalogId")
        val entry = existing.toCatalogEntryOrNull() ?: error("Missing imported local model metadata for $catalogId")
        val compatibility = compatibilityEstimator.estimate(entry)
        if (!compatibility.canDownload) {
            publish(
                existing.copy(
                    status = LocalModelStatus.INCOMPATIBLE.name,
                    lastError = compatibility.reasons.joinToString(" "),
                    updatedAt = System.currentTimeMillis(),
                ),
                onProgress,
            )
            error(compatibility.reasons.joinToString("\n"))
        }

        val fileName = queryDisplayName(uri) ?: entry.primaryFile?.relativePath ?: "${entry.id}.litertlm"
        val inputFileSize = queryFileSize(uri).coerceAtLeast(entry.estimatedInstalledBytes)
        val installDir = modelDirectory(catalogId).apply { mkdirs() }
        val targetFile = File(installDir, fileName)
        val tempFile = File(targetFile.absolutePath + ".part")

        try {
            publish(
                existing.mergeCatalogEntry(entry.copy(estimatedDownloadBytes = inputFileSize, estimatedInstalledBytes = inputFileSize)).copy(
                    status = LocalModelStatus.INSTALLING.name,
                    currentFile = fileName,
                    bytesDownloaded = 0L,
                    bytesTotal = inputFileSize,
                    progressPercent = 0,
                    bytesPerSecond = 0L,
                    etaSeconds = 0L,
                    lastError = "",
                    updatedAt = System.currentTimeMillis(),
                ),
                onProgress,
            )

            context.contentResolver.openInputStream(uri)?.use { input ->
                tempFile.outputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    val startedAt = System.currentTimeMillis()
                    var lastUpdateAt = startedAt
                    var written = 0L
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        written += read
                        val now = System.currentTimeMillis()
                        if (now - lastUpdateAt >= PROGRESS_UPDATE_INTERVAL_MS) {
                            val elapsed = (now - startedAt).coerceAtLeast(1L)
                            val bytesPerSecond = (written * 1000L) / elapsed
                            val etaSeconds = if (inputFileSize > 0 && bytesPerSecond > 0) {
                                ((inputFileSize - written).coerceAtLeast(0L) / bytesPerSecond)
                            } else {
                                0L
                            }
                            val progressPercent = if (inputFileSize > 0) {
                                ((written * 100L) / inputFileSize).toInt().coerceIn(0, 100)
                            } else {
                                0
                            }
                            publish(
                                installDao.getByCatalogId(catalogId)?.copy(
                                    bytesDownloaded = written,
                                    bytesTotal = inputFileSize,
                                    progressPercent = progressPercent,
                                    bytesPerSecond = bytesPerSecond,
                                    etaSeconds = etaSeconds,
                                    currentFile = fileName,
                                    updatedAt = now,
                                ) ?: existing,
                                onProgress,
                            )
                            lastUpdateAt = now
                        }
                    }
                }
            } ?: throw IOException("Unable to open imported file.")

            if (targetFile.exists()) targetFile.delete()
            if (!tempFile.renameTo(targetFile)) {
                throw IOException("Failed to finalize imported model.")
            }

            publish(
                installDao.getByCatalogId(catalogId)?.copy(
                    status = LocalModelStatus.VERIFYING.name,
                    currentFile = fileName,
                    bytesDownloaded = inputFileSize,
                    bytesTotal = inputFileSize,
                    progressPercent = 100,
                    bytesPerSecond = 0L,
                    etaSeconds = 0L,
                    updatedAt = System.currentTimeMillis(),
                ) ?: existing,
                onProgress,
            )

            publish(
                installDao.getByCatalogId(catalogId)?.copy(
                    status = LocalModelStatus.READY.name,
                    filePathsJson = JsonInstant.encodeToString(listOf(targetFile.absolutePath)),
                    checksum = sha256(targetFile),
                    installedSizeBytes = targetFile.length(),
                    currentFile = "",
                    bytesDownloaded = targetFile.length(),
                    bytesTotal = targetFile.length(),
                    progressPercent = 100,
                    bytesPerSecond = 0L,
                    etaSeconds = 0L,
                    lastError = "",
                    updatedAt = System.currentTimeMillis(),
                ) ?: existing,
                onProgress,
            )
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            publish(
                installDao.getByCatalogId(catalogId)?.copy(
                    status = LocalModelStatus.CANCELED.name,
                    currentFile = "",
                    bytesPerSecond = 0L,
                    etaSeconds = 0L,
                    lastError = "Import canceled.",
                    updatedAt = System.currentTimeMillis(),
                ) ?: existing,
                onProgress,
            )
            throw cancelled
        } catch (throwable: Throwable) {
            Log.w(TAG, "Failed to import local model $catalogId", throwable)
            val installedFiles = listInstalledFiles(installDir)
            publish(
                installDao.getByCatalogId(catalogId)?.copy(
                    status = LocalModelStatus.FAILED.name,
                    filePathsJson = JsonInstant.encodeToString(installedFiles.map { it.absolutePath }),
                    installedSizeBytes = installedFiles.sumOf { it.length() },
                    currentFile = "",
                    bytesPerSecond = 0L,
                    etaSeconds = 0L,
                    lastError = throwable.message ?: "Import failed",
                    updatedAt = System.currentTimeMillis(),
                ) ?: existing,
                onProgress,
            )
            throw throwable
        }
    }

    suspend fun markCanceled(catalogId: String) = withContext(Dispatchers.IO) {
        installDao.getByCatalogId(catalogId)?.let { entity ->
            installDao.upsert(
                entity.copy(
                    status = LocalModelStatus.CANCELED.name,
                    currentFile = "",
                    bytesPerSecond = 0L,
                    etaSeconds = 0L,
                    lastError = if (entity.lastError.isBlank()) "Install canceled." else entity.lastError,
                    updatedAt = System.currentTimeMillis(),
                )
            )
        }
    }

    suspend fun getHuggingFaceToken(): String = withContext(Dispatchers.IO) {
        secureStore.getSecret(HUGGING_FACE_TOKEN_KEY).orEmpty()
    }

    suspend fun hasHuggingFaceToken(): Boolean = withContext(Dispatchers.IO) {
        secureStore.hasSecret(HUGGING_FACE_TOKEN_KEY)
    }

    suspend fun saveHuggingFaceToken(token: String) = withContext(Dispatchers.IO) {
        val trimmed = token.trim()
        if (trimmed.isBlank()) {
            secureStore.removeSecret(HUGGING_FACE_TOKEN_KEY)
        } else {
            secureStore.putSecret(HUGGING_FACE_TOKEN_KEY, trimmed)
        }
    }

    suspend fun updateModelMetadata(model: Model) = withContext(Dispatchers.IO) {
        val existing = installDao.getAll()
            .firstOrNull { install ->
                install.status == LocalModelStatus.READY.name &&
                    install.toCatalogEntryOrNull()?.modelUuid == model.id
            }
            ?: return@withContext
        val entry = existing.toCatalogEntryOrNull() ?: return@withContext
        val updatedEntry = entry.copy(
            displayName = model.displayName.ifBlank { entry.displayName },
            type = model.type,
            inputModalities = model.inputModalities.distinct(),
            outputModalities = model.outputModalities.distinct(),
            abilities = model.abilities.distinct(),
            customIconUri = model.customIconUri,
        )
        installDao.upsert(existing.mergeCatalogEntry(updatedEntry))
    }

    suspend fun removeModel(catalogId: String) = withContext(Dispatchers.IO) {
        val existing = installDao.getByCatalogId(catalogId) ?: return@withContext
        modelDirectory(catalogId).deleteRecursively()
        installDao.upsert(
            existing.copy(
                filePathsJson = "[]",
                installedSizeBytes = 0L,
                status = LocalModelStatus.NOT_DOWNLOADED.name,
                currentFile = "",
                bytesDownloaded = 0L,
                bytesTotal = existing.downloadSizeBytes,
                progressPercent = 0,
                bytesPerSecond = 0L,
                etaSeconds = 0L,
                sourceUri = if (existing.provenance == LocalModelProvenance.IMPORTED.name) "" else existing.sourceUri,
                lastError = "",
                updatedAt = System.currentTimeMillis(),
            )
        )
    }

    private suspend fun seedCatalogEntriesIfMissing() {
        val existingById = installDao.getAll().associateBy { it.catalogId }
        installDao.upsertAll(
            LocalModelCatalog.entries.map { entry ->
                existingById[entry.id]?.mergeCatalogEntry(entry) ?: entry.toEntity(status = LocalModelStatus.NOT_DOWNLOADED)
            }
        )
    }

    private suspend fun reconcileMissingFiles(installs: List<LocalModelInstallEntity>): List<LocalModelInstallEntity> {
        return installs.map { install ->
            if (install.status != LocalModelStatus.READY.name) {
                install
            } else {
                val filePaths = runCatching {
                    JsonInstant.decodeFromString<List<String>>(install.filePathsJson)
                }.getOrDefault(emptyList())
                val allPresent = filePaths.isNotEmpty() && filePaths.all { File(it).exists() }
                if (allPresent) {
                    install
                } else {
                    val repaired = install.copy(
                        status = LocalModelStatus.NOT_DOWNLOADED.name,
                        filePathsJson = "[]",
                        installedSizeBytes = 0L,
                        currentFile = "",
                        bytesDownloaded = 0L,
                        progressPercent = 0,
                        bytesPerSecond = 0L,
                        etaSeconds = 0L,
                        lastError = "Model files are missing and need to be downloaded or imported again.",
                        updatedAt = System.currentTimeMillis(),
                    )
                    installDao.upsert(repaired)
                    repaired
                }
            }
        }
    }

    private suspend fun syncLocalProviderModels(installs: List<LocalModelInstallEntity>) {
        val readyModels = installs
            .filter { it.status == LocalModelStatus.READY.name }
            .mapNotNull { install -> install.toCatalogEntryOrNull()?.toModel() }
            .sortedBy { it.displayName.lowercase() }

        settingsStore.update { current ->
            current.ensureBuiltInProviders().withLocalProviderModels(readyModels)
        }
    }

    private suspend fun resolveDownloadFiles(entry: LocalModelCatalogEntry): LocalModelCatalogEntry {
        if (entry.files.isNotEmpty()) {
            upsertResolvedEntry(entry)
            return entry
        }
        if (entry.repoId.isBlank()) return entry

        val hfModelDetails = runCatching {
            fetchHuggingFaceModelDetails(entry.repoId)
        }.getOrNull()
        
        val targetExtensions = entry.packageFormat.extensions

        client.newCall(
            Request.Builder()
                .url("https://huggingface.co/api/models/${entry.repoId}")
                .withHuggingFaceAuth()
                .build()
        ).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Failed to fetch Hugging Face metadata: HTTP ${response.code}")
            }
            val body = response.body?.string() ?: throw IOException("Missing Hugging Face metadata response body.")
            val payload = JsonInstant.decodeFromString<HuggingFaceModelApiResponse>(body)
            val matchingFiles = payload.siblings.mapNotNull { sibling ->
                val relativePath = sibling.relativePath ?: return@mapNotNull null
                if (targetExtensions.none { relativePath.endsWith(it, ignoreCase = true) }) {
                    return@mapNotNull null
                }
                LocalModelDownloadFile(relativePath = relativePath, sizeBytes = sibling.sizeBytes ?: 0L)
            }.sortedBy { it.relativePath.lowercase() }

            if (matchingFiles.isEmpty()) {
                throw IOException("No compatible ${entry.packageFormat.name.lowercase()} files were found in ${entry.repoId}.")
            }

            var resolved = entry.withResolvedFiles(matchingFiles)
            if (hfModelDetails != null) {
                resolved = applyHuggingFaceMetadata(resolved, hfModelDetails)
            }
            upsertResolvedEntry(resolved)
            return resolved
        }
    }

    private fun applyHuggingFaceMetadata(entry: LocalModelCatalogEntry, hfModel: HuggingFaceSearchModel): LocalModelCatalogEntry {
        val tags = hfModel.tags.map { it.lowercase() }
        val pipeline = hfModel.pipeline_tag?.lowercase()

        val type = if (pipeline == "feature-extraction" || pipeline == "sentence-similarity" || tags.contains("sentence-transformers")) {
            me.rerere.ai.provider.ModelType.EMBEDDING
        } else {
            me.rerere.ai.provider.ModelType.CHAT
        }
        
        val inputModalities = mutableListOf(me.rerere.ai.provider.Modality.TEXT)
        if (pipeline == "image-text-to-text" || tags.contains("multimodal") || tags.contains("vision")) {
            inputModalities.add(me.rerere.ai.provider.Modality.IMAGE)
        }
        
        val abilities = mutableListOf<me.rerere.ai.provider.ModelAbility>()
        if (pipeline != "feature-extraction" && pipeline != "sentence-similarity") {
            if (tags.contains("reasoning") || tags.any { it.contains("think") } || hfModel.id.lowercase().contains("r1")) {
                abilities.add(me.rerere.ai.provider.ModelAbility.REASONING)
            }
        }

        return entry.copy(
            type = type,
            inputModalities = inputModalities,
            abilities = abilities,
        )
    }

    private suspend fun fetchHuggingFaceModelDetails(repoId: String): HuggingFaceSearchModel = withContext(Dispatchers.IO) {
        client.newCall(
            Request.Builder()
                .url("https://huggingface.co/api/models/$repoId")
                .withHuggingFaceAuth()
                .build()
        ).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Failed to fetch details for $repoId")
            val body = response.body?.string() ?: throw IOException("Missing body")
            JsonInstant.decodeFromString<HuggingFaceSearchModel>(body)
        }
    }

    suspend fun searchHuggingFaceModels(query: String): List<LocalModelCatalogEntry> = withContext(Dispatchers.IO) {
        val url = okhttp3.HttpUrl.Builder()
            .scheme("https")
            .host("huggingface.co")
            .addPathSegment("api")
            .addPathSegment("models")
            .addQueryParameter("search", query)
            .addQueryParameter("limit", "30")
            .addQueryParameter("full", "true")
            .build()
        
        val response = client.newCall(
            Request.Builder()
                .url(url)
                .withHuggingFaceAuth()
                .build()
        ).execute()

        val models = response.use { res ->
            if (!res.isSuccessful) return@withContext emptyList()
            val bodyStr = res.body?.string() ?: return@withContext emptyList()
            JsonInstant.decodeFromString<List<HuggingFaceSearchModel>>(bodyStr)
        }

        models.mapNotNull { hfModel ->
            val tags = hfModel.tags.map { it.lowercase() }
            val pipeline = hfModel.pipeline_tag?.lowercase()
            
            if (pipeline == "feature-extraction" || pipeline == "sentence-similarity" || tags.contains("sentence-transformers")) {
                return@mapNotNull null
            }
            
            val backend = when {
                tags.contains("gguf") -> LocalRuntimeBackend.LLAMA_CPP
                tags.contains("tflite") || tags.contains("litert") -> LocalRuntimeBackend.LITERT
                else -> return@mapNotNull null
            }

            val format = when (backend) {
                LocalRuntimeBackend.LLAMA_CPP -> LocalPackageFormat.GGUF
                LocalRuntimeBackend.LITERT -> LocalPackageFormat.LITERT_LM
            }

            val displayName = hfModel.id.substringAfter("/")

            applyHuggingFaceMetadata(
                entry = LocalModelCatalogEntry(
                    id = hfModel.id,
                    modelUuid = kotlin.uuid.Uuid.random(),
                    repoId = hfModel.id,
                    modelId = hfModel.id,
                    displayName = displayName,
                    description = "Found via Hugging Face Search.",
                    runtimeBackend = backend,
                    packageFormat = format,
                    downloadAccess = LocalModelDownloadAccess.PUBLIC,
                    provenance = LocalModelProvenance.CURATED,
                    minimumRamBytes = 0L,
                    recommendedRamBytes = 0L,
                    estimatedDownloadBytes = 0L,
                    estimatedInstalledBytes = 0L,
                ),
                hfModel = hfModel
            )
        }.sortedByDescending { it.abilities.contains(me.rerere.ai.provider.ModelAbility.REASONING) }
    }

    private suspend fun upsertResolvedEntry(entry: LocalModelCatalogEntry) {
        installDao.getByCatalogId(entry.id)?.let { existing ->
            installDao.upsert(existing.mergeCatalogEntry(entry))
        }
    }

    private fun modelDirectory(catalogId: String): File = File(context.filesDir, "local_models/$catalogId")

    private suspend fun downloadFile(
        entry: LocalModelCatalogEntry,
        file: LocalModelDownloadFile,
        installDir: File,
        totalBytes: Long,
        completedBytesBeforeFile: Long,
        onProgress: suspend (bytesDownloaded: Long, bytesPerSecond: Long, etaSeconds: Long, currentFile: String) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        val targetFile = File(installDir, file.relativePath)
        targetFile.parentFile?.mkdirs()
        val tempFile = File(targetFile.absolutePath + ".part")
        val resumedBytes = tempFile.takeIf(File::exists)?.length() ?: 0L
        val requestBuilder = Request.Builder().url(entry.buildResolveUrl(file))
            .withHuggingFaceAuth()
        if (resumedBytes > 0L) {
            requestBuilder.header("Range", "bytes=$resumedBytes-")
        }
        client.newCall(requestBuilder.build()).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Failed to download ${file.relativePath}: HTTP ${response.code}")
            }
            val append = resumedBytes > 0L && response.code == 206
            if (!append && tempFile.exists()) {
                tempFile.delete()
            }

            val remainingBytes = response.body?.contentLength()?.takeIf { it > 0L } ?: 0L
            val fileBytes = when {
                append && remainingBytes > 0L -> resumedBytes + remainingBytes
                file.sizeBytes > 0L -> file.sizeBytes
                remainingBytes > 0L -> remainingBytes
                else -> 0L
            }

            val startedAt = System.currentTimeMillis()
            var lastUpdateAt = startedAt
            var writtenForThisAttempt = 0L
            tempFile.outputStream().use { output ->
                if (append) {
                    output.channel.position(resumedBytes)
                }
                response.body?.byteStream()?.use { input ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        writtenForThisAttempt += read
                        val now = System.currentTimeMillis()
                        if (now - lastUpdateAt >= PROGRESS_UPDATE_INTERVAL_MS) {
                            val bytesDownloaded = completedBytesBeforeFile + resumedBytes + writtenForThisAttempt
                            val elapsed = (now - startedAt).coerceAtLeast(1L)
                            val bytesPerSecond = ((resumedBytes + writtenForThisAttempt) * 1000L) / elapsed
                            val totalBytesHint = when {
                                totalBytes > 0L -> totalBytes
                                fileBytes > 0L -> completedBytesBeforeFile + fileBytes
                                else -> 0L
                            }
                            val etaSeconds = if (totalBytesHint > 0L && bytesPerSecond > 0L) {
                                ((totalBytesHint - bytesDownloaded).coerceAtLeast(0L) / bytesPerSecond)
                            } else {
                                0L
                            }
                            onProgress(bytesDownloaded, bytesPerSecond, etaSeconds, file.relativePath)
                            lastUpdateAt = now
                        }
                    }
                } ?: throw IOException("Missing response body for ${file.relativePath}")
            }
        }
        if (targetFile.exists()) targetFile.delete()
        if (!tempFile.renameTo(targetFile)) {
            throw IOException("Failed to finalize ${file.relativePath}")
        }
        targetFile
    }

    private suspend fun publish(
        entity: LocalModelInstallEntity,
        onProgress: suspend (LocalInstallProgressSnapshot) -> Unit,
    ) {
        installDao.upsert(entity)
        onProgress(entity.toProgressSnapshot())
    }

    private fun listInstalledFiles(directory: File): List<File> {
        return if (!directory.exists()) {
            emptyList()
        } else {
            directory.walkTopDown().filter { it.isFile && !it.name.endsWith(".part") }.toList()
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
    }

    private fun queryDisplayName(uri: Uri): String? {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) return cursor.getString(index)
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/')
    }

    private fun queryFileSize(uri: Uri): Long {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (index >= 0) return cursor.getLong(index)
            }
        }
        return 0L
    }

    private fun Request.Builder.withHuggingFaceAuth(): Request.Builder {
        val token = secureStore.getSecret(HUGGING_FACE_TOKEN_KEY).orEmpty()
        return if (token.isBlank()) this else header("Authorization", "Bearer $token")
    }

    private fun LocalModelCatalogEntry.toEntity(
        status: LocalModelStatus,
        filePaths: List<String> = emptyList(),
        installedSizeBytes: Long = 0L,
        lastError: String = "",
    ): LocalModelInstallEntity {
        return LocalModelInstallEntity(
            catalogId = id,
            repoId = repoId,
            revision = revision,
            modelId = modelId,
            displayName = displayName,
            description = description,
            entryJson = serialize(),
            filePathsJson = JsonInstant.encodeToString(filePaths),
            downloadSizeBytes = estimatedDownloadBytes,
            estimatedInstalledSizeBytes = estimatedInstalledBytes,
            installedSizeBytes = installedSizeBytes,
            checksum = primaryFile?.sha256 ?: "",
            status = status.name,
            runtimeBackend = runtimeBackend.name,
            provenance = provenance.name,
            downloadAccess = downloadAccess.name,
            supportedAbisJson = JsonInstant.encodeToString(supportedAbis),
            minSdk = minSdk,
            minimumRamBytes = minimumRamBytes,
            recommendedRamBytes = recommendedRamBytes,
            delegateInfo = runtimeBackend.name,
            safeForBackground = safeForBackground,
            currentFile = "",
            bytesDownloaded = 0L,
            bytesTotal = estimatedDownloadBytes,
            progressPercent = 0,
            bytesPerSecond = 0L,
            etaSeconds = 0L,
            sourceUri = "",
            lastError = lastError,
            updatedAt = System.currentTimeMillis(),
        )
    }
}

private fun LocalModelInstallEntity.mergeCatalogEntry(entry: LocalModelCatalogEntry): LocalModelInstallEntity {
    val clearStaleAuthFailure = entry.downloadAccess != LocalModelDownloadAccess.PUBLIC &&
        status == LocalModelStatus.FAILED.name &&
        lastError.contains("401", ignoreCase = true)
    val effectiveEntry = entry.withUserOverridesFrom(toCatalogEntryOrNull())

    return copy(
        repoId = effectiveEntry.repoId,
        revision = effectiveEntry.revision,
        modelId = effectiveEntry.modelId,
        displayName = effectiveEntry.displayName,
        description = effectiveEntry.description,
        entryJson = effectiveEntry.serialize(),
        downloadSizeBytes = effectiveEntry.estimatedDownloadBytes,
        estimatedInstalledSizeBytes = effectiveEntry.estimatedInstalledBytes,
        runtimeBackend = effectiveEntry.runtimeBackend.name,
        provenance = effectiveEntry.provenance.name,
        downloadAccess = effectiveEntry.downloadAccess.name,
        supportedAbisJson = JsonInstant.encodeToString(effectiveEntry.supportedAbis),
        minSdk = effectiveEntry.minSdk,
        minimumRamBytes = effectiveEntry.minimumRamBytes,
        recommendedRamBytes = effectiveEntry.recommendedRamBytes,
        delegateInfo = effectiveEntry.runtimeBackend.name,
        safeForBackground = effectiveEntry.safeForBackground,
        status = if (clearStaleAuthFailure) LocalModelStatus.NOT_DOWNLOADED.name else status,
        currentFile = if (clearStaleAuthFailure) "" else currentFile,
        bytesDownloaded = if (clearStaleAuthFailure) 0L else bytesDownloaded,
        bytesTotal = if (clearStaleAuthFailure) effectiveEntry.estimatedDownloadBytes else if (bytesTotal > 0L) bytesTotal else effectiveEntry.estimatedDownloadBytes,
        progressPercent = if (clearStaleAuthFailure) 0 else progressPercent,
        bytesPerSecond = if (clearStaleAuthFailure) 0L else bytesPerSecond,
        etaSeconds = if (clearStaleAuthFailure) 0L else etaSeconds,
        lastError = if (clearStaleAuthFailure) "" else lastError,
    )
}

private fun LocalModelCatalogEntry.withUserOverridesFrom(existing: LocalModelCatalogEntry?): LocalModelCatalogEntry {
    if (existing == null) return this
    return copy(
        displayName = existing.displayName.ifBlank { displayName },
        type = existing.type,
        inputModalities = existing.inputModalities.ifEmpty { inputModalities },
        outputModalities = existing.outputModalities.ifEmpty { outputModalities },
        abilities = existing.abilities,
        files = if (files.isEmpty() && existing.files.isNotEmpty()) existing.files else files,
        estimatedDownloadBytes = if (estimatedDownloadBytes == 0L && existing.estimatedDownloadBytes > 0L) existing.estimatedDownloadBytes else estimatedDownloadBytes,
        estimatedInstalledBytes = if (estimatedInstalledBytes == 0L && existing.estimatedInstalledBytes > 0L) existing.estimatedInstalledBytes else estimatedInstalledBytes,
        customIconUri = existing.customIconUri,
    )
}

private fun LocalModelInstallEntity.toProgressSnapshot(): LocalInstallProgressSnapshot {
    return LocalInstallProgressSnapshot(
        catalogId = catalogId,
        displayName = displayName,
        status = runCatching { LocalModelStatus.valueOf(status) }.getOrDefault(LocalModelStatus.NOT_DOWNLOADED),
        bytesDownloaded = bytesDownloaded,
        bytesTotal = bytesTotal,
        progressPercent = progressPercent,
        bytesPerSecond = bytesPerSecond,
        etaSeconds = etaSeconds,
        currentFile = currentFile,
        lastError = lastError,
    )
}

@Serializable
private data class HuggingFaceModelApiResponse(
    val siblings: List<HuggingFaceModelSibling> = emptyList(),
)

@Serializable
private data class HuggingFaceModelSibling(
    @SerialName("rfilename")
    val relativePath: String? = null,
    @SerialName("size")
    val sizeBytes: Long? = null,
)

@Serializable
private data class HuggingFaceSearchModel(
    val id: String = "",
    val tags: List<String> = emptyList(),
    val pipeline_tag: String? = null,
    val downloads: Int = 0,
)
