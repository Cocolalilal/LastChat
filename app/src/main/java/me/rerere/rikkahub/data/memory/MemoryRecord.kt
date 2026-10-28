package me.rerere.rikkahub.data.memory

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** Per-owner records participate in the same database snapshot as conversations and backups. */
@Entity(tableName = "character_memory", primaryKeys = ["owner", "kind", "id"])
data class MemoryRecord(val owner: String, val kind: String, val id: String, val payload: String)

@Dao
interface CharacterMemoryDao {
    @Query("SELECT * FROM character_memory WHERE owner = :owner")
    fun observe(owner: String): Flow<List<MemoryRecord>>
    @Query("SELECT * FROM character_memory WHERE owner = :owner")
    suspend fun all(owner: String): List<MemoryRecord>
    @Query("SELECT * FROM character_memory WHERE owner = :owner AND kind = :kind AND id = :id")
    suspend fun get(owner: String, kind: String, id: String): MemoryRecord?
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(record: MemoryRecord)
    @Query("DELETE FROM character_memory WHERE owner = :owner AND kind = :kind AND id = :id")
    suspend fun delete(owner: String, kind: String, id: String)
    @Query("DELETE FROM character_memory WHERE owner = :owner AND kind = :kind")
    suspend fun clearKind(owner: String, kind: String)
    @Query("DELETE FROM character_memory WHERE owner = :owner")
    suspend fun deleteOwner(owner: String)
}
