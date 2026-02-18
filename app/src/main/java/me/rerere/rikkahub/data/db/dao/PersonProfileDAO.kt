package me.rerere.rikkahub.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import me.rerere.rikkahub.data.db.entity.PersonProfileEntity

@Dao
interface PersonProfileDAO {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(profile: PersonProfileEntity): Long

    @Update
    suspend fun update(profile: PersonProfileEntity)

    @Query("SELECT * FROM PersonProfileEntity WHERE node_id = :nodeId LIMIT 1")
    suspend fun getByNodeId(nodeId: Int): PersonProfileEntity?

    @Query("SELECT * FROM PersonProfileEntity WHERE assistant_id = :assistantId")
    fun getByAssistantFlow(assistantId: String): Flow<List<PersonProfileEntity>>

    @Query("DELETE FROM PersonProfileEntity WHERE assistant_id = :assistantId")
    suspend fun deleteAllForAssistant(assistantId: String)
}
