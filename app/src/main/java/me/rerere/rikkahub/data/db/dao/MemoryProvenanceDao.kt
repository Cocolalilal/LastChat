package me.rerere.rikkahub.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import me.rerere.rikkahub.data.db.entity.MemoryProvenanceEntity

/** DAO for `memory_provenance` — the "why does this memory exist" story (Memory v2, plan §4.8). */
@Dao
interface MemoryProvenanceDao {
    @Upsert
    suspend fun upsert(provenance: MemoryProvenanceEntity)

    @Upsert
    suspend fun upsertAll(provenance: List<MemoryProvenanceEntity>)

    @Query("SELECT * FROM memory_provenance WHERE row_kind = :rowKind AND row_id = :rowId ORDER BY created_at ASC")
    suspend fun getFor(rowKind: Int, rowId: String): List<MemoryProvenanceEntity>

    @Query("SELECT * FROM memory_provenance WHERE conversation_id = :conversationId")
    suspend fun getForConversation(conversationId: String): List<MemoryProvenanceEntity>

    @Query("DELETE FROM memory_provenance WHERE row_kind = :rowKind AND row_id = :rowId")
    suspend fun deleteFor(rowKind: Int, rowId: String)
}
