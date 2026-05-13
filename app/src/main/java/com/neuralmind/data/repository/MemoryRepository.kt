package com.neuralmind.data.repository

import com.neuralmind.data.local.db.dao.MemoryDao
import com.neuralmind.data.local.db.entity.MemoryEntity
import com.neuralmind.domain.model.Memory
import com.neuralmind.domain.model.MemoryLayer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MemoryRepository @Inject constructor(
    private val memoryDao: MemoryDao
) {
    fun getAllMemories(): Flow<List<Memory>> {
        return memoryDao.getAllMemories().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    fun getAllActiveMemories(): Flow<List<Memory>> {
        return memoryDao.getAllActiveMemories().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    fun getMemoriesByLayer(layer: MemoryLayer): Flow<List<Memory>> {
        return memoryDao.getMemoriesByLayer(layer.name).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    suspend fun addMemory(memory: Memory): Long {
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
        return memoryDao.insert(entity)
    }

    suspend fun updateMemory(memory: Memory) {
        val entity = memoryDao.getMemoryById(memory.id)
        entity?.let {
            val updatedEntity = it.copy(
                content = memory.content,
                category = memory.category,
                importance = memory.importance,
                updatedAt = System.currentTimeMillis()
            )
            memoryDao.update(updatedEntity)
        }
    }

    suspend fun deleteMemory(memoryId: Long) {
        val memory = memoryDao.getMemoryById(memoryId)
        memory?.let {
            memoryDao.delete(it)
        }
    }

    suspend fun deleteMemory(memory: Memory) {
        deleteMemory(memory.id)
    }

    suspend fun setActive(memoryId: Long, isActive: Boolean) {
        memoryDao.setActive(memoryId, isActive)
    }

    suspend fun activateMemoryLayer(layer: MemoryLayer) {
        memoryDao.getMemoriesByLayer(layer.name).collect { entities ->
            entities.forEach { entity ->
                memoryDao.setActive(entity.id, true)
                memoryDao.incrementAccess(entity.id)
            }
        }
    }

    suspend fun activateMemoryFromUserInput(input: String) {
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
            }
        }

        activateMemoryLayer(MemoryLayer.L1_WORKING)
        activateMemoryLayer(MemoryLayer.L2_SHORT_TERM)
        activateMemoryLayer(MemoryLayer.L3_SESSION)
    }

    suspend fun insertDefaultMemories() {
        if (memoryDao.getMemoryCount() > 0) return

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
