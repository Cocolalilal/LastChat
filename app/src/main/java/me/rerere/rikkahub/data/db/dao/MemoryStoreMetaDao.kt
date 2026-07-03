package me.rerere.rikkahub.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import me.rerere.rikkahub.data.db.entity.MemoryStoreMetaEntity

@Dao
interface MemoryStoreMetaDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(row: MemoryStoreMetaEntity)

    @Query("SELECT value FROM memory_store_meta WHERE `key` = :key LIMIT 1")
    suspend fun get(key: String): String?

    @Query("DELETE FROM memory_store_meta WHERE `key` = :key")
    suspend fun delete(key: String)
}
