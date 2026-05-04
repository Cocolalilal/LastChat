package me.rerere.rikkahub.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "local_model_install")
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
    @ColumnInfo(name = "description", defaultValue = "")
    val description: String = "",
    @ColumnInfo(name = "entry_json", defaultValue = "")
    val entryJson: String = "",
    @ColumnInfo(name = "file_paths_json", defaultValue = "[]")
    val filePathsJson: String = "[]",
    @ColumnInfo(name = "download_size_bytes", defaultValue = "0")
    val downloadSizeBytes: Long = 0L,
    @ColumnInfo(name = "estimated_installed_size_bytes", defaultValue = "0")
    val estimatedInstalledSizeBytes: Long = 0L,
    @ColumnInfo(name = "installed_size_bytes", defaultValue = "0")
    val installedSizeBytes: Long = 0L,
    @ColumnInfo(name = "checksum", defaultValue = "")
    val checksum: String = "",
    @ColumnInfo(name = "status")
    val status: String,
    @ColumnInfo(name = "runtime_backend")
    val runtimeBackend: String,
    @ColumnInfo(name = "provenance", defaultValue = "CURATED")
    val provenance: String = "CURATED",
    @ColumnInfo(name = "download_access", defaultValue = "PUBLIC")
    val downloadAccess: String = "PUBLIC",
    @ColumnInfo(name = "supported_abis_json", defaultValue = "[]")
    val supportedAbisJson: String = "[]",
    @ColumnInfo(name = "min_sdk", defaultValue = "0")
    val minSdk: Int = 0,
    @ColumnInfo(name = "minimum_ram_bytes", defaultValue = "0")
    val minimumRamBytes: Long = 0L,
    @ColumnInfo(name = "recommended_ram_bytes", defaultValue = "0")
    val recommendedRamBytes: Long = 0L,
    @ColumnInfo(name = "delegate_info", defaultValue = "")
    val delegateInfo: String = "",
    @ColumnInfo(name = "safe_for_background", defaultValue = "0")
    val safeForBackground: Boolean = false,
    @ColumnInfo(name = "current_file", defaultValue = "")
    val currentFile: String = "",
    @ColumnInfo(name = "bytes_downloaded", defaultValue = "0")
    val bytesDownloaded: Long = 0L,
    @ColumnInfo(name = "bytes_total", defaultValue = "0")
    val bytesTotal: Long = 0L,
    @ColumnInfo(name = "progress_percent", defaultValue = "0")
    val progressPercent: Int = 0,
    @ColumnInfo(name = "bytes_per_second", defaultValue = "0")
    val bytesPerSecond: Long = 0L,
    @ColumnInfo(name = "eta_seconds", defaultValue = "0")
    val etaSeconds: Long = 0L,
    @ColumnInfo(name = "source_uri", defaultValue = "")
    val sourceUri: String = "",
    @ColumnInfo(name = "last_error", defaultValue = "")
    val lastError: String = "",
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
)
