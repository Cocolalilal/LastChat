package me.rerere.rikkahub.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Per-conversation extraction bookkeeping for the memory encoder (schema v34).
 *
 * Holds the encoding watermark as the **id of the last processed message** (not a list index):
 * deleting or reordering messages below an index would silently corrupt the extraction window, so
 * the watermark anchors on a stable message id and is re-resolved to an index against the current
 * branch (see [MemoryWatermark]). Kept in its own memory-store table rather than on
 * [ConversationEntity] so the high-frequency conversation-persist path (which rebuilds the whole
 * entity on every save) can never clobber it, and so the memory system stays self-contained.
 *
 * Hard invariant: the watermark never advances past a message no extraction pass has read, so a
 * deferred/failed pass — or a deletion/branch switch below it — delays or re-reads memory but can
 * never lose it.
 */
@Entity(
    tableName = "memory_conversation_state",
    indices = [Index(value = ["assistant_id"])]
)
data class MemoryConversationStateEntity(
    @PrimaryKey
    @ColumnInfo(name = "conversation_id")
    val conversationId: String,
    @ColumnInfo(name = "assistant_id")
    val assistantId: String,
    /** Id of the last processed message; null = nothing extracted yet. Anchors the watermark. */
    @ColumnInfo(name = "extracted_up_to_message_id")
    val extractedUpToMessageId: String? = null,
    @ColumnInfo(name = "last_extract_at")
    val lastExtractAt: Long = 0L,
)
