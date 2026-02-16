package me.rerere.rikkahub.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import me.rerere.rikkahub.data.db.entity.GraphEpisodeEntity

@Dao
interface GraphEpisodeDAO {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(episode: GraphEpisodeEntity): Long

    @Update
    suspend fun update(episode: GraphEpisodeEntity)

    @Query("SELECT * FROM GraphEpisodeEntity WHERE id = :id")
    suspend fun getById(id: Int): GraphEpisodeEntity?

    @Query("SELECT * FROM GraphEpisodeEntity WHERE assistant_id = :assistantId ORDER BY end_time DESC")
    suspend fun getAllEpisodes(assistantId: String): List<GraphEpisodeEntity>

    @Query("SELECT * FROM GraphEpisodeEntity WHERE assistant_id = :assistantId ORDER BY end_time DESC")
    fun getAllEpisodesFlow(assistantId: String): Flow<List<GraphEpisodeEntity>>

    @Query("SELECT * FROM GraphEpisodeEntity WHERE assistant_id = :assistantId ORDER BY end_time DESC LIMIT :limit")
    suspend fun getRecentEpisodes(assistantId: String, limit: Int): List<GraphEpisodeEntity>

    @Query("SELECT * FROM GraphEpisodeEntity WHERE conversation_id = :conversationId ORDER BY end_time DESC")
    suspend fun getEpisodesForConversation(conversationId: String): List<GraphEpisodeEntity>

    @Query("SELECT * FROM GraphEpisodeEntity WHERE assistant_id = :assistantId AND embedding IS NOT NULL")
    suspend fun getEpisodesWithEmbeddings(assistantId: String): List<GraphEpisodeEntity>

    @Query("SELECT COUNT(*) FROM GraphEpisodeEntity WHERE assistant_id = :assistantId")
    suspend fun getEpisodeCount(assistantId: String): Int

    @Query("SELECT COUNT(*) FROM GraphEpisodeEntity WHERE assistant_id = :assistantId")
    fun getEpisodeCountFlow(assistantId: String): Flow<Int>

    @Query("DELETE FROM GraphEpisodeEntity WHERE id = :id")
    suspend fun delete(id: Int)

    @Query("DELETE FROM GraphEpisodeEntity WHERE assistant_id = :assistantId")
    suspend fun deleteAllForAssistant(assistantId: String)
}
