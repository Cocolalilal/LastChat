package me.rerere.rikkahub.web

import android.content.Context
import android.webkit.MimeTypeMap
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.uuid.Uuid

data class WebUploadedFileRecord(
    val id: Long,
    val relativePath: String,
    val uri: String,
    val fileName: String,
    val mime: String,
    val size: Long,
)

@OptIn(ExperimentalAtomicApi::class)
object WebUploadRegistry {
    private const val UPLOAD_DIR = "upload"

    private val nextId = AtomicLong(1L)
    private val lock = Any()
    private val recordsById = mutableMapOf<Long, WebUploadedFileRecord>()
    private val recordsByPath = mutableMapOf<String, WebUploadedFileRecord>()

    suspend fun saveUpload(
        context: Context,
        originalFileName: String,
        mimeType: String,
        bytes: ByteArray,
    ): WebUploadedFileRecord = withContext(Dispatchers.IO) {
        val displayName = sanitizeDisplayName(originalFileName)
        val extension = guessExtension(displayName, mimeType)
        val storedName = buildString {
            append(Uuid.random())
            if (extension.isNotBlank()) {
                append('.')
                append(extension)
            }
        }

        val uploadDir = context.filesDir.resolve(UPLOAD_DIR).apply {
            if (!exists()) {
                mkdirs()
            }
        }
        val file = uploadDir.resolve(storedName)
        file.writeBytes(bytes)

        val relativePath = "$UPLOAD_DIR/$storedName"
        val record = WebUploadedFileRecord(
            id = nextId.fetchAndAdd(1L),
            relativePath = relativePath,
            uri = file.toUri().toString(),
            fileName = displayName,
            mime = mimeType.ifBlank { "application/octet-stream" },
            size = file.length(),
        )

        locked {
            recordsById[record.id] = record
            recordsByPath[record.relativePath] = record
        }
        record
    }

    fun get(id: Long): WebUploadedFileRecord? = locked {
        recordsById[id]
    }

    fun getByRelativePath(relativePath: String): WebUploadedFileRecord? = locked {
        recordsByPath[relativePath]
    }

    fun listRelativePaths(): Set<String> = locked {
        recordsByPath.keys.toSet()
    }

    suspend fun delete(context: Context, id: Long): Boolean = withContext(Dispatchers.IO) {
        val record = locked {
            val removed = recordsById.remove(id) ?: return@locked null
            recordsByPath.remove(removed.relativePath)
            removed
        } ?: return@withContext false

        val file = context.filesDir.resolve(record.relativePath)
        if (file.exists()) {
            file.delete()
        } else {
            true
        }
    }

    private fun sanitizeDisplayName(fileName: String): String {
        return fileName
            .substringAfterLast('/')
            .substringAfterLast('\\')
            .replace(Regex("[\\u0000-\\u001F\\u007F]"), "")
            .ifBlank { "file" }
    }

    private fun guessExtension(fileName: String, mimeType: String): String {
        val rawExtension = fileName.substringAfterLast('.', missingDelimiterValue = "")
            .trim()
            .lowercase()
        if (rawExtension.matches(Regex("[a-z0-9]{1,10}"))) {
            return rawExtension
        }

        return MimeTypeMap.getSingleton()
            .getExtensionFromMimeType(mimeType.substringBefore(';').trim())
            ?.trim()
            ?.lowercase()
            ?.takeIf { it.matches(Regex("[a-z0-9]{1,10}")) }
            .orEmpty()
    }

    private inline fun <T> locked(block: () -> T): T = synchronized(lock, block)
}
