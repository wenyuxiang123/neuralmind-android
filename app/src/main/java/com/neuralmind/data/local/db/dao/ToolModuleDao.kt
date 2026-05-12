package com.neuralmind.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.neuralmind.data.local.db.entity.ToolModuleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ToolModuleDao {
    @Query("SELECT * FROM tool_modules ORDER BY category, name")
    fun getAllModules(): Flow<List<ToolModuleEntity>>

    @Query("SELECT * FROM tool_modules WHERE id = :id")
    suspend fun getModuleById(id: String): ToolModuleEntity?

    @Query("SELECT * FROM tool_modules WHERE isInstalled = 1")
    fun getInstalledModules(): Flow<List<ToolModuleEntity>>

    @Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    suspend fun insert(module: ToolModuleEntity)

    @Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    suspend fun insertAll(modules: List<ToolModuleEntity>)

    @androidx.room.Update
    suspend fun update(module: ToolModuleEntity)

    @androidx.room.Delete
    suspend fun delete(module: ToolModuleEntity)

    @Query("UPDATE tool_modules SET isInstalled = :isInstalled, localPath = :localPath WHERE id = :id")
    suspend fun setInstalled(id: String, isInstalled: Boolean, localPath: String? = null)

    @Query("UPDATE tool_modules SET isDownloading = :isDownloading, downloadProgress = :progress WHERE id = :id")
    suspend fun setDownloading(id: String, isDownloading: Boolean, progress: Float)
}
