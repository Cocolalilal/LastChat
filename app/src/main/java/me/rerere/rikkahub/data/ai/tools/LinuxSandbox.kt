package me.rerere.rikkahub.data.ai.tools

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import kotlin.uuid.Uuid

private val LINUX_IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp")

class LinuxSandbox(private val context: Context) {
    private val baseDir = File(context.filesDir, "linux_env")
    private val workspacesDir = File(baseDir, "workspaces")

    init {
        workspacesDir.mkdirs()
    }

    fun getConversationDir(conversationId: Uuid): File {
        return File(workspacesDir, conversationId.toString()).apply { mkdirs() }
    }

    fun importFile(conversationId: Uuid, sourceUri: Uri, filename: String): String {
        val dir = getConversationDir(conversationId)
        val destFile = resolveInside(dir, filename)
        destFile.parentFile?.mkdirs()

        context.contentResolver.openInputStream(sourceUri)?.use { input ->
            destFile.outputStream().use { output ->
                input.copyTo(output)
            }
        } ?: throw java.io.FileNotFoundException("Could not open input stream for URI: $sourceUri")

        return destFile.absolutePath
    }

    fun listFiles(conversationId: Uuid): List<FileInfo> {
        val dir = getConversationDir(conversationId)
        return dir.walkTopDown()
            .filter { file -> file.isFile }
            .map { file ->
                FileInfo(
                    name = file.relativeTo(dir).invariantSeparatorsPath,
                    size = file.length(),
                    isImage = file.extension.lowercase() in LINUX_IMAGE_EXTENSIONS,
                    mimeType = getMimeType(file.extension),
                )
            }
            .toList()
    }

    fun readTextFile(conversationId: Uuid, relativePath: String): String {
        val file = resolveInside(getConversationDir(conversationId), relativePath)
        require(file.isFile) { "File does not exist: $relativePath" }
        return file.readText()
    }

    fun writeTextFile(conversationId: Uuid, relativePath: String, content: String) {
        val file = resolveInside(getConversationDir(conversationId), relativePath)
        file.parentFile?.mkdirs()
        file.writeText(content)
    }

    fun deleteFile(conversationId: Uuid, relativePath: String): Boolean {
        val file = resolveInside(getConversationDir(conversationId), relativePath)
        return file.exists() && file.isFile && file.delete()
    }

    fun cleanupConversation(conversationId: Uuid) {
        val dir = File(workspacesDir, conversationId.toString())
        if (dir.exists()) {
            dir.deleteRecursively()
        }
    }

    fun getFileUri(conversationId: Uuid, relativePath: String): Uri {
        val file = resolveInside(getConversationDir(conversationId), relativePath)
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
    }

    private fun resolveInside(root: File, relativePath: String): File {
        require(relativePath.isNotBlank()) { "Path is required" }
        require(!File(relativePath).isAbsolute) { "Absolute paths are not allowed" }
        val canonicalRoot = root.canonicalFile
        val target = File(canonicalRoot, relativePath).canonicalFile
        val rootPath = canonicalRoot.path
        val targetPath = target.path
        val insideRoot = targetPath == rootPath || targetPath.startsWith(rootPath + File.separator)
        require(insideRoot) { "Access denied: path outside Linux workspace" }
        return target
    }

    private fun getMimeType(extension: String): String {
        return when (extension.lowercase()) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "bmp" -> "image/bmp"
            "pdf" -> "application/pdf"
            "txt" -> "text/plain"
            "json" -> "application/json"
            "csv" -> "text/csv"
            "html", "htm" -> "text/html"
            "xml" -> "text/xml"
            "zip" -> "application/zip"
            "mp3" -> "audio/mpeg"
            "mp4" -> "video/mp4"
            "wav" -> "audio/wav"
            "js" -> "text/javascript"
            "py" -> "text/x-python"
            "kt" -> "text/x-kotlin"
            "java" -> "text/x-java-source"
            else -> "application/octet-stream"
        }
    }

    data class FileInfo(
        val name: String,
        val size: Long,
        val isImage: Boolean,
        val mimeType: String,
    )
}
