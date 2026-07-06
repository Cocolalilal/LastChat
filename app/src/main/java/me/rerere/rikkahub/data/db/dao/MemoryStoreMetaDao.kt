package me.rerere.rikkahub.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import me.rerere.rikkahub.data.db.entity.MemoryStoreMetaEntity

/** DAO for `memory_store_meta` — free-form store metadata (Memory v2, plan §4.11). */
@Dao
interface MemoryStoreMetaDao {
    @Upsert
    suspend fun put(row: MemoryStoreMetaEntity)

    @Query("SELECT value FROM memory_store_meta WHERE key = :key")
    suspend fun get(key: String): String?

    @Query("DELETE FROM memory_store_meta WHERE key = :key")
    suspend fun delete(key: String)
}
