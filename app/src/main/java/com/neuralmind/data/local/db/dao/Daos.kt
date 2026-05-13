package com.neuralmind.data.local.db.dao

import androidx.room.*
import com.neuralmind.data.local.db.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationDao {
    @Query("SELECT * FROM conversations ORDER BY updatedAt DESC")
    fun getAllConversations(): Flow<List<ConversationEntity>>
    
    @Query("SELECT * FROM conversations WHERE id = :id")
    suspend fun getConversationById(id: Long): ConversationEntity?
    
    @Query("SELECT * FROM conversations WHERE isPinned = 1 ORDER BY updatedAt DESC")
    fun getPinnedConversations(): Flow<List<ConversationEntity>>
    
    @Query("SELECT * FROM conversations WHERE isArchived = 0 ORDER BY updatedAt DESC")
    fun getActiveConversations(): Flow<List<ConversationEntity>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(conversation: ConversationEntity): Long
    
    @Update
    suspend fun update(conversation: ConversationEntity)
    
    @Delete
    suspend fun delete(conversation: ConversationEntity)
    
    @Query("UPDATE conversations SET messageCount = messageCount + 1, updatedAt = :timestamp WHERE id = :conversationId")
    suspend fun incrementMessageCount(conversationId: Long, timestamp: Long = System.currentTimeMillis())
    
    @Query("UPDATE conversations SET isPinned = :isPinned WHERE id = :id")
    suspend fun setPinned(id: Long, isPinned: Boolean)
    
    @Query("UPDATE conversations SET isArchived = :isArchived WHERE id = :id")
    suspend fun setArchived(id: Long, isArchived: Boolean)
}

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY timestamp ASC")
    fun getMessagesByConversation(conversationId: Long): Flow<List<MessageEntity>>
    
    @Query("SELECT * FROM messages WHERE id = :id")
    suspend fun getMessageById(id: Long): MessageEntity?
    
    @Query("SELECT COUNT(*) FROM messages WHERE conversationId = :conversationId")
    suspend fun getMessageCount(conversationId: Long): Int
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: MessageEntity): Long
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(messages: List<MessageEntity>)
    
    @Update
    suspend fun update(message: MessageEntity)
    
    @Delete
    suspend fun delete(message: MessageEntity)
    
    @Query("DELETE FROM messages WHERE conversationId = :conversationId")
    suspend fun deleteByConversation(conversationId: Long)
}

@Dao
interface ModelDao {
    @Query("SELECT * FROM models ORDER BY category, name")
    fun getAllModels(): Flow<List<ModelEntity>>
    
    @Query("SELECT * FROM models WHERE id = :id")
    suspend fun getModelById(id: String): ModelEntity?
    
    @Query("SELECT * FROM models WHERE category = :category ORDER BY name")
    fun getModelsByCategory(category: String): Flow<List<ModelEntity>>
    
    @Query("SELECT * FROM models WHERE isInstalled = 1")
    fun getInstalledModels(): Flow<List<ModelEntity>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(model: ModelEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(models: List<ModelEntity>)
    
    @Update
    suspend fun update(model: ModelEntity)
    
    @Delete
    suspend fun delete(model: ModelEntity)
    
    @Query("UPDATE models SET isInstalled = :isInstalled, localPath = :localPath WHERE id = :id")
    suspend fun setInstalled(id: String, isInstalled: Boolean, localPath: String? = null)
    
    @Query("UPDATE models SET isDownloading = :isDownloading, downloadProgress = :progress WHERE id = :id")
    suspend fun setDownloading(id: String, isDownloading: Boolean, progress: Float)
}

@Dao
interface MemoryDao {
    @Query("SELECT * FROM memories ORDER BY layer, importance DESC")
    fun getAllMemories(): Flow<List<MemoryEntity>>
    
    @Query("SELECT * FROM memories WHERE isActive = 1 ORDER BY layer, importance DESC")
    fun getAllActiveMemories(): Flow<List<MemoryEntity>>
    
    @Query("SELECT * FROM memories WHERE layer = :layer AND isActive = 1 ORDER BY importance DESC")
    fun getMemoriesByLayer(layer: String): Flow<List<MemoryEntity>>
    
    @Query("SELECT * FROM memories WHERE id = :id")
    suspend fun getMemoryById(id: Long): MemoryEntity?
    
    @Query("SELECT COUNT(*) FROM memories")
    suspend fun getMemoryCount(): Int
    
    @Query("SELECT * FROM memories WHERE content LIKE '%' || :keyword || '%' AND isActive = 1")
    fun searchMemories(keyword: String): Flow<List<MemoryEntity>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(memory: MemoryEntity): Long
    
    @Update
    suspend fun update(memory: MemoryEntity)
    
    @Delete
    suspend fun delete(memory: MemoryEntity)
    
    @Query("UPDATE memories SET accessCount = accessCount + 1, lastAccessed = :timestamp WHERE id = :id")
    suspend fun incrementAccess(id: Long, timestamp: Long = System.currentTimeMillis())
    
    @Query("UPDATE memories SET isActive = :isActive WHERE id = :id")
    suspend fun setActive(id: Long, isActive: Boolean)
    
    @Query("SELECT COUNT(*) FROM memories WHERE layer = :layer")
    suspend fun getCountByLayer(layer: String): Int
    
    @Query("DELETE FROM memories WHERE layer = :layer AND id NOT IN (SELECT id FROM memories WHERE layer = :layer ORDER BY importance DESC LIMIT :limit)")
    suspend fun pruneLayer(layer: String, limit: Int)
}

@Dao
interface SkillDao {
    @Query("SELECT * FROM skills ORDER BY category, name")
    fun getAllSkills(): Flow<List<SkillEntity>>
    
    @Query("SELECT * FROM skills WHERE id = :id")
    suspend fun getSkillById(id: String): SkillEntity?
    
    @Query("SELECT * FROM skills WHERE category = :category ORDER BY name")
    fun getSkillsByCategory(category: String): Flow<List<SkillEntity>>
    
    @Query("SELECT * FROM skills WHERE isInstalled = 1")
    fun getInstalledSkills(): Flow<List<SkillEntity>>
    
    @Query("SELECT * FROM skills WHERE isActive = 1")
    fun getActiveSkills(): Flow<List<SkillEntity>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(skill: SkillEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(skills: List<SkillEntity>)
    
    @Update
    suspend fun update(skill: SkillEntity)
    
    @Delete
    suspend fun delete(skill: SkillEntity)
    
    @Query("UPDATE skills SET isActive = :isActive WHERE id = :id")
    suspend fun setActive(id: String, isActive: Boolean)
    
    @Query("UPDATE skills SET isInstalled = :isInstalled WHERE id = :id")
    suspend fun setInstalled(id: String, isInstalled: Boolean)
}

@Dao
interface AutomationRuleDao {
    @Query("SELECT * FROM automation_rules ORDER BY updatedAt DESC")
    fun getAllRules(): Flow<List<AutomationRuleEntity>>
    
    @Query("SELECT * FROM automation_rules WHERE isEnabled = 1")
    fun getEnabledRules(): Flow<List<AutomationRuleEntity>>
    
    @Query("SELECT * FROM automation_rules WHERE id = :id")
    suspend fun getRuleById(id: String): AutomationRuleEntity?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(rule: AutomationRuleEntity)
    
    @Update
    suspend fun update(rule: AutomationRuleEntity)
    
    @Delete
    suspend fun delete(rule: AutomationRuleEntity)
    
    @Query("UPDATE automation_rules SET isEnabled = :isEnabled WHERE id = :id")
    suspend fun setEnabled(id: String, isEnabled: Boolean)
    
    @Query("UPDATE automation_rules SET lastTriggered = :timestamp, triggerCount = triggerCount + 1 WHERE id = :id")
    suspend fun recordTrigger(id: String, timestamp: Long = System.currentTimeMillis())
}

@Dao
interface ToolModuleDao {
    @Query("SELECT * FROM tool_modules ORDER BY category, name")
    fun getAllModules(): Flow<List<ToolModuleEntity>>
    
    @Query("SELECT * FROM tool_modules WHERE id = :id")
    suspend fun getModuleById(id: String): ToolModuleEntity?
    
    @Query("SELECT * FROM tool_modules WHERE isInstalled = 1")
    fun getInstalledModules(): Flow<List<ToolModuleEntity>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(module: ToolModuleEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(modules: List<ToolModuleEntity>)
    
    @Update
    suspend fun update(module: ToolModuleEntity)
    
    @Delete
    suspend fun delete(module: ToolModuleEntity)
    
    @Query("UPDATE tool_modules SET isInstalled = :isInstalled, localPath = :localPath WHERE id = :id")
    suspend fun setInstalled(id: String, isInstalled: Boolean, localPath: String? = null)
    
    @Query("UPDATE tool_modules SET isDownloading = :isDownloading, downloadProgress = :progress WHERE id = :id")
    suspend fun setDownloading(id: String, isDownloading: Boolean, progress: Float)
}
