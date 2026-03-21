package me.rerere.rikkahub.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import me.rerere.rikkahub.data.db.entity.ChatAttachmentEntity

@Dao
interface ChatAttachmentDao {
    @Query("SELECT * FROM chat_attachment")
    fun observeAll(): Flow<List<ChatAttachmentEntity>>

    @Query("SELECT * FROM chat_attachment")
    suspend fun getAll(): List<ChatAttachmentEntity>

    @Query("SELECT * FROM chat_attachment WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): ChatAttachmentEntity?

    @Query("SELECT * FROM chat_attachment WHERE file_path = :filePath LIMIT 1")
    suspend fun getByFilePath(filePath: String): ChatAttachmentEntity?

    @Query("SELECT * FROM chat_attachment WHERE sha256 = :sha256 AND deleted = 0 LIMIT 1")
    suspend fun getActiveBySha256(sha256: String): ChatAttachmentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: ChatAttachmentEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<ChatAttachmentEntity>)

    @Update
    suspend fun update(entity: ChatAttachmentEntity)

    @Query("DELETE FROM chat_attachment WHERE id = :id")
    suspend fun deleteById(id: String)
}
