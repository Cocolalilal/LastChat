package me.rerere.rikkahub.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * `memory_conversation_state` — per-conversation extraction watermark (Memory v2, plan §4.11).
 *
 * The watermark is **message-id anchored** (attempt 1's §7.2 hardening, kept verbatim): it
 * re-resolves to the nearest surviving earlier processed message when its anchor disappears, and
 * never sits inside a streaming message. A dedicated table keeps it off the high-frequency
 * conversation-persist path.
 */
@Entity(tableName = "memory_conversation_state")
data class MemoryConversationStateEntity(
    @PrimaryKey
    @ColumnInfo(name = "conversation_id")
    val conversationId: String,
    @ColumnInfo(name = "extracted_up_to_message_id")
    val extractedUpToMessageId: String? = null,
    @ColumnInfo(name = "last_extracted_at", defaultValue = "0")
    val lastExtractedAt: Long = 0,
)
