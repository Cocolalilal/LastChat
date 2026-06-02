package me.rerere.rikkahub.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import me.rerere.rikkahub.data.db.entity.LocalModelInstallEntity

@Dao
interface LocalModelInstallDao {
    @Query("SELECT * FROM local_model_install ORDER BY display_name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<LocalModelInstallEntity>>

    @Query("SELECT * FROM local_model_install ORDER BY display_name COLLATE NOCASE ASC")
    suspend fun getAll(): List<LocalModelInstallEntity>

    @Query("SELECT * FROM local_model_install WHERE catalog_id = :catalogId LIMIT 1")
    suspend fun getByCatalogId(catalogId: String): LocalModelInstallEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: LocalModelInstallEntity)

    @Update
    suspend fun update(entity: LocalModelInstallEntity)

    @Query("DELETE FROM local_model_install WHERE catalog_id = :catalogId")
    suspend fun deleteByCatalogId(catalogId: String)
}
