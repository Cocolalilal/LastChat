package me.rerere.rikkahub.utils

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import androidx.core.net.toFile
import androidx.core.net.toUri
import java.io.File

/**
 * A local / app-owned file the user can Open, Share, Save to Downloads, or Preview.
 */
data class ResolvedLocalFile(
    val uri: Uri,
    val fileName: String,
    val mimeType: String,
    val localFile: File? = null,
    val sizeBytes: Long? = null,
)

private val IMAGE_EXTENSIONS = setOf(
    "png", "jpg", "jpeg", "webp", "gif", "bmp", "heic", "heif", "avif",
)

private val TEXT_EXTENSIONS = setOf(
    "txt", "md", "markdown", "json", "xml", "html", "htm", "css", "js", "ts", "tsx",
    "jsx", "kt", "kts", "java", "py", "sh", "bash", "zsh", "yml", "yaml", "toml",
    "csv", "tsv", "log", "ini", "conf", "cfg", "c", "cpp", "h", "hpp", "rs", "go",
    "swift", "sql", "gradle", "properties", "gitignore", "dockerfile", "makefile",
    "r", "rb", "php", "lua", "pl", "scm", "clj", "edn", "svg",
)

fun isRemoteLinkTarget(target: String): Boolean {
    val lower = target.trim().lowercase()
    return lower.startsWith("http://") ||
        lower.startsWith("https://") ||
        lower.startsWith("mailto:")
}

/** True when [target] should open the in-app file action sheet instead of a browser. */
fun looksLikeLocalFileTarget(target: String): Boolean {
    val trimmed = target.trim()
    if (trimmed.isEmpty() || isRemoteLinkTarget(trimmed)) return false
    return trimmed.startsWith("content://", ignoreCase = true) ||
        trimmed.startsWith("file://", ignoreCase = true) ||
        trimmed.startsWith("/workspace/") ||
        trimmed == "/workspace" ||
        (trimmed.startsWith("/") && !trimmed.startsWith("//"))
}

fun guessMimeFromFileName(fileName: String): String {
    val ext = fileName.substringAfterLast('.', missingDelimiterValue = "")
        .lowercase()
        .takeIf { it.isNotBlank() }
        ?: return "application/octet-stream"
    return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
}

fun isImageFileName(fileName: String): Boolean {
    val ext = fileName.substringAfterLast('.', missingDelimiterValue = "").lowercase()
    return ext in IMAGE_EXTENSIONS
}

fun ResolvedLocalFile.isImage(): Boolean =
    mimeType.startsWith("image/") || isImageFileName(fileName)

fun ResolvedLocalFile.isTextPreviewable(): Boolean {
    if (mimeType.startsWith("text/")) return true
    if (mimeType in setOf(
            "application/json",
            "application/xml",
            "application/javascript",
            "application/x-javascript",
            "application/yaml",
            "application/x-yaml",
            "application/toml",
        )
    ) {
        return true
    }
    val ext = fileName.substringAfterLast('.', missingDelimiterValue = "").lowercase()
    return ext in TEXT_EXTENSIONS
}

fun ResolvedLocalFile.canPreviewInApp(): Boolean = isImage() || isTextPreviewable()

/**
 * Resolve a path under `filesDir/workspaces/{workspaceId}/{areaSubdir}/...`
 * with path-traversal protection. Returns null if the path escapes or is not a file.
 */
fun resolveWorkspaceAreaFile(
    appFilesDir: File,
    workspaceId: String,
    relativeOrWorkspacePath: String,
    areaSubdir: String = "files",
): File? {
    if (workspaceId.isBlank()) return null
    if (workspaceId.contains('/') || workspaceId.contains('\\') || workspaceId.contains("..")) {
        return null
    }
    if (areaSubdir.contains('/') || areaSubdir.contains('\\') || areaSubdir.contains("..")) {
        return null
    }
    if (relativeOrWorkspacePath.contains('\u0000')) return null

    val relative = relativeOrWorkspacePath
        .removePrefix("/workspace/")
        .removePrefix("/workspace")
        .trimStart('/')
        .replace('\\', '/')

    // Reject obvious traversal segments before canonicalization
    if (relative.split('/').any { it == ".." }) return null

    val base = File(appFilesDir, "workspaces/$workspaceId/$areaSubdir").canonicalFile
    val candidate = if (relative.isEmpty()) {
        base
    } else {
        File(base, relative).canonicalFile
    }
    if (candidate.path != base.path && !candidate.path.startsWith(base.path + File.separator)) {
        return null
    }
    return candidate.takeIf { it.isFile }
}

fun Context.fileProviderUri(file: File): Uri =
    FileProvider.getUriForFile(this, "$packageName.fileprovider", file)

private fun Context.isUnderAppStorage(file: File): Boolean {
    val paths = listOfNotNull(
        filesDir.canonicalFile,
        cacheDir.canonicalFile,
        getExternalFilesDir(null)?.canonicalFile,
    )
    val canonical = runCatching { file.canonicalFile }.getOrNull() ?: return false
    return paths.any { root ->
        canonical.path == root.path || canonical.path.startsWith(root.path + File.separator)
    }
}

private fun Context.toResolvedLocalFile(
    file: File,
    displayNameHint: String?,
    mimeHint: String?,
): ResolvedLocalFile {
    val name = displayNameHint?.takeIf { it.isNotBlank() && !it.contains('/') }
        ?: file.name
    val mime = mimeHint?.takeIf { it.isNotBlank() }
        ?: guessMimeFromFileName(name)
    val shareUri = runCatching { fileProviderUri(file) }.getOrElse { Uri.fromFile(file) }
    return ResolvedLocalFile(
        uri = shareUri,
        fileName = name,
        mimeType = mime,
        localFile = file,
        sizeBytes = file.length(),
    )
}

/**
 * Resolve an assistant/markdown/document target into a readable local file reference.
 *
 * Supports:
 * - `content://…`
 * - `file://…` under app storage
 * - `/workspace/…` when [workspaceId] is set (maps to workspaces/{id}/files/…)
 * - bare relative names under the workspace files root when [workspaceId] is set
 */
fun Context.resolveLocalFileTarget(
    target: String,
    workspaceId: String? = null,
    displayNameHint: String? = null,
    mimeHint: String? = null,
): ResolvedLocalFile? {
    val trimmed = target.trim()
    if (trimmed.isEmpty() || isRemoteLinkTarget(trimmed)) return null

    return when {
        trimmed.startsWith("content://", ignoreCase = true) -> {
            val uri = trimmed.toUri()
            val name = displayNameHint?.takeIf { it.isNotBlank() && !it.contains('/') }
                ?: getFileNameFromUri(uri)
                ?: uri.lastPathSegment?.substringAfterLast('/')
                ?: "file"
            val mime = mimeHint?.takeIf { it.isNotBlank() }
                ?: getFileMimeType(uri)
                ?: guessMimeFromFileName(name)
            ResolvedLocalFile(uri = uri, fileName = name, mimeType = mime)
        }

        trimmed.startsWith("file://", ignoreCase = true) -> {
            val file = runCatching { trimmed.toUri().toFile() }.getOrNull() ?: return null
            val canonical = runCatching { file.canonicalFile }.getOrNull() ?: return null
            if (!canonical.isFile) return null
            if (!isUnderAppStorage(canonical)) return null
            toResolvedLocalFile(canonical, displayNameHint, mimeHint)
        }

        trimmed.startsWith("/workspace/") || trimmed == "/workspace" -> {
            val id = workspaceId ?: return null
            val file = resolveWorkspaceAreaFile(filesDir, id, trimmed) ?: return null
            toResolvedLocalFile(file, displayNameHint, mimeHint)
        }

        trimmed.startsWith("/") -> {
            val file = File(trimmed)
            val canonical = runCatching { file.canonicalFile }.getOrNull() ?: return null
            if (!canonical.isFile || !isUnderAppStorage(canonical)) return null
            toResolvedLocalFile(canonical, displayNameHint, mimeHint)
        }

        workspaceId != null -> {
            val file = resolveWorkspaceAreaFile(filesDir, workspaceId, trimmed) ?: return null
            toResolvedLocalFile(file, displayNameHint, mimeHint)
        }

        else -> null
    }
}

fun Context.resolveWorkspaceEntry(
    workspaceRoot: String,
    areaSubdir: String,
    relativePath: String,
    displayName: String? = null,
    mimeHint: String? = null,
): ResolvedLocalFile? {
    val file = resolveWorkspaceAreaFile(
        appFilesDir = filesDir,
        workspaceId = workspaceRoot,
        relativeOrWorkspacePath = relativePath,
        areaSubdir = areaSubdir,
    ) ?: return null
    return toResolvedLocalFile(file, displayName ?: file.name, mimeHint)
}

fun formatFileSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return "%.1f KB".format(kb)
    val mb = kb / 1024.0
    if (mb < 1024) return "%.1f MB".format(mb)
    val gb = mb / 1024.0
    return "%.2f GB".format(gb)
}
