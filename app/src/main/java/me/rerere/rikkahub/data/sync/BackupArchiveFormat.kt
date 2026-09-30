package me.rerere.rikkahub.data.sync

import kotlinx.serialization.Serializable
import java.io.File

import me.rerere.rikkahub.R

enum class BackupContentOption(
    val titleRes: Int,
    val descRes: Int,
) {
    CHARACTERS(R.string.backup_content_characters, R.string.backup_content_characters_desc),
    CHATS(R.string.backup_content_chats, R.string.backup_content_chats_desc),
    PROVIDERS(R.string.backup_content_providers, R.string.backup_content_providers_desc),
    API_KEYS(R.string.backup_content_api_keys, R.string.backup_content_api_keys_desc),
    WORKSPACES(R.string.backup_content_workspaces, R.string.backup_content_workspaces_desc),
    SKILLS(R.string.backup_content_skills, R.string.backup_content_skills_desc),
    LOREBOOKS(R.string.backup_content_lorebooks, R.string.backup_content_lorebooks_desc),
    SETTINGS(R.string.backup_content_settings, R.string.backup_content_settings_desc),
}

internal object BackupArchiveFormat {
    const val CURRENT_FORMAT_VERSION = 2
    const val SETTINGS_ENTRY = "settings.json"
    const val MANIFEST_ENTRY = "backup_manifest.json"
    const val PREFS_DIR = "prefs"

    const val LEGACY_DB_ENTRY = "rikka_hub.db"
    const val DB_ENTRY = "rikka_hub"
    const val WAL_ENTRY = "rikka_hub-wal"
    const val SHM_ENTRY = "rikka_hub-shm"

    const val WORKSPACES_DIR = "workspaces"

    /**
     * Subdirectories of each workspace root that are intentionally excluded from backups:
     * the on-device Linux rootfs (large and reinstallable, and whose symlinks/permissions
     * cannot survive a zip round-trip) and its scratch temp space.
     */
    val WORKSPACE_EXCLUDED_SUBDIRS = setOf("linux", "tmp")

    /**
     * Transient compiler and package-manager cache subdirectories that are not user data
     * and should not bloat workspace backups.
     */
    val WORKSPACE_CACHE_DIRS = setOf("__pycache__", ".pytest_cache", ".mypy_cache", ".cache")

    val MANAGED_FILE_DIRS = listOf(
        "upload",
        "avatars",
        "assistant_backgrounds",
        "custom_icons",
        "custom_fonts",
        "images",
        "chat_files",
        "lorebook_covers",
        "lorebook_attachments",
        "skills",
        "tool_outputs",
        WORKSPACES_DIR,
    )

    val PORTABLE_SHARED_PREF_STORES = listOf(
        "rikkahub.preferences",
        "spontaneous_messaging_state",
    )

    fun normalizeEntryName(entryName: String): String {
        return entryName.replace('\\', '/').trimStart('/')
    }

    fun isDatabaseEntry(entryName: String): Boolean {
        return when (normalizeEntryName(entryName)) {
            LEGACY_DB_ENTRY, DB_ENTRY, WAL_ENTRY, SHM_ENTRY -> true
            else -> false
        }
    }

    fun managedDirForEntry(entryName: String): String? {
        val normalized = normalizeEntryName(entryName)
        val topLevel = normalized.substringBefore('/', normalized)
        return topLevel.takeIf { MANAGED_FILE_DIRS.contains(it) }
    }

    fun prefEntryName(storeName: String): String {
        return "$PREFS_DIR/$storeName.json"
    }

    /**
     * For the [WORKSPACES_DIR] archive tree, decides whether a path relative to the workspaces
     * root (e.g. `<workspaceId>/linux/...`) should be excluded from the backup.
     * Only the `<workspaceId>/files/...` tree is included, while compiler/runtime caches
     * like `__pycache__` and `.cache` are skipped to ensure compact, lossless user exports.
     */
    fun isExcludedWorkspacePath(relativePath: String, isDirectory: Boolean = false): Boolean {
        val parts = relativePath.split('/')
        // Any path directly under a workspace root must be in "files"
        // E.g. <workspaceId>/linux or <workspaceId>/tmp is excluded.
        if (parts.size >= 2 && parts[1] != "files") {
            return true
        }
        val fileName = parts.last()
        if (fileName in WORKSPACE_CACHE_DIRS) {
            return true
        }
        if (!isDirectory && (fileName.endsWith(".pyc") || fileName.endsWith(".pyo"))) {
            return true
        }
        return false
    }
}

@Serializable
data class WorkspaceExportMetadata(
    val id: String,
    val name: String,
    val root: String,
    val hasRootfs: Boolean,
    val hasPython: Boolean,
    val rootfsUrl: String? = null,
)

@Serializable
data class BackupManifest(
    val formatVersion: Int = BackupArchiveFormat.CURRENT_FORMAT_VERSION,
    val includesDatabase: Boolean = false,
    val includesFiles: Boolean = false,
    val managedFileDirs: List<String> = emptyList(),
    val sharedPrefsStores: List<String> = emptyList(),
    val selectedContent: List<String> = emptyList(),
    val workspacesMetadata: List<WorkspaceExportMetadata> = emptyList(),
)

internal data class DirectoryArchiveEntry(
    val source: File,
    val entryName: String,
    val isDirectory: Boolean,
)

internal fun enumerateDirectoryEntries(
    directory: File,
    archiveRoot: String,
    skip: (relativePath: String, isDirectory: Boolean) -> Boolean = { _, _ -> false },
): List<DirectoryArchiveEntry> {
    if (!directory.exists()) {
        return emptyList()
    }
    val normalizedRoot = archiveRoot.trim('/').replace('\\', '/')
    return directory.walkTopDown()
        .onEnter { dir ->
            // Prevent descending into (and following symlinks within) excluded subtrees, e.g.
            // the reinstallable workspace Linux rootfs — huge, and its symlinks/permissions
            // cannot survive a zip round-trip anyway.
            val relative = dir.relativeTo(directory).invariantSeparatorsPath
            relative.isEmpty() || !skip(relative, true)
        }
        .filter { it.exists() }
        .mapNotNull { file ->
            val relative = file.relativeTo(directory).invariantSeparatorsPath
            if (relative.isNotEmpty() && skip(relative, file.isDirectory)) {
                return@mapNotNull null
            }
            val entryName = when {
                relative.isEmpty() && file.isDirectory -> "$normalizedRoot/"
                relative.isEmpty() -> normalizedRoot
                file.isDirectory -> "$normalizedRoot/$relative/"
                else -> "$normalizedRoot/$relative"
            }
            DirectoryArchiveEntry(
                source = file,
                entryName = entryName,
                isDirectory = file.isDirectory,
            )
        }.toList()
}

internal fun safeZipDestination(root: File, entryName: String): File {
    val normalized = BackupArchiveFormat.normalizeEntryName(entryName)
    val target = File(root, normalized).canonicalFile
    val canonicalRoot = root.canonicalFile
    val rootPath = canonicalRoot.path
    val targetPath = target.path
    val insideRoot = targetPath == rootPath || targetPath.startsWith(rootPath + File.separator)
    require(insideRoot) { "Invalid zip entry path: $entryName" }
    return target
}
