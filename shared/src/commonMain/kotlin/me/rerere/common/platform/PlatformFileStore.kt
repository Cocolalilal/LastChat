package me.rerere.common.platform

interface PlatformFileStore {
    suspend fun readBytes(path: String): ByteArray?

    suspend fun writeBytes(path: String, bytes: ByteArray)

    suspend fun delete(path: String): Boolean

    suspend fun exists(path: String): Boolean

    suspend fun lastModified(path: String): Long?
}
