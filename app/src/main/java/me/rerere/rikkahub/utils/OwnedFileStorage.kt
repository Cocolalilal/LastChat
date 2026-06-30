package me.rerere.rikkahub.utils

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.core.net.toFile
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.buffer
import okio.sink
import java.io.File
import kotlin.uuid.Uuid

enum class OwnedFileDirectory(
    val folderName: String,
) {
    AVATAR("avatars"),
    ASSISTANT_BACKGROUND("assistant_backgrounds"),
    LOREBOOK_COVER("lorebook_covers"),
    LOREBOOK_ATTACHMENT("lorebook_attachments"),
}

suspend fun Context.importOwnedFile(
    sourceUri: Uri,
    directory: OwnedFileDirectory,
    fileNameHint: String? = null,
    mimeHint: String? = null,
): Uri? = withContext(Dispatchers.IO) {
    val inputStream = openOwnedUriInputStream(sourceUri) ?: return@withContext null
    val originalName = fileNameHint ?: getFileNameFromUri(sourceUri)
    val mimeType = mimeHint ?: getFileMimeType(sourceUri)
    val targetDir = getOwnedDirectory(directory)
    val targetFile = targetDir.resolve(buildOwnedFileName(originalName, mimeType))
    inputStream.use { input ->
        targetFile.sink().buffer().outputStream().use { output ->
            input.copyTo(output)
        }
    }
    targetFile.toUri()
}

suspend fun Context.importOwnedFiles(
    uris: List<Uri>,
    directory: OwnedFileDirectory,
): List<Uri> = withContext(Dispatchers.IO) {
    uris.mapNotNull { uri ->
        importOwnedFile(
            sourceUri = uri,
            directory = directory,
        )
    }
}

suspend fun Context.deleteOwnedFileIfPresent(
    uri: Uri,
    directory: OwnedFileDirectory,
): Boolean = withContext(Dispatchers.IO) {
    val file = resolveOwnedFile(uri) ?: return@withContext false
    val canonicalDirectory = getOwnedDirectory(directory).canonicalFile
    val canonicalFile = runCatching { file.canonicalFile }.getOrNull() ?: return@withContext false
    val directoryPath = canonicalDirectory.path
    val filePath = canonicalFile.path
    if (filePath != directoryPath && !filePath.startsWith(directoryPath + File.separator)) {
        return@withContext false
    }
    canonicalFile.delete()
}

fun Context.getOwnedDirectory(directory: OwnedFileDirectory): File {
    return filesDir.resolve(directory.folderName).apply { mkdirs() }
}

fun Context.resolveOwnedFile(uri: Uri): File? {
    return when (uri.scheme) {
        "file" -> runCatching { uri.toFile() }.getOrNull()
        "content" -> resolveAppOwnedFileProviderFile(
            authority = uri.authority,
            encodedPath = uri.encodedPath,
            expectedAuthority = "$packageName.fileprovider",
            cacheDir = cacheDir,
            filesDir = filesDir,
            externalFilesDir = getExternalFilesDir(null),
        )

        else -> null
    }
}

private fun buildOwnedFileName(
    originalName: String?,
    mimeType: String?,
): String {
    val extension = originalName
        ?.substringAfterLast('.', "")
        ?.takeIf { it.isNotBlank() && it != originalName }
        ?.lowercase()
        ?: mimeType
            ?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) }
            ?.takeIf { it.isNotBlank() }
            ?.lowercase()
    val safeBaseName = sanitizeOwnedBaseName(originalName?.substringBeforeLast('.', originalName))
    return buildString {
        append(safeBaseName)
        append('-')
        append(Uuid.random())
        extension?.let {
            append('.')
            append(it)
        }
    }
}

private fun sanitizeOwnedBaseName(rawName: String?): String {
    val cleaned = rawName
        ?.replace(Regex("[^A-Za-z0-9._-]+"), "-")
        ?.trim('-', '.', '_')
        ?.take(48)
    return cleaned?.takeIf { it.isNotBlank() } ?: "file"
}
