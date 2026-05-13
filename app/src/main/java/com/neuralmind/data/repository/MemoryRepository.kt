package com.neuralmind.data.repository

import com.neuralmind.core.Logger
import com.neuralmind.data.local.db.dao.MemoryDao
import com.neuralmind.data.local.db.entity.MemoryEntity
import com.neuralmind.domain.model.Memory
import com.neuralmind.domain.model.MemoryLayer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MemoryRepository @Inject constructor(
    private val memoryDao: MemoryDao
) {
    
    fun getAllMemories(): Flow<List<Memory>> {
        Logger.d(Logger.Tags.REPO, "getAllMemories() called")
        return memoryDao.getAllMemories().map { entities ->
            entities.map { it.toDomain() }
        }
    }
    
    fun getAllActiveMemories(): Flow<List<Memory>> {
        Logger.d(Logger.Tags.REPO, "getAllActiveMemories() called")
        return memoryDao.getAllActiveMemories().map { entities ->
            entities.map { it.toDomain() }
        }
    }
    
    /**
     * Get a snapshot of currently active memories (non-Flow, immediate value).
     * Used for injecting memory context into prompt at inference time.
     */
    suspend fun getActiveMemoriesSnapshot(): List<Memory> {
        Logger.d(Logger.Tags.REPO, "getActiveMemoriesSnapshot() called")
        return memoryDao.getAllActiveMemories().first().map { it.toDomain() }
    }
    
    fun getMemoriesByLayer(layer: MemoryLayer): Flow<List<Memory>> {
        Logger.d(Logger.Tags.REPO, "getMemoriesByLayer(layer=${layer.name})")
        return memoryDao.getMemoriesByLayer(layer.name).map { entities ->
            entities.map { it.toDomain() }
        }
    }
    
    suspend fun addMemory(memory: Memory): Long {
        Logger.d(Logger.Tags.REPO, "addMemory(layer=${memory.layer.name}, content=${memory.content.take(50)})")
        return try {
            val entity = MemoryEntity(
                layer = memory.layer.name,
                content = memory.content,
                category = memory.category,
                importance = memory.importance,
                accessCount = 0,
                lastAccessed = System.currentTimeMillis(),
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
                isActive = true
            )
            val id = memoryDao.insert(entity)
            Logger.i(Logger.Tags.REPO, "addMemory success: id=$id")
            id
        } catch (e: Exception) {
            Logger.e(Logger.Tags.REPO, "addMemory failed", e)
            throw e
        }
    }
    
    suspend fun updateMemory(memory: Memory) {
        Logger.d(Logger.Tags.REPO, "updateMemory(id=${memory.id})")
        try {
            val entity = memoryDao.getMemoryById(memory.id)
            entity?.let {
                val updatedEntity = it.copy(
                    content = memory.content,
                    category = memory.category,
                    importance = memory.importance,
                    updatedAt = System.currentTimeMillis()
                )
                memoryDao.update(updatedEntity)
                Logger.i(Logger.Tags.REPO, "updateMemory success: id=${memory.id}")
            } ?: Logger.w(Logger.Tags.REPO, "updateMemory: memory not found, id=${memory.id}")
        } catch (e: Exception) {
            Logger.e(Logger.Tags.REPO, "updateMemory failed: id=${memory.id}", e)
        }
    }
    
    suspend fun deleteMemory(memoryId: Long) {
        Logger.d(Logger.Tags.REPO, "deleteMemory(memoryId=$memoryId)")
        try {
            val memory = memoryDao.getMemoryById(memoryId)
            memory?.let {
                memoryDao.delete(it)
                Logger.i(Logger.Tags.REPO, "deleteMemory success: id=$memoryId")
            } ?: Logger.w(Logger.Tags.REPO, "deleteMemory: memory not found, id=$memoryId")
        } catch (e: Exception) {
            Logger.e(Logger.Tags.REPO, "deleteMemory failed: id=$memoryId", e)
        }
    }
    
    suspend fun deleteMemory(memory: Memory) {
        Logger.d(Logger.Tags.REPO, "deleteMemory(memory=${memory.id})")
        deleteMemory(memory.id)
    }
    
    suspend fun setActive(memoryId: Long, isActive: Boolean) {
        Logger.d(Logger.Tags.REPO, "setActive(memoryId=$memoryId, isActive=$isActive)")
        try {
            memoryDao.setActive(memoryId, isActive)
            Logger.i(Logger.Tags.REPO, "setActive success: id=$memoryId")
        } catch (e: Exception) {
            Logger.e(Logger.Tags.REPO, "setActive failed: id=$memoryId", e)
        }
    }
    
    suspend fun activateMemoryLayer(layer: MemoryLayer) {
        Logger.d(Logger.Tags.REPO, "activateMemoryLayer(layer=${layer.name})")
        try {
            val entities = memoryDao.getMemoriesByLayer(layer.name).first()
            entities.forEach { entity ->
                memoryDao.setActive(entity.id, true)
                memoryDao.incrementAccess(entity.id)
            }
            Logger.i(Logger.Tags.REPO, "activateMemoryLayer success: layer=${layer.name}, count=${entities.size}")
        } catch (e: Exception) {
            Logger.e(Logger.Tags.REPO, "activateMemoryLayer failed: layer=${layer.name}", e)
        }
    }
    
    /**
     * Activate memory layers based on user input and automatically save user info as memories.
     * This enables the memory system to learn from user conversations.
     */
    suspend fun activateMemoryFromUserInput(input: String) {
        Logger.d(Logger.Tags.REPO, "activateMemoryFromUserInput(input=${input.take(50)}...)")
        try {
            val patterns = listOf(
                Pair(listOf("记住", "学习", "掌握", "了解"), MemoryLayer.L7_KNOWLEDGE),
                Pair(listOf("我喜欢", "我偏好", "我想"), MemoryLayer.L6_PREFERENCE),
                Pair(listOf("每天", "定时", "提醒", "日程"), MemoryLayer.L4_SCHEDULE),
                Pair(listOf("我是", "我的名字", "个人信息"), MemoryLayer.L5_PERSONAL),
                Pair(listOf("习惯", "总是", "通常"), MemoryLayer.L8_HABIT),
                Pair(listOf("目标", "梦想", "想要"), MemoryLayer.L9_DEEP)
            )
            
            patterns.forEach { (keywords, layer) ->
                if (keywords.any { input.contains(it, ignoreCase = true) }) {
                    activateMemoryLayer(layer)
                    saveUserInfoFromInput(input, layer)
                }
            }
            
            activateMemoryLayer(MemoryLayer.L1_WORKING)
            activateMemoryLayer(MemoryLayer.L2_SHORT_TERM)
            activateMemoryLayer(MemoryLayer.L3_SESSION)
            
            Logger.i(Logger.Tags.REPO, "activateMemoryFromUserInput completed")
        } catch (e: Exception) {
            Logger.e(Logger.Tags.REPO, "activateMemoryFromUserInput failed", e)
        }
    }
    
    /**
     * Automatically save user information from input as memories.
     * Extracts structured information based on memory layer context.
     */
    private suspend fun saveUserInfoFromInput(input: String, layer: MemoryLayer) {
        try {
            when (layer) {
                MemoryLayer.L5_PERSONAL -> {
                    // Extract name from patterns like "我叫XXX", "我的名字是XXX"
                    val namePatterns = listOf("我叫", "我的名字是", "我是")
                    for (pattern in namePatterns) {
                        if (input.contains(pattern)) {
                            val start = input.indexOf(pattern) + pattern.length
                            val content = input.substring(start).take(20).trim()
                            if (content.isNotEmpty() && content.length > 1) {
                                addMemory(Memory(
                                    layer = layer,
                                    content = "用户名字: $content",
                                    category = "个人信息",
                                    importance = 9
                                ))
                            }
                            break
                        }
                    }
                }
                MemoryLayer.L6_PREFERENCE -> {
                    // Save preference-related content
                    val prefPatterns = listOf("我喜欢", "我偏好", "我想", "我想要")
                    for (pattern in prefPatterns) {
                        if (input.contains(pattern)) {
                            val start = input.indexOf(pattern) + pattern.length
                            val content = input.substring(start).take(50).trim()
                            if (content.isNotEmpty()) {
                                addMemory(Memory(
                                    layer = layer,
                                    content = "用户偏好: $content",
                                    category = "偏好",
                                    importance = 7
                                ))
                            }
                            break
                        }
                    }
                }
                MemoryLayer.L7_KNOWLEDGE -> {
                    // Save knowledge from user input
                    val learnPatterns = listOf("记住", "学习", "掌握", "了解")
                    for (pattern in learnPatterns) {
                        if (input.contains(pattern)) {
                            // Extract the content after the keyword
                            val cleanInput = input.replace(pattern, "").trim()
                            if (cleanInput.isNotEmpty()) {
                                addMemory(Memory(
                                    layer = layer,
                                    content = cleanInput.take(100),
                                    category = "知识",
                                    importance = 7
                                ))
                            }
                            break
                        }
                    }
                }
                MemoryLayer.L8_HABIT -> {
                    // Save habit patterns
                    val habitPatterns = listOf("习惯", "总是", "通常")
                    for (pattern in habitPatterns) {
                        if (input.contains(pattern)) {
                            val cleanInput = input.replace(pattern, "").trim()
                            if (cleanInput.isNotEmpty()) {
                                addMemory(Memory(
                                    layer = layer,
                                    content = "用户习惯: $cleanInput",
                                    category = "习惯",
                                    importance = 6
                                ))
                            }
                            break
                        }
                    }
                }
                MemoryLayer.L9_DEEP -> {
                    // Save deep goals and aspirations
                    val goalPatterns = listOf("目标", "梦想", "想要", "我希望")
                    for (pattern in goalPatterns) {
                        if (input.contains(pattern)) {
                            val cleanInput = input.replace(pattern, "").trim()
                            if (cleanInput.isNotEmpty()) {
                                addMemory(Memory(
                                    layer = layer,
                                    content = "用户目标: $cleanInput",
                                    category = "目标",
                                    importance = 8
                                ))
                            }
                            break
                        }
                    }
                }
                else -> {
                    // For other layers, save general context
                    if (input.length in 10..200) {
                        addMemory(Memory(
                            layer = layer,
                            content = input.take(100),
                            category = "上下文",
                            importance = 5
                        ))
                    }
                }
            }
        } catch (e: Exception) {
            Logger.e(Logger.Tags.REPO, "saveUserInfoFromInput failed: layer=${layer.name}", e)
        }
    }
    
    suspend fun insertDefaultMemories() {
        Logger.d(Logger.Tags.REPO, "insertDefaultMemories() called")
        try {
            if (memoryDao.getMemoryCount() > 0) {
                Logger.d(Logger.Tags.REPO, "Default memories already exist, skipping")
                return
            }
            
            val defaultMemories = listOf(
                Memory(
                    layer = MemoryLayer.L7_KNOWLEDGE,
                    content = "NeuralMind 是一个本地运行的 AI 助手，所有推理都在设备上进行",
                    category = "系统",
                    importance = 10
                ),
                Memory(
                    layer = MemoryLayer.L7_KNOWLEDGE,
                    content = "支持九层记忆系统，包括工作记忆、短期记忆、会话记忆等",
                    category = "功能",
                    importance = 9
                ),
                Memory(
                    layer = MemoryLayer.L7_KNOWLEDGE,
                    content = "拥有技能模块、设备控制、工具包等多种功能",
                    category = "功能",
                    importance = 9
                ),
                Memory(
                    layer = MemoryLayer.L6_PREFERENCE,
                    content = "用户喜欢简洁高效的界面设计",
                    category = "UI偏好",
                    importance = 7
                ),
                Memory(
                    layer = MemoryLayer.L5_PERSONAL,
                    content = "您是 NeuralMind 的用户，欢迎使用！",
                    category = "基本信息",
                    importance = 8
                )
            )
            
            defaultMemories.forEach { addMemory(it) }
            Logger.i(Logger.Tags.REPO, "insertDefaultMemories success: ${defaultMemories.size} memories inserted")
        } catch (e: Exception) {
            Logger.e(Logger.Tags.REPO, "insertDefaultMemories failed", e)
        }
    }
    
    private fun MemoryEntity.toDomain(): Memory {
        return Memory(
            id = id,
            layer = MemoryLayer.valueOf(layer),
            content = content,
            category = category,
            importance = importance,
            accessCount = accessCount,
            lastAccessed = lastAccessed,
            createdAt = createdAt,
            updatedAt = updatedAt,
            isActive = isActive
        )
    }
}
