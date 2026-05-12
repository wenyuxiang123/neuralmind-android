package com.neuralmind.data.repository

import com.neuralmind.data.local.db.dao.MemoryDao
import com.neuralmind.data.local.db.entity.MemoryEntity
import com.neuralmind.domain.model.Memory
import com.neuralmind.domain.model.MemoryLayer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class MemoryRepository @Inject constructor(
    private val memoryDao: MemoryDao
) {
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

    suspend fun addMemory(
        layer: MemoryLayer,
        content: String,
        category: String,
        importance: Int
    ): Long {
        val entity = MemoryEntity(
            layer = layer.name,
            content = content,
            category = category,
            importance = importance,
            accessCount = 0,
            lastAccessed = System.currentTimeMillis(),
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            isActive = true
        )
        return memoryDao.insert(entity)
    }

    suspend fun deleteMemory(memoryId: Long) {
        val memory = memoryDao.getMemoryById(memoryId)
        memory?.let {
            memoryDao.delete(it)
        }
    }

    suspend fun setActive(memoryId: Long, isActive: Boolean) {
        memoryDao.setActive(memoryId, isActive)
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
