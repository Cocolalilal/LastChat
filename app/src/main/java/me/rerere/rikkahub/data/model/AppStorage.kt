package me.rerere.rikkahub.data.model

data class StorageCategoryUsage(
    val id: String,
    val label: String,
    val bytes: Long,
    val clearable: Boolean = false,
)

data class OtherUploadFile(
    val path: String,
    val uri: String,
    val displayName: String,
    val mime: String? = null,
    val sizeBytes: Long,
    val modifiedAt: Long,
    val isImage: Boolean = false,
)

data class AppStorageSnapshot(
    val totalBytes: Long = 0L,
    val appBytes: Long = 0L,
    val dataBytes: Long = 0L,
    val cacheBytes: Long = 0L,
    val chatBytes: Long = 0L,
    val chatCount: Int = 0,
    val categories: List<StorageCategoryUsage> = emptyList(),
    val isScanning: Boolean = true,
)
