package me.rerere.common.platform

interface PlatformFileStore {
    suspend fun readBytes(path: String): ByteArray?

    suspend fun writeBytes(path: String, bytes: ByteArray)

    suspend fun delete(path: String): Boolean

    suspend fun exists(path: String): Boolean

    suspend fun lastModified(path: String): Long?

    /** Returns a platform URL for an app-private file when the platform can expose one. */
    fun localUrl(path: String): String? = null

    /**
     * Recursively lists files under [path], returning paths relative to the store root
     * using `/` separators. Directories themselves are omitted. Default is empty so
     * stubs stay source-compatible.
     */
    suspend fun listFiles(path: String): List<String> = emptyList()

    suspend fun fileSize(path: String): Long? = readBytes(path)?.size?.toLong()

    suspend fun appendBytes(path: String, bytes: ByteArray) {
        val existing = readBytes(path) ?: ByteArray(0)
        writeBytes(path, existing + bytes)
    }
}
