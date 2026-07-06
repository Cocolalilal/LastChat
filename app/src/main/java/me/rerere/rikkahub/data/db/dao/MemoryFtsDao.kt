package me.rerere.rikkahub.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import me.rerere.rikkahub.data.db.entity.MemoryFtsEntity

/**
 * DAO for the unified `memory_fts` index (Memory v2, plan §4.9).
 *
 * Manually synced by the applier — there are no Room triggers. A single [search] MATCH covers all
 * recall paths (facts, episodes, entities).
 */
@Dao
interface MemoryFtsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(row: MemoryFtsEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rows: List<MemoryFtsEntity>)

    /** Returns the `row_ref_id`s of matching rows, optionally restricted to a [MemFtsRowKind]. */
    @Query("SELECT row_ref_id FROM memory_fts WHERE memory_fts MATCH :query LIMIT :limit")
    suspend fun search(query: String, limit: Int): List<String>

    @Query("SELECT row_ref_id FROM memory_fts WHERE row_kind = :rowKind AND memory_fts MATCH :query LIMIT :limit")
    suspend fun searchKind(rowKind: Int, query: String, limit: Int): List<String>

    @Query("DELETE FROM memory_fts WHERE row_ref_id = :rowRefId")
    suspend fun deleteByRef(rowRefId: String)
}
