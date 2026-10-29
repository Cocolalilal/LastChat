package me.rerere.rikkahub.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

data class SavedArtifact(
    val id: String,
    val title: String,
    val timestamp: Long,
    val htmlFile: File,
    val imageFile: File?,
    val sizeBytes: Long,
)

object ArtifactStorageManager {
    private const val TAG = "ArtifactStorageManager"
    const val DIR_NAME = "artifacts"

    fun getArtifactsDir(context: Context): File {
        val dir = File(context.filesDir, DIR_NAME)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    suspend fun saveArtifact(
        context: Context,
        code: String,
        bitmap: Bitmap? = null
    ): SavedArtifact? = withContext(Dispatchers.IO) {
        runCatching {
            val dir = getArtifactsDir(context)
            val hash = hashString(code)
            val title = extractTitle(code)
            val htmlFile = File(dir, "$hash.html")
            if (!htmlFile.exists() || htmlFile.length() == 0L) {
                htmlFile.writeText(code)
            }
            val imageFile = File(dir, "$hash.png")
            if (bitmap != null && (!imageFile.exists() || imageFile.length() == 0L)) {
                imageFile.outputStream().use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
            }
            val totalBytes = htmlFile.length() + (if (imageFile.exists()) imageFile.length() else 0L)
            SavedArtifact(
                id = hash,
                title = title,
                timestamp = htmlFile.lastModified(),
                htmlFile = htmlFile,
                imageFile = if (imageFile.exists()) imageFile else null,
                sizeBytes = totalBytes
            )
        }.onFailure {
            Log.e(TAG, "Failed to save artifact", it)
        }.getOrNull()
    }

    suspend fun getSavedArtifacts(context: Context): List<SavedArtifact> = withContext(Dispatchers.IO) {
        runCatching {
            val dir = getArtifactsDir(context)
            val htmlFiles = dir.listFiles { file -> file.extension == "html" } ?: emptyArray()
            htmlFiles.map { htmlFile ->
                val id = htmlFile.nameWithoutExtension
                val imageFile = File(dir, "$id.png").takeIf { it.exists() }
                val code = runCatching { htmlFile.readText() }.getOrDefault("")
                val title = extractTitle(code)
                val totalBytes = htmlFile.length() + (imageFile?.length() ?: 0L)
                SavedArtifact(
                    id = id,
                    title = title,
                    timestamp = htmlFile.lastModified(),
                    htmlFile = htmlFile,
                    imageFile = imageFile,
                    sizeBytes = totalBytes
                )
            }.sortedByDescending { it.timestamp }
        }.getOrDefault(emptyList())
    }

    suspend fun deleteArtifact(context: Context, id: String): Boolean = withContext(Dispatchers.IO) {
        val dir = getArtifactsDir(context)
        val htmlFile = File(dir, "$id.html")
        val imageFile = File(dir, "$id.png")
        var deleted = false
        if (htmlFile.exists()) deleted = htmlFile.delete() || deleted
        if (imageFile.exists()) deleted = imageFile.delete() || deleted
        deleted
    }

    suspend fun clearAll(context: Context): Boolean = withContext(Dispatchers.IO) {
        val dir = getArtifactsDir(context)
        dir.listFiles()?.forEach { it.delete() }
        true
    }

    fun getTotalSizeBytes(context: Context): Long {
        val dir = File(context.filesDir, DIR_NAME)
        if (!dir.exists()) return 0L
        return dir.listFiles()?.sumOf { it.length() } ?: 0L
    }

    private fun extractTitle(html: String): String {
        val titleMatch = Regex("<title>([^<]+)</title>", RegexOption.IGNORE_CASE).find(html)
        if (titleMatch != null) {
            val t = titleMatch.groupValues[1].trim()
            if (t.isNotBlank()) return t
        }
        val headerMatch = Regex("<h[1-3][^>]*>([^<]+)</h[1-3]>", RegexOption.IGNORE_CASE).find(html)
        if (headerMatch != null) {
            val t = headerMatch.groupValues[1].trim()
            if (t.isNotBlank()) return t
        }
        val textSnippet = html.replace(Regex("<[^>]*>"), " ")
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }
            .take(4)
            .joinToString(" ")
        return textSnippet.ifBlank { "In-World Visual" }
    }

    private fun hashString(input: String): String {
        val bytes = MessageDigest.getInstance("MD5").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }.take(16)
    }
}
