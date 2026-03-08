package me.rerere.rikkahub.web

import android.content.Context
import android.net.Uri
import androidx.core.net.toFile
import androidx.core.net.toUri
import io.ktor.http.ContentType
import java.io.Closeable
import java.io.File
import java.io.InputStream
import java.net.URLConnection
import java.net.URLEncoder

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
        append(URLEncoder.encode(this@toWebMediaUrl, Charsets.UTF_8.name()))
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
            val mimeType = URLConnection.guessContentTypeFromName(file.name)
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
                ?: URLConnection.guessContentTypeFromName(uri.lastPathSegment.orEmpty())
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

private fun File.isInside(parent: File): Boolean {
    val canonicalParent = parent.canonicalFile
    val canonicalSelf = canonicalFile
    return canonicalSelf.path == canonicalParent.path ||
        canonicalSelf.path.startsWith(canonicalParent.path + File.separator)
}
