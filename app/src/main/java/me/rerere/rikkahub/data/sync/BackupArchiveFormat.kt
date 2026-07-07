package me.rerere.rikkahub.data.sync

import kotlinx.serialization.Serializable
import java.io.File

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
        WORKSPACES_DIR,
        "model_catalog",
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
     */
    fun isExcludedWorkspacePath(relativePath: String): Boolean {
        val parts = relativePath.split('/')
        return parts.size >= 2 && parts[1] in WORKSPACE_EXCLUDED_SUBDIRS
    }
}

@Serializable
data class BackupManifest(
    val formatVersion: Int = BackupArchiveFormat.CURRENT_FORMAT_VERSION,
    val includesDatabase: Boolean = false,
    val includesFiles: Boolean = false,
    val managedFileDirs: List<String> = emptyList(),
    val sharedPrefsStores: List<String> = emptyList(),
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
