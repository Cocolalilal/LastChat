package me.rerere.rikkahub.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Per-conversation extraction bookkeeping for the memory encoder (schema v34).
 *
 * Holds the encoding watermark: the index into `Conversation.currentMessages` up to which
 * extraction has run. Kept in its own memory-store table rather than on [ConversationEntity] so the
 * high-frequency conversation-persist path (which rebuilds the whole entity on every save) can
 * never clobber the watermark, and so the memory system stays self-contained.
 *
 * Hard invariant: the watermark never advances past a message no extraction pass has read, so a
 * deferred/failed pass delays memory but can never lose it.
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
    @ColumnInfo(name = "extracted_up_to_index")
    val extractedUpToIndex: Int = -1,
    @ColumnInfo(name = "last_extract_at")
    val lastExtractAt: Long = 0L,
)
