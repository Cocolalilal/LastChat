package me.rerere.rikkahub.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import me.rerere.rikkahub.data.db.entity.LocalModelInstallEntity

@Dao
interface LocalModelInstallDao {
    @Query("SELECT * FROM local_model_install ORDER BY display_name ASC")
    fun observeAll(): Flow<List<LocalModelInstallEntity>>

    @Query("SELECT * FROM local_model_install ORDER BY display_name ASC")
    suspend fun getAll(): List<LocalModelInstallEntity>

    @Query("SELECT * FROM local_model_install WHERE catalog_id = :catalogId LIMIT 1")
    suspend fun getByCatalogId(catalogId: String): LocalModelInstallEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: LocalModelInstallEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<LocalModelInstallEntity>)

    @Query("DELETE FROM local_model_install WHERE catalog_id = :catalogId")
    suspend fun deleteByCatalogId(catalogId: String)
}
