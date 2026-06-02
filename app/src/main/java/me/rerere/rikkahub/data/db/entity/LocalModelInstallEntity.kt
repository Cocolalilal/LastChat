package me.rerere.rikkahub.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "local_model_install",
    indices = [
        Index(value = ["status"]),
        Index(value = ["repo_id", "file_name"]),
    ]
)
data class LocalModelInstallEntity(
    @PrimaryKey
    @ColumnInfo(name = "catalog_id")
    val catalogId: String,
    @ColumnInfo(name = "repo_id")
    val repoId: String,
    @ColumnInfo(name = "revision")
    val revision: String,
    @ColumnInfo(name = "model_id")
    val modelId: String,
    @ColumnInfo(name = "display_name")
    val displayName: String,
    @ColumnInfo(name = "file_name")
    val fileName: String,
    @ColumnInfo(name = "local_path")
    val localPath: String,
    @ColumnInfo(name = "status")
    val status: String,
    @ColumnInfo(name = "progress_percent", defaultValue = "0")
    val progressPercent: Int = 0,
    @ColumnInfo(name = "bytes_downloaded", defaultValue = "0")
    val bytesDownloaded: Long = 0L,
    @ColumnInfo(name = "bytes_total", defaultValue = "0")
    val bytesTotal: Long = 0L,
    @ColumnInfo(name = "size_bytes", defaultValue = "0")
    val sizeBytes: Long = 0L,
    @ColumnInfo(name = "min_ram_gb", defaultValue = "0")
    val minRamGb: Int = 0,
    @ColumnInfo(name = "description", defaultValue = "")
    val description: String = "",
    @ColumnInfo(name = "top_k", defaultValue = "64")
    val topK: Int = 64,
    @ColumnInfo(name = "top_p", defaultValue = "0.95")
    val topP: Float = 0.95f,
    @ColumnInfo(name = "temperature", defaultValue = "1.0")
    val temperature: Float = 1.0f,
    @ColumnInfo(name = "max_tokens", defaultValue = "1024")
    val maxTokens: Int = 1024,
    @ColumnInfo(name = "context_window_tokens")
    val contextWindowTokens: Int? = null,
    @ColumnInfo(name = "accelerators_csv", defaultValue = "gpu,cpu")
    val acceleratorsCsv: String = "gpu,cpu",
    @ColumnInfo(name = "selected_accelerator", defaultValue = "auto")
    val selectedAccelerator: String = "auto",
    @ColumnInfo(name = "vision_accelerator", defaultValue = "")
    val visionAccelerator: String = "",
    @ColumnInfo(name = "supports_image", defaultValue = "0")
    val supportsImage: Boolean = false,
    @ColumnInfo(name = "supports_audio", defaultValue = "0")
    val supportsAudio: Boolean = false,
    @ColumnInfo(name = "supports_reasoning", defaultValue = "0")
    val supportsReasoning: Boolean = false,
    @ColumnInfo(name = "supports_tools", defaultValue = "0")
    val supportsTools: Boolean = false,
    @ColumnInfo(name = "speculative_decoding", defaultValue = "0")
    val speculativeDecoding: Boolean = false,
    @ColumnInfo(name = "update_revision", defaultValue = "")
    val updateRevision: String = "",
    @ColumnInfo(name = "update_file_name", defaultValue = "")
    val updateFileName: String = "",
    @ColumnInfo(name = "update_info", defaultValue = "")
    val updateInfo: String = "",
    @ColumnInfo(name = "imported", defaultValue = "0")
    val imported: Boolean = false,
    @ColumnInfo(name = "last_error", defaultValue = "")
    val lastError: String = "",
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
)
