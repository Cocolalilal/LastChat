package me.rerere.rikkahub.web

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.core.net.toFile
import androidx.core.net.toUri
import io.ktor.http.ContentType
import me.rerere.common.http.urlEncode
import java.io.Closeable
import java.io.File
import java.io.InputStream

data class WebMediaContent(
    val inputStream: InputStream,
    val contentType: ContentType,
    val fileName: String?,
) : Closeable {
    override fun close() {
        inputStream.close()
    }
}

fun String.toWebMediaUrl(context: Context): String? {
    if (startsWith("data:")) return this
    if (startsWith("http://") || startsWith("https://")) return this

    val uri = runCatching { toUri() }.getOrNull() ?: return null
    if (!uri.isAllowedWebMediaUri(context)) return null

    return buildString {
        append("/api/files/content?uri=")
        append(this@toWebMediaUrl.urlEncode(spaceAsPlus = true))
    }
}

fun Uri.isAllowedWebMediaUri(context: Context): Boolean {
    return when (scheme?.lowercase()) {
        "file" -> runCatching {
            val file = toFile().canonicalFile
            file.isInside(context.filesDir) || file.isInside(context.cacheDir)
        }.getOrDefault(false)

        "content" -> !authority.isNullOrBlank()

        "android.resource" -> {
            val authorityValue = authority ?: return false
            authorityValue == context.packageName
        }

        else -> false
    }
}

fun openAllowedWebMedia(context: Context, uri: Uri): WebMediaContent? {
    if (!uri.isAllowedWebMediaUri(context)) return null

    return when (uri.scheme?.lowercase()) {
        "file" -> {
            val file = runCatching { uri.toFile().canonicalFile }.getOrNull() ?: return null
            val mimeType = guessWebMediaMimeTypeFromName(file.name)
                ?.let { ContentType.parse(it) }
                ?: ContentType.Application.OctetStream
            if (!file.exists()) return null
            WebMediaContent(
                inputStream = file.inputStream(),
                contentType = mimeType,
                fileName = file.name
            )
        }

        "content" -> {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val mimeType = context.contentResolver.getType(uri)
                ?.let { ContentType.parse(it) }
                ?: ContentType.Application.OctetStream
            val fileName = context.contentResolver.query(
                uri,
                arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getString(0)
                } else {
                    null
                }
            }
            WebMediaContent(
                inputStream = inputStream,
                contentType = mimeType,
                fileName = fileName
            )
        }

        "android.resource" -> {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val mimeType = context.contentResolver.getType(uri)
                ?.let { ContentType.parse(it) }
                ?: guessWebMediaMimeTypeFromName(uri.lastPathSegment.orEmpty())
                    ?.let { ContentType.parse(it) }
                ?: ContentType.Application.OctetStream
            WebMediaContent(
                inputStream = inputStream,
                contentType = mimeType,
                fileName = uri.lastPathSegment
            )
        }

        else -> null
    }
}

internal fun guessWebMediaMimeTypeFromName(name: String): String? {
    val extension = name
        .substringBeforeLast('?')
        .substringBeforeLast('#')
        .substringAfterLast('.', missingDelimiterValue = "")
        .lowercase()
        .takeIf { it.isNotBlank() }
        ?: return null

    return when (extension) {
        "avif" -> "image/avif"
        "bmp" -> "image/bmp"
        "css" -> "text/css"
        "gif" -> "image/gif"
        "htm", "html" -> "text/html"
        "ico" -> "image/x-icon"
        "jpeg", "jpg" -> "image/jpeg"
        "js", "mjs" -> "application/javascript"
        "json" -> "application/json"
        "m4a" -> "audio/mp4"
        "mp3" -> "audio/mpeg"
        "mp4" -> "video/mp4"
        "pdf" -> "application/pdf"
        "png" -> "image/png"
        "svg" -> "image/svg+xml"
        "txt" -> "text/plain"
        "wav" -> "audio/wav"
        "webm" -> "video/webm"
        "webp" -> "image/webp"
        else -> MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
    }
}

private fun File.isInside(parent: File): Boolean {
    val canonicalParent = parent.canonicalFile
    val canonicalSelf = canonicalFile
    return canonicalSelf.path == canonicalParent.path ||
        canonicalSelf.path.startsWith(canonicalParent.path + File.separator)
}
