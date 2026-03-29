package me.rerere.rikkahub.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "conversation_attachment_ref",
    primaryKeys = ["conversation_id", "attachment_id"],
    indices = [
        Index(value = ["attachment_id"]),
        Index(value = ["conversation_id"]),
    ]
)
data class ConversationAttachmentRefEntity(
    @ColumnInfo(name = "conversation_id")
    val conversationId: String,
    @ColumnInfo(name = "attachment_id")
    val attachmentId: String,
)
