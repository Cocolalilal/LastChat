package me.rerere.common.platform.android

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.rerere.common.platform.PlatformFileStore
import okio.buffer
import okio.sink
import okio.source
import java.io.File
import java.io.IOException

class AndroidFileStore(
    private val rootDir: File
) : PlatformFileStore {
    override suspend fun readBytes(path: String): ByteArray? = withContext(Dispatchers.IO) {
        val file = resolvePath(path)
        if (file.exists()) {
            file.source().buffer().use { source ->
                source.readByteArray()
            }
        } else {
            null
        }
    }

    override suspend fun writeBytes(path: String, bytes: ByteArray): Unit = withContext(Dispatchers.IO) {
        val file = resolvePath(path)
        val parent = file.parentFile
        if (parent != null && !parent.exists()) {
            if (!parent.mkdirs() && !parent.exists()) {
                throw IOException("Failed to create directory: $parent")
            }
        }
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.sink().buffer().use { sink ->
            sink.write(bytes)
        }
        if (!tmp.renameTo(file)) {
            if (file.exists() && file.delete() && tmp.renameTo(file)) {
                return@withContext
            }
            throw IOException("Failed to replace $file with temp file")
        }
    }

    override suspend fun delete(path: String): Boolean = withContext(Dispatchers.IO) {
        val file = resolvePath(path)
        !file.exists() || file.delete()
    }

    override suspend fun exists(path: String): Boolean = withContext(Dispatchers.IO) {
        resolvePath(path).exists()
    }

    override suspend fun lastModified(path: String): Long? = withContext(Dispatchers.IO) {
        resolvePath(path)
            .takeIf { it.exists() }
            ?.lastModified()
            ?.takeIf { it > 0L }
    }

    private fun resolvePath(path: String): File {
        val root = rootDir.canonicalFile
        val file = File(root, path).canonicalFile
        if (!file.path.startsWith(root.path)) {
            throw SecurityException("Refusing to access file outside root: $path")
        }
        return file
    }
}
