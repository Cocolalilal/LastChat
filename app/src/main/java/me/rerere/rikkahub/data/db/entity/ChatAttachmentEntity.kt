package me.rerere.rikkahub.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "chat_attachment",
    indices = [
        Index(value = ["sha256"]),
        Index(value = ["file_path"], unique = true),
        Index(value = ["kind"]),
        Index(value = ["deleted"]),
    ]
)
data class ChatAttachmentEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "file_path", defaultValue = "")
    val filePath: String,
    @ColumnInfo(name = "display_name", defaultValue = "")
    val displayName: String,
    @ColumnInfo(name = "sha256")
    val sha256: String,
    @ColumnInfo(name = "mime", defaultValue = "")
    val mime: String,
    @ColumnInfo(name = "kind")
    val kind: String,
    @ColumnInfo(name = "size_bytes", defaultValue = "0")
    val sizeBytes: Long,
    @ColumnInfo(name = "width")
    val width: Int? = null,
    @ColumnInfo(name = "height")
    val height: Int? = null,
    @ColumnInfo(name = "ocr_text")
    val ocrText: String? = null,
    @ColumnInfo(name = "ocr_status", defaultValue = "NONE")
    val ocrStatus: String,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
    @ColumnInfo(name = "deleted", defaultValue = "0")
    val deleted: Boolean = false,
)
