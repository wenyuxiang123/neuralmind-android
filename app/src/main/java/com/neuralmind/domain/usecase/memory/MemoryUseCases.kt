package com.neuralmind.domain.usecase.memory

import com.neuralmind.data.repository.MemoryRepository
import com.neuralmind.domain.model.Memory
import com.neuralmind.domain.model.MemoryLayer
import javax.inject.Inject

class GetAllMemoriesUseCase @Inject constructor(
    private val repository: MemoryRepository
) {
    operator fun invoke() = repository.getAllMemories()
}

class GetMemoriesByLayerUseCase @Inject constructor(
    private val repository: MemoryRepository
) {
    operator fun invoke(layer: MemoryLayer) = repository.getMemoriesByLayer(layer)
}

class AddMemoryUseCase @Inject constructor(
    private val repository: MemoryRepository
) {
    suspend operator fun invoke(memory: Memory) = repository.addMemory(memory)
}

class ActivateMemoryLayerUseCase @Inject constructor(
    private val repository: MemoryRepository
) {
    suspend operator fun invoke(layer: MemoryLayer, text: String) {
        repository.activateMemoryLayer(layer)
    }
}

class UpdateMemoryUseCase @Inject constructor(
    private val repository: MemoryRepository
) {
    suspend operator fun invoke(memory: Memory) = repository.updateMemory(memory)
}

class DeleteMemoryUseCase @Inject constructor(
    private val repository: MemoryRepository
) {
    suspend operator fun invoke(memoryId: Long) = repository.deleteMemory(memoryId)
}
