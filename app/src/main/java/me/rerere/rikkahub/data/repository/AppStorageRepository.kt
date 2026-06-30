package me.rerere.rikkahub.data.repository

import android.app.usage.StorageStatsManager
import android.content.Context
import android.net.Uri
import android.os.Process
import android.os.storage.StorageManager
import android.util.Log
import androidx.core.net.toFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import me.rerere.common.android.appTempFolder
import me.rerere.rikkahub.AppScope
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.model.AppStorageSnapshot
import me.rerere.rikkahub.data.model.Avatar
import me.rerere.rikkahub.data.model.OtherUploadFile
import me.rerere.rikkahub.data.model.StorageCategoryUsage
import me.rerere.rikkahub.utils.getFileMimeType
import me.rerere.rikkahub.utils.OwnedFileDirectory
import me.rerere.rikkahub.utils.getOwnedDirectory
import me.rerere.rikkahub.utils.importOwnedFile
import me.rerere.rikkahub.utils.JsonInstant
import me.rerere.rikkahub.utils.jsonPrimitiveOrNull
import me.rerere.rikkahub.utils.resolveOwnedFile
import me.rerere.rikkahub.web.WebUploadRegistry
import java.io.File
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi

private const val TAG = "AppStorageRepo"

private const val CATEGORY_CHAT = "chat_attachments"
private const val CATEGORY_ASSISTANT_MEDIA = "assistant_media"
private const val CATEGORY_LOREBOOK_MEDIA = "lorebook_media"
private const val CATEGORY_GENERATED_MEDIA = "generated_media"
private const val CATEGORY_WORKSPACES = "workspaces"
private const val CATEGORY_ICONS_AND_FONTS = "icons_and_fonts"
private const val CATEGORY_DATABASES = "databases"
private const val CATEGORY_ICON_CACHE = "icon_cache"
private const val CATEGORY_OCR_CACHE = "ocr_cache"
private const val CATEGORY_TEMP_FILES = "temp_files"
private const val CATEGORY_APP_CACHE = "app_cache"
private const val CATEGORY_CODE_CACHE = "code_cache"
private const val CATEGORY_OTHER_UPLOADS = "other_uploads"
private const val CATEGORY_OTHER_APP_DATA = "other_app_data"

private data class AppPackageStorageStats(
    val appBytes: Long = 0L,
    val dataBytes: Long = 0L,
    val cacheBytes: Long = 0L,
)

private data class AppStorageAuditState(
    val packageStats: AppPackageStorageStats = AppPackageStorageStats(),
    val categoriesExcludingChat: List<StorageCategoryUsage> = emptyList(),
    val isScanning: Boolean = true,
)

@OptIn(ExperimentalAtomicApi::class)
class AppStorageRepository(
    private val context: Context,
    private val settingsStore: SettingsStore,
    private val chatAttachmentRepository: ChatAttachmentRepository,
    private val appScope: AppScope,
) {
    private val auditState = MutableStateFlow(AppStorageAuditState())
    private val initialScanStarted = AtomicBoolean(false)

    fun observeSnapshot(): Flow<AppStorageSnapshot> {
        ensureInitialScan()
        return combine(
            chatAttachmentRepository.observeStorageSummary(),
            auditState,
        ) { chatSummary, audit ->
            val dataAndCacheBudget = (audit.packageStats.dataBytes + audit.packageStats.cacheBytes)
                .coerceAtLeast(0L)
            val knownBytes = chatSummary.overview.totalSizeBytes + audit.categoriesExcludingChat.sumOf { it.bytes }
            val remainderBytes = (dataAndCacheBudget - knownBytes).coerceAtLeast(0L)
            val categories = buildList {
                add(
                    StorageCategoryUsage(
                        id = CATEGORY_CHAT,
                        label = "Chat attachments",
                        bytes = chatSummary.overview.totalSizeBytes,
                    )
                )
                addAll(audit.categoriesExcludingChat.filter { it.bytes > 0L })
                if (remainderBytes > 0L) {
                    add(
                        StorageCategoryUsage(
                            id = CATEGORY_OTHER_APP_DATA,
                            label = "Other app data",
                            bytes = remainderBytes,
                        )
                    )
                }
            }
            AppStorageSnapshot(
                totalBytes = audit.packageStats.appBytes + audit.packageStats.dataBytes + audit.packageStats.cacheBytes,
                appBytes = audit.packageStats.appBytes,
                dataBytes = audit.packageStats.dataBytes,
                cacheBytes = audit.packageStats.cacheBytes,
                chatBytes = chatSummary.overview.totalSizeBytes,
                chatCount = chatSummary.overview.totalCount,
                categories = categories,
                isScanning = audit.isScanning || chatSummary.isSyncing,
            )
        }
    }

    suspend fun refreshNow() = withContext(Dispatchers.IO) {
        scanStorage()
    }

    suspend fun clearCategory(categoryId: String) = withContext(Dispatchers.IO) {
        when (categoryId) {
            CATEGORY_TEMP_FILES -> context.appTempFolder.deleteRecursively()
            CATEGORY_OCR_CACHE -> {
                context.cacheDir.resolve("pdf_ocr").deleteRecursively()
                context.cacheDir.resolve("ocr_cache.json").delete()
            }

            CATEGORY_ICON_CACHE -> context.filesDir.resolve("icon_cache").deleteRecursively()
            else -> return@withContext
        }
        scanStorage()
    }

    suspend fun listOtherUploadFiles(): List<OtherUploadFile> = withContext(Dispatchers.IO) {
        val uploadDir = context.filesDir.resolve("upload")
        if (!uploadDir.exists()) {
            return@withContext emptyList()
        }
        val protectedPaths = collectProtectedUploadPaths()
        runCatching {
            uploadDir.walkTopDown()
                .filter { it.isFile }
                .mapNotNull { file ->
                    val canonicalFile = runCatching { file.canonicalFile }.getOrNull() ?: return@mapNotNull null
                    val canonicalPath = runCatching { canonicalFile.canonicalPath }.getOrNull() ?: return@mapNotNull null
                    if (canonicalPath in protectedPaths) {
                        return@mapNotNull null
                    }
                    val fileUri = Uri.fromFile(canonicalFile)
                    val mime = context.getFileMimeType(fileUri)
                    OtherUploadFile(
                        path = canonicalPath,
                        uri = fileUri.toString(),
                        displayName = canonicalFile.name,
                        mime = mime,
                        sizeBytes = canonicalFile.length(),
                        modifiedAt = canonicalFile.lastModified(),
                        isImage = mime?.startsWith("image/") == true,
                    )
                }
                .sortedWith(
                    compareByDescending<OtherUploadFile> { it.modifiedAt }
                        .thenByDescending { it.sizeBytes }
                        .thenBy { it.displayName.lowercase() }
                )
                .toList()
        }.getOrElse { error ->
            Log.e(TAG, "Failed to inspect other uploads", error)
            emptyList()
        }
    }

    suspend fun deleteOtherUploadFile(path: String): Boolean = withContext(Dispatchers.IO) {
        val uploadDir = context.filesDir.resolve("upload")
        val targetFile = runCatching { File(path).canonicalFile }.getOrNull() ?: return@withContext false
        val targetPath = runCatching { targetFile.canonicalPath }.getOrNull() ?: return@withContext false
        if (!targetFile.exists() || !targetFile.isFile || !targetFile.isInside(uploadDir)) {
            return@withContext false
        }
        if (targetPath in collectProtectedUploadPaths()) {
            return@withContext false
        }
        val deleted = runCatching { targetFile.delete() }.getOrDefault(false)
        if (deleted) {
            scanStorage()
        }
        deleted
    }

    suspend fun deleteAllOtherUploadFiles(): Int = withContext(Dispatchers.IO) {
        val orphanFiles = listOtherUploadFiles()
        var deletedCount = 0
        orphanFiles.forEach { file ->
            val deleted = runCatching { File(file.path).delete() }.getOrDefault(false)
            if (deleted) {
                deletedCount += 1
            }
        }
        if (deletedCount > 0) {
            scanStorage()
        }
        deletedCount
    }

    suspend fun deleteFilesIfUnreferenced(
        fileRefs: Collection<String>,
    ): Int = withContext(Dispatchers.IO) {
        if (fileRefs.isEmpty()) {
            return@withContext 0
        }
        val protectedPaths = collectProtectedFilePaths(root = context.filesDir)
        val targets = fileRefs.mapNotNull { fileRef -> resolveManagedFileRef(fileRef, context) }
            .mapNotNull { file -> runCatching { file.canonicalFile }.getOrNull() }
            .filter { file -> file.exists() && file.isFile && file.isInside(context.filesDir) }
            .distinctBy { file -> runCatching { file.canonicalPath }.getOrNull().orEmpty() }

        var deletedCount = 0
        targets.forEach { file ->
            val canonicalPath = runCatching { file.canonicalPath }.getOrNull() ?: return@forEach
            if (canonicalPath in protectedPaths) {
                return@forEach
            }
            val deleted = runCatching { file.delete() }.getOrDefault(false)
            if (deleted) {
                deletedCount += 1
            }
        }
        if (deletedCount > 0) {
            scanStorage()
        }
        deletedCount
    }

    private fun ensureInitialScan() {
        if (!initialScanStarted.compareAndSet(false, true)) {
            return
        }
        appScope.launch(Dispatchers.IO) {
            scanStorage()
        }
    }

    private suspend fun scanStorage() {
        auditState.update { it.copy(isScanning = true) }
        try {
            migrateLegacyOwnedUploadFiles()
            chatAttachmentRepository.indexAllConversationAttachments()
            val trackedAttachmentPaths = chatAttachmentRepository.getTrackedAttachmentFilePaths()
            val chatUsageBytes = chatAttachmentRepository.getUsageSnapshot().sumOf { it.sizeBytes }
            val categories = buildCategoryBreakdown(trackedAttachmentPaths)
            val packageStats = queryPackageStats().withFallbackDataBytes(
                fallbackDataAndCacheBytes = chatUsageBytes + categories.sumOf { it.bytes }
            )
            auditState.value = AppStorageAuditState(
                packageStats = packageStats,
                categoriesExcludingChat = categories,
                isScanning = false,
            )
        } catch (error: Exception) {
            Log.e(TAG, "Failed to scan app storage", error)
            auditState.update { it.copy(isScanning = false) }
        }
    }

    private suspend fun migrateLegacyOwnedUploadFiles() {
        val settings = settingsStore.settingsFlow.value
        if (settings.init) {
            return
        }

        val migrationCache = mutableMapOf<Pair<OwnedFileDirectory, String>, String>()
        val migratedSourceUrls = linkedSetOf<String>()
        var hasChanges = false

        val migratedAssistants = settings.assistants.map { assistant ->
            val migratedAvatar = when (val avatar = assistant.avatar) {
                is Avatar.Image -> migrateUploadUrlIfNeeded(
                    sourceUrl = avatar.url,
                    directory = OwnedFileDirectory.AVATAR,
                    migrationCache = migrationCache,
                    migratedSourceUrls = migratedSourceUrls,
                )?.let { Avatar.Image(url = it) } ?: avatar

                else -> avatar
            }
            val migratedBackground = migrateUploadUrlIfNeeded(
                sourceUrl = assistant.background,
                directory = OwnedFileDirectory.ASSISTANT_BACKGROUND,
                migrationCache = migrationCache,
                migratedSourceUrls = migratedSourceUrls,
            )
            if (migratedAvatar != assistant.avatar || (migratedBackground != null && migratedBackground != assistant.background)) {
                hasChanges = true
                assistant.copy(
                    avatar = migratedAvatar,
                    background = migratedBackground ?: assistant.background,
                )
            } else {
                assistant
            }
        }

        val migratedLorebooks = settings.lorebooks.map { lorebook ->
            val migratedCover = when (val cover = lorebook.cover) {
                is Avatar.Image -> migrateUploadUrlIfNeeded(
                    sourceUrl = cover.url,
                    directory = OwnedFileDirectory.LOREBOOK_COVER,
                    migrationCache = migrationCache,
                    migratedSourceUrls = migratedSourceUrls,
                )?.let { Avatar.Image(url = it) } ?: cover

                else -> cover
            }
            val migratedEntries = lorebook.entries.map { entry ->
                val migratedAttachments = entry.attachments.map { attachment ->
                    val migratedUrl = migrateUploadUrlIfNeeded(
                        sourceUrl = attachment.url,
                        directory = OwnedFileDirectory.LOREBOOK_ATTACHMENT,
                        migrationCache = migrationCache,
                        migratedSourceUrls = migratedSourceUrls,
                    )
                    if (migratedUrl != null && migratedUrl != attachment.url) {
                        hasChanges = true
                        attachment.copy(url = migratedUrl)
                    } else {
                        attachment
                    }
                }
                if (migratedAttachments != entry.attachments) {
                    hasChanges = true
                    entry.copy(attachments = migratedAttachments)
                } else {
                    entry
                }
            }
            if (migratedCover != lorebook.cover || migratedEntries != lorebook.entries) {
                hasChanges = true
                lorebook.copy(
                    cover = migratedCover,
                    entries = migratedEntries,
                    updatedAt = System.currentTimeMillis(),
                )
            } else {
                lorebook
            }
        }

        if (!hasChanges) {
            return
        }

        val updatedSettings = settings.copy(
            assistants = migratedAssistants,
            lorebooks = migratedLorebooks,
        )
        settingsStore.update(updatedSettings)

        val stillReferencedUploadUrls = collectLegacyUploadUrls(updatedSettings)
        migratedSourceUrls.forEach { sourceUrl ->
            if (sourceUrl in stillReferencedUploadUrls) {
                return@forEach
            }
            val sourceFile = sourceUrl.toOwnedFileOrNull(context) ?: return@forEach
            if (!sourceFile.exists() || chatAttachmentRepository.hasTrackedAttachmentFile(sourceFile)) {
                return@forEach
            }
            sourceFile.delete()
        }
    }

    private suspend fun migrateUploadUrlIfNeeded(
        sourceUrl: String?,
        directory: OwnedFileDirectory,
        migrationCache: MutableMap<Pair<OwnedFileDirectory, String>, String>,
        migratedSourceUrls: MutableSet<String>,
    ): String? {
        val normalizedUrl = sourceUrl?.takeIf { it.isNotBlank() } ?: return null
        val sourceFile = normalizedUrl.toOwnedFileOrNull(context) ?: return null
        if (!sourceFile.exists() || !sourceFile.isInside(context.filesDir.resolve("upload"))) {
            return null
        }
        val cacheKey = directory to normalizedUrl
        migrationCache[cacheKey]?.let { return it }
        val migratedUri = context.importOwnedFile(
            sourceUri = Uri.fromFile(sourceFile),
            directory = directory,
            fileNameHint = sourceFile.name,
            mimeHint = context.getFileMimeType(Uri.fromFile(sourceFile)),
        ) ?: return null
        migratedSourceUrls += normalizedUrl
        return migratedUri.toString().also { migrationCache[cacheKey] = it }
    }

    private fun collectLegacyUploadUrls(settings: Settings): Set<String> {
        return buildSet {
            settings.assistants.forEach { assistant ->
                (assistant.avatar as? Avatar.Image)
                    ?.url
                    ?.takeIf { it.toOwnedFileOrNull(context)?.isInside(context.filesDir.resolve("upload")) == true }
                    ?.let(::add)
                assistant.background
                    ?.takeIf { it.toOwnedFileOrNull(context)?.isInside(context.filesDir.resolve("upload")) == true }
                    ?.let(::add)
            }
            settings.lorebooks.forEach { lorebook ->
                (lorebook.cover as? Avatar.Image)
                    ?.url
                    ?.takeIf { it.toOwnedFileOrNull(context)?.isInside(context.filesDir.resolve("upload")) == true }
                    ?.let(::add)
                lorebook.entries.forEach { entry ->
                    entry.attachments.forEach { attachment ->
                        attachment.url
                            .takeIf { it.toOwnedFileOrNull(context)?.isInside(context.filesDir.resolve("upload")) == true }
                            ?.let(::add)
                    }
                }
            }
        }
    }

    private suspend fun buildCategoryBreakdown(
        trackedAttachmentPaths: Set<String>,
    ): List<StorageCategoryUsage> = withContext(Dispatchers.IO) {
        val uploadDir = context.filesDir.resolve("upload")
        val protectedUploadPaths = trackedAttachmentPaths + collectProtectedUploadPaths(excludeTrackedChatPaths = true)
        val assistantMediaBytes = safeDirectoryBytes(context.getOwnedDirectory(OwnedFileDirectory.AVATAR)) +
            safeDirectoryBytes(context.getOwnedDirectory(OwnedFileDirectory.ASSISTANT_BACKGROUND))
        val lorebookMediaBytes = safeDirectoryBytes(context.getOwnedDirectory(OwnedFileDirectory.LOREBOOK_COVER)) +
            safeDirectoryBytes(context.getOwnedDirectory(OwnedFileDirectory.LOREBOOK_ATTACHMENT))
        val generatedMediaBytes = safeDirectoryBytes(context.filesDir.resolve("images")) +
            safeDirectoryBytes(context.filesDir.resolve("chat_files"))
        val workspacesBytes = safeDirectoryBytes(context.filesDir.resolve("workspaces"))
        val iconsAndFontsBytes = safeDirectoryBytes(context.filesDir.resolve("auto_icons")) +
            safeDirectoryBytes(context.filesDir.resolve("custom_icons")) +
            safeDirectoryBytes(context.filesDir.resolve("custom_fonts"))
        val databaseBytes = safeDirectoryBytes(context.getDatabasePath("rikka_hub").parentFile) +
            safeDirectoryBytes(context.filesDir.parentFile?.resolve("shared_prefs")) +
            safeDirectoryBytes(context.noBackupFilesDir)
        val iconCacheBytes = safeDirectoryBytes(context.filesDir.resolve("icon_cache"))
        val ocrCacheBytes = safeDirectoryBytes(context.cacheDir.resolve("pdf_ocr")) +
            safeFileBytes(context.cacheDir.resolve("ocr_cache.json"))
        val tempBytes = safeDirectoryBytes(context.appTempFolder)
        val cacheBytes = safeDirectoryBytes(
            context.cacheDir,
            excludedRoots = setOf(
                context.cacheDir.resolve("pdf_ocr"),
                context.appTempFolder,
            ),
            excludedFiles = setOf(context.cacheDir.resolve("ocr_cache.json")),
        )
        val codeCacheBytes = safeDirectoryBytes(context.codeCacheDir)
        val otherUploadBytes = safeDirectoryBytes(uploadDir, excludedCanonicalPaths = protectedUploadPaths)

        listOf(
            StorageCategoryUsage(CATEGORY_ASSISTANT_MEDIA, "Assistant media", assistantMediaBytes),
            StorageCategoryUsage(CATEGORY_LOREBOOK_MEDIA, "Lorebook media", lorebookMediaBytes),
            StorageCategoryUsage(CATEGORY_GENERATED_MEDIA, "Generated media", generatedMediaBytes),
            StorageCategoryUsage(CATEGORY_WORKSPACES, "Workspaces", workspacesBytes),
            StorageCategoryUsage(CATEGORY_ICONS_AND_FONTS, "Icons and fonts", iconsAndFontsBytes),
            StorageCategoryUsage(CATEGORY_DATABASES, "Settings and database", databaseBytes),
            StorageCategoryUsage(CATEGORY_ICON_CACHE, "Icon disk cache", iconCacheBytes, clearable = true),
            StorageCategoryUsage(CATEGORY_OCR_CACHE, "OCR and PDF cache", ocrCacheBytes, clearable = true),
            StorageCategoryUsage(CATEGORY_TEMP_FILES, "Temporary files", tempBytes, clearable = true),
            StorageCategoryUsage(CATEGORY_APP_CACHE, "App cache", cacheBytes),
            StorageCategoryUsage(CATEGORY_CODE_CACHE, "Code cache", codeCacheBytes),
            StorageCategoryUsage(CATEGORY_OTHER_UPLOADS, "Other uploads", otherUploadBytes),
        ).filter { it.bytes > 0L }
    }

    private fun queryPackageStats(): AppPackageStorageStats {
        return runCatching {
            val storageManager = context.getSystemService(Context.STORAGE_SERVICE) as? StorageManager
            val statsManager = context.getSystemService(Context.STORAGE_STATS_SERVICE) as? StorageStatsManager
            if (storageManager == null || statsManager == null) {
                return@runCatching AppPackageStorageStats()
            }
            val uuid = storageManager.getUuidForPath(context.filesDir)
            val stats = statsManager.queryStatsForPackage(uuid, context.packageName, Process.myUserHandle())
            AppPackageStorageStats(
                appBytes = stats.appBytes,
                dataBytes = stats.dataBytes,
                cacheBytes = stats.cacheBytes,
            )
        }.getOrElse { error ->
            Log.w(TAG, "Falling back to scanned storage totals", error)
            AppPackageStorageStats()
        }
    }

    private suspend fun collectProtectedUploadPaths(
        excludeTrackedChatPaths: Boolean = false,
    ): Set<String> = withContext(Dispatchers.IO) {
        collectProtectedFilePaths(
            root = context.filesDir.resolve("upload"),
            excludeTrackedChatPaths = excludeTrackedChatPaths,
        )
    }

    private suspend fun collectProtectedFilePaths(
        root: File? = null,
        excludeTrackedChatPaths: Boolean = false,
    ): Set<String> = withContext(Dispatchers.IO) {
        val protectedPaths = mutableSetOf<String>()
        if (!excludeTrackedChatPaths) {
            protectedPaths += chatAttachmentRepository.getTrackedAttachmentFilePaths().filterToRoot(root)
        }
        protectedPaths += collectSettingsReferencedFilePaths(
            settings = settingsStore.settingsFlow.value,
            root = root ?: context.filesDir,
        )
        protectedPaths += collectActiveWebUploadPaths(root = root)
        protectedPaths
    }

    private fun collectSettingsReferencedFilePaths(
        settings: Settings,
        root: File,
    ): Set<String> {
        if (settings.init) {
            return emptySet()
        }
        val element = JsonInstant.encodeToJsonElement(Settings.serializer(), settings)
        return buildSet {
            collectFilePathsFromJson(
                element = element,
                root = root,
                collector = this,
            )
        }
    }

    private fun collectFilePathsFromJson(
        element: JsonElement,
        root: File,
        collector: MutableSet<String>,
    ) {
        when (element) {
            is JsonObject -> element.values.forEach { value ->
                collectFilePathsFromJson(
                    element = value,
                    root = root,
                    collector = collector,
                )
            }

            is JsonArray -> element.forEach { value ->
                collectFilePathsFromJson(
                    element = value,
                    root = root,
                    collector = collector,
                )
            }

            else -> {
                val value = element.jsonPrimitiveOrNull
                    ?.takeIf { it.isString }
                    ?.content
                    ?.takeIf { it.isNotBlank() }
                    ?: return
                val file = value.toOwnedFileOrNull(context) ?: return
                val canonicalFile = runCatching { file.canonicalFile }.getOrNull() ?: return
                if (!canonicalFile.exists() || !canonicalFile.isInside(root)) {
                    return
                }
                collector += runCatching { canonicalFile.canonicalPath }.getOrNull() ?: return
            }
        }
    }

    private fun collectActiveWebUploadPaths(
        root: File? = null,
    ): Set<String> {
        val uploadDir = root ?: context.filesDir.resolve("upload")
        return WebUploadRegistry.listRelativePaths().mapNotNullTo(mutableSetOf()) { relativePath ->
            val file = context.filesDir.resolve(relativePath)
            val canonicalFile = runCatching { file.canonicalFile }.getOrNull() ?: return@mapNotNullTo null
            if (!canonicalFile.exists() || !canonicalFile.isInside(uploadDir)) {
                return@mapNotNullTo null
            }
            runCatching { canonicalFile.canonicalPath }.getOrNull()
        }
    }
}

private fun resolveManagedFileRef(
    value: String,
    context: Context,
): File? {
    val trimmed = value.trim()
    if (trimmed.isBlank()) {
        return null
    }
    return trimmed.toOwnedFileOrNull(context)
        ?: runCatching { File(trimmed) }.getOrNull()
}

private fun Set<String>.filterToRoot(
    root: File?,
): Set<String> {
    if (root == null) {
        return this
    }
    val canonicalRoot = runCatching { root.canonicalFile }.getOrNull() ?: return emptySet()
    return filterTo(mutableSetOf()) { path ->
        val file = runCatching { File(path).canonicalFile }.getOrNull() ?: return@filterTo false
        file.isInside(canonicalRoot)
    }
}

private fun String.toOwnedFileOrNull(context: Context): File? {
    return runCatching { Uri.parse(this) }.getOrNull()?.let(context::resolveStorageFile)
}

private fun Context.resolveStorageFile(uri: Uri): File? {
    return when (uri.scheme) {
        "file" -> runCatching { uri.toFile() }.getOrNull()
        "content" -> resolveOwnedFile(uri)
        else -> null
    }
}

private fun AppPackageStorageStats.withFallbackDataBytes(
    fallbackDataAndCacheBytes: Long,
): AppPackageStorageStats {
    if (appBytes != 0L || dataBytes != 0L || cacheBytes != 0L) {
        return this
    }
    return copy(dataBytes = fallbackDataAndCacheBytes.coerceAtLeast(0L))
}

private fun File.isInside(root: File): Boolean {
    val canonicalRoot = runCatching { root.canonicalFile }.getOrNull() ?: return false
    val canonicalFile = runCatching { canonicalFile }.getOrNull() ?: return false
    val rootPath = canonicalRoot.path
    val filePath = canonicalFile.path
    return filePath == rootPath || filePath.startsWith(rootPath + File.separator)
}

private fun safeDirectoryBytes(
    directory: File?,
    excludedRoots: Set<File> = emptySet(),
    excludedFiles: Set<File> = emptySet(),
    excludedCanonicalPaths: Set<String> = emptySet(),
): Long {
    if (directory == null || !directory.exists()) {
        return 0L
    }
    val normalizedExcludedRoots = excludedRoots.mapNotNullTo(mutableSetOf()) { root ->
        runCatching { root.canonicalPath }.getOrNull()
    }
    val normalizedExcludedFiles = excludedFiles.mapNotNullTo(mutableSetOf()) { file ->
        runCatching { file.canonicalPath }.getOrNull()
    }
    return runCatching {
        directory.walkTopDown()
            .onEnter { candidate ->
                val candidatePath = runCatching { candidate.canonicalPath }.getOrNull() ?: return@onEnter true
                normalizedExcludedRoots.none { excludedRoot ->
                    candidatePath == excludedRoot || candidatePath.startsWith(excludedRoot + File.separator)
                }
            }
            .filter { it.isFile }
            .sumOf { file ->
                val canonicalPath = runCatching { file.canonicalPath }.getOrNull() ?: return@sumOf 0L
                if (canonicalPath in normalizedExcludedFiles || canonicalPath in excludedCanonicalPaths) {
                    0L
                } else {
                    file.length()
                }
            }
    }.getOrDefault(0L)
}

private fun safeFileBytes(file: File?): Long {
    if (file == null || !file.exists() || !file.isFile) {
        return 0L
    }
    return file.length()
}
