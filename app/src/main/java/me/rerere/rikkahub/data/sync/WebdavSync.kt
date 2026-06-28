package me.rerere.rikkahub.data.sync

import android.content.Context
import at.bitfire.dav4jvm.okhttp.DavCollection
import at.bitfire.dav4jvm.okhttp.Response
import at.bitfire.dav4jvm.okhttp.exception.NotFoundException
import at.bitfire.dav4jvm.property.webdav.DisplayName
import at.bitfire.dav4jvm.property.webdav.GetContentLength
import at.bitfire.dav4jvm.property.webdav.GetLastModified
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.SecretKeyManager
import me.rerere.rikkahub.data.datastore.WebDavConfig
import me.rerere.rikkahub.data.datastore.sanitize
import me.rerere.rikkahub.data.db.AppDatabase
import me.rerere.rikkahub.utils.LogUtil
import okio.buffer
import okio.sink
import okio.source
import java.io.File
import java.time.Instant
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

private const val TAG = "DataSync"

class WebdavSync(
    private val settingsStore: SettingsStore,
    private val json: Json,
    private val context: Context,
    private val secretKeyManager: SecretKeyManager,
    private val appDatabase: AppDatabase,
    private val webDavClientFactory: WebDavClientFactory,
) {
    suspend fun testWebdav(webDavConfig: WebDavConfig) {
        val davCollection = webDavClientFactory.collection(webDavConfig, path = "")

        withContext(Dispatchers.IO) {
            davCollection.propfind(depth = 1) { response, relation ->
                LogUtil.i(TAG, "testWebdav: $response | $relation")
            }
        }
    }

    suspend fun backupToWebDav(webDavConfig: WebDavConfig) = withContext(Dispatchers.IO) {
        val file = prepareBackupFile(webDavConfig)
        val collection = webDavClientFactory.collection(webDavConfig)
        collection.ensureCollectionExists()
        val target = webDavClientFactory.collection(webDavConfig, file.name)
        webDavClientFactory.putFile(target, file) { response ->
            LogUtil.i(TAG, "backupToWebDav: $response")
        }
    }

    suspend fun listBackupFiles(webDavConfig: WebDavConfig): List<WebDavBackupItem> =
        withContext(Dispatchers.IO) {
            val collection = webDavClientFactory.collection(webDavConfig)
            val files = mutableListOf<WebDavBackupItem>()
            collection.propfind(depth = 1) { response, relation ->
                LogUtil.i(TAG, "listBackupFiles: ${response.properties} ${response.href}")
                if (relation == Response.HrefRelation.MEMBER) {
                    val displayName = response.properties.filterIsInstance<DisplayName>()
                        .firstOrNull()?.displayName ?: "Unknown"
                    val size = response.properties.filterIsInstance<GetContentLength>()
                        .firstOrNull()?.contentLength ?: 0L
                    val lastModified = response.properties.filterIsInstance<GetLastModified>()
                        .firstOrNull()?.lastModified ?: Instant.EPOCH
                    files.add(
                        WebDavBackupItem(
                            href = response.href.toString(),
                            displayName = displayName,
                            size = size,
                            lastModified = lastModified,
                        )
                    )
                }
            }
            files
        }

    suspend fun restoreFromWebDav(
        webDavConfig: WebDavConfig,
        item: WebDavBackupItem,
    ): RestoreResult = withContext(Dispatchers.IO) {
        val collection = webDavClientFactory.hrefCollection(webDavConfig, item.href)
        val backupFile = File(context.cacheDir, item.displayName)
        if (backupFile.exists()) {
            backupFile.delete()
        }

        collection.get(
            accept = "",
            headers = null,
        ) { response ->
            if (response.isSuccessful) {
                LogUtil.i(
                    TAG,
                    "restoreFromWebDav: Downloading ${item.displayName} to ${backupFile.absolutePath}",
                )
                response.body?.byteStream()?.use { inputStream ->
                    backupFile.sink().buffer().outputStream().use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
            } else {
                LogUtil.e(
                    TAG,
                    "restoreFromWebDav: Failed to download ${item.displayName}, response: $response",
                )
                throw Exception("Failed to download backup file: ${response.message}")
            }
        }

        LogUtil.i(TAG, "restoreFromWebDav: Downloaded ${backupFile.length()} bytes")

        try {
            restoreFromBackupFile(backupFile)
        } finally {
            if (backupFile.exists()) {
                backupFile.delete()
                LogUtil.i(TAG, "restoreFromWebDav: Cleaned up temporary backup file")
            }
        }
    }

    suspend fun deleteWebDavBackupFile(webDavConfig: WebDavConfig, item: WebDavBackupItem) =
        withContext(Dispatchers.IO) {
            val collection = webDavClientFactory.hrefCollection(webDavConfig, item.href)
            collection.delete { response ->
                LogUtil.i(TAG, "deleteWebDavBackupFile: $response")
            }
        }

    suspend fun restoreFromLocalFile(file: File, webDavConfig: WebDavConfig): RestoreResult =
        withContext(Dispatchers.IO) {
            LogUtil.i(TAG, "restoreFromLocalFile: Starting restore from ${file.absolutePath}")

            if (!file.exists()) {
                throw Exception("Backup file does not exist")
            }

            if (!file.canRead()) {
                throw Exception("Cannot read backup file")
            }

            try {
                restoreFromBackupFile(file)
            } catch (e: Exception) {
                LogUtil.e(TAG, "restoreFromLocalFile: Failed to restore from local file", e)
                throw Exception("Restore failed: ${e.message}")
            }
        }

    suspend fun prepareBackupFile(webDavConfig: WebDavConfig): File = withContext(Dispatchers.IO) {
        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
        val backupFile = File(context.cacheDir, "LastChat_backup_$timestamp.zip")
        if (backupFile.exists()) {
            backupFile.delete()
        }

        val includesDatabase = webDavConfig.items.contains(WebDavConfig.BackupItem.DATABASE)
        val includesFiles = webDavConfig.items.contains(WebDavConfig.BackupItem.FILES)
        val manifest = BackupManifest(
            includesDatabase = includesDatabase,
            includesFiles = includesFiles,
            managedFileDirs = if (includesFiles) BackupArchiveFormat.MANAGED_FILE_DIRS else emptyList(),
            sharedPrefsStores = BackupArchiveFormat.PORTABLE_SHARED_PREF_STORES,
        )

        ZipOutputStream(backupFile.sink().buffer().outputStream()).use { zipOut ->
            val settingsForExport =
                secretKeyManager.populateSecretsForExport(settingsStore.settingsFlow.value)
            addVirtualFileToZip(
                zipOut = zipOut,
                name = BackupArchiveFormat.SETTINGS_ENTRY,
                content = json.encodeToString(settingsForExport),
            )
            addVirtualFileToZip(
                zipOut = zipOut,
                name = BackupArchiveFormat.MANIFEST_ENTRY,
                content = json.encodeToString(manifest),
            )

            BackupArchiveFormat.PORTABLE_SHARED_PREF_STORES.forEach { storeName ->
                val snapshot = exportSharedPreferencesSnapshot(context, storeName)
                addVirtualFileToZip(
                    zipOut = zipOut,
                    name = BackupArchiveFormat.prefEntryName(storeName),
                    content = json.encodeToString(snapshot),
                )
            }

            if (includesDatabase) {
                checkpointDatabase()
                addDatabaseEntries(zipOut)
            }

            if (includesFiles) {
                addManagedFileEntries(zipOut)
            }
        }

        backupFile
    }

    data class RestoreResult(
        val sanitization: DatabaseSanitizer.SanitizationResult,
        val settingsCleanup: BackupCleanupResult,
    )

    private suspend fun restoreFromBackupFile(backupFile: File): RestoreResult =
        withContext(Dispatchers.IO) {
            LogUtil.i(TAG, "restoreFromBackupFile: Starting restore from ${backupFile.absolutePath}")

            var unsupportedZipEntriesBytes = 0L
            var settingsCleanupResult = BackupCleanupResult()
            var sanitizationResult = DatabaseSanitizer.SanitizationResult()
            var settingsJson: String? = null
            var manifest: BackupManifest? = null
            val stagedPrefs = linkedMapOf<String, SharedPreferencesSnapshot>()
            val stagedManagedDirs = linkedSetOf<String>()
            var foundSupportedEntry = false

            val restoreTempDir = File(context.cacheDir, "restore_temp_${System.currentTimeMillis()}")
            val stagedFilesDir = File(restoreTempDir, "files")
            val stagedDbDir = File(restoreTempDir, "db")
            restoreTempDir.mkdirs()
            stagedFilesDir.mkdirs()
            stagedDbDir.mkdirs()

            try {
                ZipInputStream(backupFile.source().buffer().inputStream()).use { zipIn ->
                    var entry: ZipEntry?
                    while (zipIn.nextEntry.also { entry = it } != null) {
                        val zipEntry = entry ?: continue
                        val normalizedName = BackupArchiveFormat.normalizeEntryName(zipEntry.name)
                        LogUtil.i(TAG, "restoreFromBackupFile: Processing entry $normalizedName")

                        when {
                            normalizedName == BackupArchiveFormat.SETTINGS_ENTRY -> {
                                settingsJson = zipIn.readBytes().toString(Charsets.UTF_8)
                                foundSupportedEntry = true
                            }

                            normalizedName == BackupArchiveFormat.MANIFEST_ENTRY -> {
                                val manifestJson = zipIn.readBytes().toString(Charsets.UTF_8)
                                manifest = json.decodeFromString<BackupManifest>(manifestJson)
                                foundSupportedEntry = true
                            }

                            BackupArchiveFormat.isDatabaseEntry(normalizedName) -> {
                                stageDatabaseEntry(zipIn, stagedDbDir, normalizedName)
                                foundSupportedEntry = true
                            }

                            normalizedName.startsWith("${BackupArchiveFormat.PREFS_DIR}/") &&
                                normalizedName.endsWith(".json") -> {
                                val storeName = normalizedName
                                    .removePrefix("${BackupArchiveFormat.PREFS_DIR}/")
                                    .removeSuffix(".json")
                                if (BackupArchiveFormat.PORTABLE_SHARED_PREF_STORES.contains(storeName)) {
                                    val snapshotJson = zipIn.readBytes().toString(Charsets.UTF_8)
                                    val snapshot =
                                        json.decodeFromString<SharedPreferencesSnapshot>(snapshotJson)
                                    stagedPrefs[storeName] = snapshot
                                    foundSupportedEntry = true
                                } else {
                                    unsupportedZipEntriesBytes += zipEntry.size.coerceAtLeast(0L)
                                }
                            }

                            BackupArchiveFormat.managedDirForEntry(normalizedName) != null -> {
                                val managedDir = BackupArchiveFormat.managedDirForEntry(normalizedName)
                                    ?: error("Managed directory was null for $normalizedName")
                                stageManagedFileEntry(zipIn, stagedFilesDir, normalizedName, zipEntry.isDirectory)
                                stagedManagedDirs.add(managedDir)
                                foundSupportedEntry = true
                            }

                            else -> {
                                LogUtil.i(
                                    TAG,
                                    "restoreFromBackupFile: Skipping unsupported entry $normalizedName (${zipEntry.size} bytes)",
                                )
                                unsupportedZipEntriesBytes += zipEntry.size.coerceAtLeast(0L)
                            }
                        }

                        zipIn.closeEntry()
                    }
                }

                if (!foundSupportedEntry) {
                    throw Exception("Backup file did not contain any supported entries")
                }

                val sanitizedSettings = settingsJson?.let { rawSettingsJson ->
                    try {
                        val parsedSettings = json.decodeFromString<Settings>(rawSettingsJson)
                        parsedSettings.sanitize().also { (_, cleanup) ->
                            settingsCleanupResult = cleanup
                        }
                    } catch (e: Exception) {
                        LogUtil.e(TAG, "restoreFromBackupFile: Failed to parse settings", e)
                        throw Exception("Failed to restore settings: ${e.message}")
                    }
                }

                restoreManagedFileDirectories(
                    stagedFilesDir = stagedFilesDir,
                    manifest = manifest,
                    stagedManagedDirs = stagedManagedDirs,
                )
                restorePortableSharedPreferences(
                    stagedSnapshots = stagedPrefs,
                    manifest = manifest,
                )

                val stagedDatabase = File(stagedDbDir, BackupArchiveFormat.DB_ENTRY)
                if (stagedDatabase.exists()) {
                    LogUtil.i(TAG, "restoreFromBackupFile: Starting database sanitization")
                    try {
                        sanitizationResult = restoreDatabase(stagedDatabase)
                        LogUtil.i(TAG, "restoreFromBackupFile: Database restored and sanitized")
                    } catch (e: Exception) {
                        LogUtil.e(TAG, "restoreFromBackupFile: Failed to restore database", e)
                        throw Exception("Database sanitization failed: ${e.message}")
                    }
                }

                sanitizedSettings?.let { (cleanedSettings, _) ->
                    settingsStore.update(cleanedSettings)
                    LogUtil.i(
                        TAG,
                        "restoreFromBackupFile: Settings restored after file/db/prefs commit",
                    )
                }

                LogUtil.i(TAG, "restoreFromBackupFile: Restore completed successfully")

                val totalCleanupResult = settingsCleanupResult.copy(
                    unsupportedZipEntriesBytes = unsupportedZipEntriesBytes,
                )
                RestoreResult(
                    sanitization = sanitizationResult,
                    settingsCleanup = totalCleanupResult,
                )
            } finally {
                restoreTempDir.deleteRecursively()
            }
        }

    private fun checkpointDatabase() {
        runCatching {
            appDatabase.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").close()
        }.onFailure { error ->
            LogUtil.w(TAG, "prepareBackupFile: WAL checkpoint failed", error)
        }
    }

    private fun addDatabaseEntries(zipOut: ZipOutputStream) {
        val dbFile = context.getDatabasePath(BackupArchiveFormat.DB_ENTRY)
        if (dbFile.exists()) {
            addFileToZip(zipOut, dbFile, BackupArchiveFormat.LEGACY_DB_ENTRY)
        }

        val walFile = File(dbFile.parentFile, BackupArchiveFormat.WAL_ENTRY)
        if (walFile.exists()) {
            addFileToZip(zipOut, walFile, BackupArchiveFormat.WAL_ENTRY)
        }

        val shmFile = File(dbFile.parentFile, BackupArchiveFormat.SHM_ENTRY)
        if (shmFile.exists()) {
            addFileToZip(zipOut, shmFile, BackupArchiveFormat.SHM_ENTRY)
        }
    }

    private fun addManagedFileEntries(zipOut: ZipOutputStream) {
        BackupArchiveFormat.MANAGED_FILE_DIRS.forEach { dirName ->
            val directory = File(context.filesDir, dirName)
            enumerateDirectoryEntries(directory, dirName).forEach { entry ->
                try {
                    if (entry.isDirectory) {
                        addDirectoryToZip(zipOut, entry.entryName)
                    } else {
                        addFileToZip(zipOut, entry.source, entry.entryName)
                    }
                } catch (e: Exception) {
                    LogUtil.w(TAG, "addManagedFileEntries: Failed to zip entry ${entry.entryName} from source ${entry.source.absolutePath}", e)
                }
            }
        }
    }

    private fun stageDatabaseEntry(
        zipIn: ZipInputStream,
        stagedDbDir: File,
        entryName: String,
    ) {
        val targetName = when (entryName) {
            BackupArchiveFormat.LEGACY_DB_ENTRY, BackupArchiveFormat.DB_ENTRY -> BackupArchiveFormat.DB_ENTRY
            BackupArchiveFormat.WAL_ENTRY -> BackupArchiveFormat.WAL_ENTRY
            BackupArchiveFormat.SHM_ENTRY -> BackupArchiveFormat.SHM_ENTRY
            else -> error("Unsupported database entry: $entryName")
        }
        val targetFile = File(stagedDbDir, targetName)
        targetFile.parentFile?.mkdirs()
        targetFile.sink().buffer().outputStream().use { outputStream ->
            zipIn.copyTo(outputStream)
        }
    }

    private fun stageManagedFileEntry(
        zipIn: ZipInputStream,
        stagedFilesDir: File,
        entryName: String,
        isDirectory: Boolean,
    ) {
        val targetFile = safeZipDestination(stagedFilesDir, entryName)
        if (isDirectory || entryName.endsWith('/')) {
            targetFile.mkdirs()
            return
        }

        targetFile.parentFile?.mkdirs()
        targetFile.sink().buffer().outputStream().use { outputStream ->
            zipIn.copyTo(outputStream)
        }
    }

    private fun restoreManagedFileDirectories(
        stagedFilesDir: File,
        manifest: BackupManifest?,
        stagedManagedDirs: Set<String>,
    ) {
        val directoriesToRestore = if (manifest?.formatVersion == BackupArchiveFormat.CURRENT_FORMAT_VERSION &&
            manifest.includesFiles
        ) {
            manifest.managedFileDirs
                .filter { BackupArchiveFormat.MANAGED_FILE_DIRS.contains(it) }
                .distinct()
        } else {
            stagedManagedDirs
                .filter { BackupArchiveFormat.MANAGED_FILE_DIRS.contains(it) }
                .distinct()
                .sorted()
        }

        directoriesToRestore.forEach { dirName ->
            val liveDir = File(context.filesDir, dirName)
            if (liveDir.exists()) {
                liveDir.deleteRecursively()
            }
            liveDir.mkdirs()

            val stagedDir = File(stagedFilesDir, dirName)
            if (stagedDir.exists()) {
                mirrorDirectory(stagedDir, liveDir)
            }
        }
    }

    private fun restorePortableSharedPreferences(
        stagedSnapshots: Map<String, SharedPreferencesSnapshot>,
        manifest: BackupManifest?,
    ) {
        val prefStoresToRestore = if (manifest?.formatVersion == BackupArchiveFormat.CURRENT_FORMAT_VERSION) {
            manifest.sharedPrefsStores
                .filter { BackupArchiveFormat.PORTABLE_SHARED_PREF_STORES.contains(it) }
                .distinct()
        } else {
            stagedSnapshots.keys
                .filter { BackupArchiveFormat.PORTABLE_SHARED_PREF_STORES.contains(it) }
                .sorted()
        }

        prefStoresToRestore.forEach { storeName ->
            val prefs = context.applicationContext.getSharedPreferences(storeName, Context.MODE_PRIVATE)
            val snapshot = stagedSnapshots[storeName]
            if (snapshot != null) {
                restoreSharedPreferencesSnapshot(prefs, snapshot)
            } else {
                prefs.edit().clear().commit()
            }
        }
    }

    private fun restoreLiveDatabase(stagedDbFile: File): DatabaseSanitizer.SanitizationResult {
        val (cleanDb, result) = DatabaseSanitizer.sanitize(context, stagedDbFile)
        appDatabase.close()
        context.deleteDatabase(BackupArchiveFormat.DB_ENTRY)

        val finalDbFile = context.getDatabasePath(BackupArchiveFormat.DB_ENTRY)
        finalDbFile.parentFile?.mkdirs()
        cleanDb.copyTo(finalDbFile, overwrite = true)

        val cleanWal = File(cleanDb.path + "-wal")
        val cleanShm = File(cleanDb.path + "-shm")
        val finalWal = File(finalDbFile.path + "-wal")
        val finalShm = File(finalDbFile.path + "-shm")

        if (cleanWal.exists()) {
            cleanWal.copyTo(finalWal, overwrite = true)
        } else {
            finalWal.delete()
        }

        if (cleanShm.exists()) {
            cleanShm.copyTo(finalShm, overwrite = true)
        } else {
            finalShm.delete()
        }

        context.deleteDatabase("rikka_hub_sanitized")
        return result
    }

    private fun restoreDatabase(stagedDbFile: File): DatabaseSanitizer.SanitizationResult {
        return restoreLiveDatabase(stagedDbFile)
    }

    private fun mirrorDirectory(sourceDir: File, targetDir: File) {
        sourceDir.walkTopDown().forEach { source ->
            val relative = source.relativeTo(sourceDir)
            val target = File(targetDir, relative.path)
            if (source.isDirectory) {
                target.mkdirs()
            } else {
                target.parentFile?.mkdirs()
                source.copyTo(target, overwrite = true)
            }
        }
    }
}

private fun addFileToZip(zipOut: ZipOutputStream, file: File, entryName: String) {
    file.source().buffer().inputStream().use { inputStream ->
        val zipEntry = ZipEntry(entryName)
        zipOut.putNextEntry(zipEntry)
        inputStream.copyTo(zipOut)
        zipOut.closeEntry()
        LogUtil.d(TAG, "addFileToZip: Added $entryName (${file.length()} bytes) to zip")
    }
}

private fun addDirectoryToZip(zipOut: ZipOutputStream, entryName: String) {
    val normalizedName = if (entryName.endsWith('/')) entryName else "$entryName/"
    val zipEntry = ZipEntry(normalizedName)
    zipOut.putNextEntry(zipEntry)
    zipOut.closeEntry()
}

private fun addVirtualFileToZip(zipOut: ZipOutputStream, name: String, content: String) {
    val zipEntry = ZipEntry(name)
    zipOut.putNextEntry(zipEntry)
    zipOut.write(content.toByteArray())
    zipOut.closeEntry()
    LogUtil.i(TAG, "addVirtualFileToZip: $name (${content.length} bytes)")
}

private suspend fun DavCollection.ensureCollectionExists() = withContext(Dispatchers.IO) {
    try {
        propfind(depth = 0) { response, relation ->
            LogUtil.i(TAG, "ensureCollectionExists: $response $relation")
        }
    } catch (e: NotFoundException) {
        LogUtil.i(TAG, "ensureCollectionExists: ${this@ensureCollectionExists.location}")
        mkCol(null) { response ->
            LogUtil.i(TAG, "ensureCollectionExists: $response")
        }
    }
}

data class WebDavBackupItem(
    val href: String,
    val displayName: String,
    val size: Long,
    val lastModified: Instant,
)
