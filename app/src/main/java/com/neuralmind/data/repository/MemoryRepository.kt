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
     * Save a conversation segment (user message + AI response) to L1/L2/L3 memory layers.
     * Called after each conversation turn completes.
     */
    suspend fun saveConversationSegment(userContent: String, aiContent: String, modelId: String) {
        Logger.d(Logger.Tags.REPO, "saveConversationSegment: user=${userContent.take(20)}..., modelId=$modelId")
        try {
            val truncatedContent = if (userContent.length > 200 || aiContent.length > 200) {
                "用户: ${userContent.take(200)}
AI: ${aiContent.take(200)}"
            } else {
                "用户: $userContent
AI: $aiContent"
            }
            
            // L1: Working memory (current dialogue context), importance = 5
            addMemory(Memory(
                layer = MemoryLayer.L1_WORKING,
                content = truncatedContent,
                category = "对话",
                importance = 5
            ))
            
            // L2: Short-term memory (recent dialogue records), importance = 4
            addMemory(Memory(
                layer = MemoryLayer.L2_SHORT_TERM,
                content = truncatedContent,
                category = "对话",
                importance = 4
            ))
            
            // L3: Session memory (this session complete record), importance = 3
            addMemory(Memory(
                layer = MemoryLayer.L3_SESSION,
                content = truncatedContent,
                category = "对话",
                importance = 3
            ))
            
            Logger.i(Logger.Tags.REPO, "saveConversationSegment success: saved to L1/L2/L3 layers")
        } catch (e: Exception) {
            Logger.e(Logger.Tags.REPO, "saveConversationSegment failed", e)
        }
    }
    
    /**
     * Clear all memories from a specific layer.
     * Used for memory pressure handling to free up memory.
     */
    suspend fun clearLayerMemories(layer: MemoryLayer) {
        try {
            val entities = memoryDao.getMemoriesByLayer(layer.name).first()
            entities.forEach { memoryDao.delete(it) }
            Logger.i(Logger.Tags.REPO, "clearLayerMemories: cleared ${entities.size} memories from ${layer.name}")
        } catch (e: Exception) {
            Logger.e(Logger.Tags.REPO, "clearLayerMemories failed: ${layer.name}", e)
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
     * Check if the input is a request/intent rather than a preference statement.
     * Requests like "我想写文章" should NOT be stored as preferences.
     */
    private fun isRequestIntent(input: String, pattern: String): Boolean {
        // Check if pattern is followed by action verbs + content
        val actionVerbs = listOf("写", "做", "要", "看", "去", "吃", "买", "玩", "听", "学", "了解", "知道", "搜索", "查询", "获取", "下载", "打开", "启动", "运行")
        val patternIndex = input.indexOf(pattern, ignoreCase = true)
        if (patternIndex < 0) return false
        
        val afterPattern = input.substring(patternIndex + pattern.length)
        // If followed by action verb and substantial content (>10 chars), likely a request
        if (afterPattern.isNotEmpty()) {
            val trimmed = afterPattern.trim()
            if (trimmed.isNotEmpty() && trimmed.length > 3) {
                // Check if starts with an action verb
                val startsWithAction = actionVerbs.any { trimmed.startsWith(it) }
                if (startsWithAction && trimmed.length > 10) {
                    return true
                }
                // Also check for common request patterns like "我想XXX一下", "我想XXX一点"
                if (trimmed.contains(Regex("(一下|一点|一下的|帮我|给我|你能|你可以)")) && trimmed.length > 10) {
                    return true
                }
            }
        }
        return false
    }
    
    /**
     * Check if extracted content is too short (low information density).
     */
    private fun isLowInfoDensity(content: String): Boolean {
        // Filter out very short content (< 3 chars after trim)
        val trimmed = content.trim()
        if (trimmed.length < 3) {
            Logger.d(Logger.Tags.REPO, "isLowInfoDensity: rejecting short content '${trimmed}'")
            return true
        }
        return false
    }
    
    /**
     * Automatically save user information from input as memories.
     * Extracts structured information based on memory layer context.
     * FIX: Better filtering to avoid storing requests as preferences.
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
                    // FIX: Only save as preference if it's NOT a request intent
                    val prefPatterns = listOf("我喜欢", "我偏好")
                    for (pattern in prefPatterns) {
                        if (input.contains(pattern)) {
                            // Skip if it's a request like "我想写文章"
                            if (pattern == "我想" && isRequestIntent(input, pattern)) {
                                Logger.d(Logger.Tags.REPO, "saveUserInfoFromInput: skipping request intent for '我想'")
                                continue
                            }
                            val start = input.indexOf(pattern) + pattern.length
                            val content = input.substring(start).take(50).trim()
                            // Only save if meaningful content
                            if (content.isNotEmpty() && !isLowInfoDensity(content)) {
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
                    // Also check "我想要" patterns
                    val wantPatterns = listOf("我想要", "我偏向")
                    for (pattern in wantPatterns) {
                        if (input.contains(pattern)) {
                            if (isRequestIntent(input, pattern)) {
                                continue
                            }
                            val start = input.indexOf(pattern) + pattern.length
                            val content = input.substring(start).take(50).trim()
                            if (content.isNotEmpty() && !isLowInfoDensity(content)) {
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
                            val cleanInput = input.replace(pattern, "", ignoreCase = true).trim()
                            if (cleanInput.isNotEmpty() && !isLowInfoDensity(cleanInput)) {
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
                            val cleanInput = input.replace(pattern, "", ignoreCase = true).trim()
                            if (cleanInput.isNotEmpty() && !isLowInfoDensity(cleanInput)) {
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
                            val cleanInput = input.replace(pattern, "", ignoreCase = true).trim()
                            if (cleanInput.isNotEmpty() && !isLowInfoDensity(cleanInput)) {
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
                        val trimmed = input.trim()
                        if (!isLowInfoDensity(trimmed)) {
                            addMemory(Memory(
                                layer = layer,
                                content = trimmed.take(100),
                                category = "上下文",
                                importance = 5
                            ))
                        }
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

