package me.rerere.rikkahub.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import me.rerere.rikkahub.data.db.entity.ChatEpisodeEntity

@Dao
interface ChatEpisodeDAO {
    @Query("SELECT * FROM ChatEpisodeEntity WHERE assistant_id = :assistantId ORDER BY end_time DESC")
    suspend fun getEpisodesOfAssistant(assistantId: String): List<ChatEpisodeEntity>

    @Query("SELECT * FROM ChatEpisodeEntity WHERE assistant_id = :assistantId ORDER BY end_time DESC")
    fun getEpisodesOfAssistantFlow(assistantId: String): Flow<List<ChatEpisodeEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEpisode(episode: ChatEpisodeEntity): Long

    @Query("DELETE FROM ChatEpisodeEntity WHERE id = :id")
    suspend fun deleteEpisode(id: Int)

    @Query("DELETE FROM ChatEpisodeEntity WHERE assistant_id = :assistantId")
    suspend fun deleteEpisodesOfAssistant(assistantId: String)
}
