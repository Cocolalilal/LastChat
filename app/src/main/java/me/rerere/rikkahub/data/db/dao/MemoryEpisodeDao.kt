package me.rerere.rikkahub.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import me.rerere.rikkahub.data.db.entity.MemoryEpisodeEntity
import me.rerere.rikkahub.data.db.entity.MemoryMentionEntity

/**
 * DAO for `memory_episode` scenes and their `memory_mention` connectivity (Memory v2, plan §4.5–4.6).
 */
@Dao
interface MemoryEpisodeDao {
    // --- episodes ---

    @Upsert
    suspend fun upsertEpisode(episode: MemoryEpisodeEntity)

    @Query("SELECT * FROM memory_episode WHERE id = :id")
    suspend fun getEpisode(id: String): MemoryEpisodeEntity?

    @Query(
        "SELECT * FROM memory_episode " +
            "WHERE owner_assistant_id = :assistantId AND status = :status " +
            "ORDER BY event_start DESC LIMIT :limit"
    )
    suspend fun getRecentEpisodes(assistantId: String, status: Int, limit: Int): List<MemoryEpisodeEntity>

    @Query("SELECT * FROM memory_episode WHERE conversation_id = :conversationId")
    suspend fun getEpisodesForConversation(conversationId: String): List<MemoryEpisodeEntity>

    @Query("UPDATE memory_episode SET status = :status WHERE id = :id")
    suspend fun setEpisodeStatus(id: String, status: Int)

    @Delete
    suspend fun deleteEpisode(episode: MemoryEpisodeEntity)

    @Query("DELETE FROM memory_episode WHERE id = :id")
    suspend fun deleteEpisodeById(id: String)

    @Query("DELETE FROM memory_episode WHERE owner_assistant_id = :assistantId")
    suspend fun deleteEpisodesOfAssistant(assistantId: String)

    // --- mentions ---

    @Upsert
    suspend fun upsertMention(mention: MemoryMentionEntity)

    @Upsert
    suspend fun upsertMentions(mentions: List<MemoryMentionEntity>)

    @Query("SELECT * FROM memory_mention WHERE episode_id = :episodeId")
    suspend fun getMentionsForEpisode(episodeId: String): List<MemoryMentionEntity>

    @Query("SELECT * FROM memory_mention WHERE entity_id = :entityId")
    suspend fun getMentionsForEntity(entityId: String): List<MemoryMentionEntity>

    @Query("DELETE FROM memory_mention WHERE episode_id = :episodeId")
    suspend fun deleteMentionsForEpisode(episodeId: String)
}
