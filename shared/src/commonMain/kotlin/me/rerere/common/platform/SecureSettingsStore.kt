package me.rerere.common.platform

interface SecureSettingsStore {
    suspend fun readString(key: String): String?

    suspend fun writeString(key: String, value: String)

    suspend fun remove(key: String)
}

