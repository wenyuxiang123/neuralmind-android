package com.neuralmind.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neuralmind.core.Logger
import com.neuralmind.data.repository.MemoryRepository
import com.neuralmind.domain.model.Memory
import com.neuralmind.domain.model.MemoryLayer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MemoryViewModel @Inject constructor(
    private val memoryRepository: MemoryRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(MemoryUiState())
    val uiState: StateFlow<MemoryUiState> = _uiState.asStateFlow()
    
    val memories = memoryRepository.getAllActiveMemories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    
    private val _activeMemoryLayers = MutableStateFlow<Set<MemoryLayer>>(
        setOf(
            MemoryLayer.L1_WORKING,
            MemoryLayer.L2_SHORT_TERM,
            MemoryLayer.L3_SESSION,
            MemoryLayer.L5_PERSONAL,
            MemoryLayer.L6_PREFERENCE
        )
    )
    
    val activeMemoryLayers: StateFlow<Set<MemoryLayer>> = _activeMemoryLayers.asStateFlow()
    
    fun toggleLayer(layer: MemoryLayer) {
        Logger.d(Logger.Tags.VM, "toggleLayer(layer=${layer.name})")
        _activeMemoryLayers.value = _activeMemoryLayers.value.toMutableSet().apply {
            if (contains(layer)) {
                remove(layer)
                Logger.i(Logger.Tags.VM, "toggleLayer: deactivated ${layer.name}")
            } else {
                add(layer)
                Logger.i(Logger.Tags.VM, "toggleLayer: activated ${layer.name}")
            }
        }
    }
    
    fun addMemory(content: String, category: String, importance: Int, layer: MemoryLayer) {
        Logger.d(Logger.Tags.VM, "addMemory(layer=${layer.name}, content=${content.take(30)}...)")
        viewModelScope.launch {
            try {
                val memory = Memory(
                    layer = layer,
                    content = content,
                    category = category,
                    importance = importance
                )
                memoryRepository.addMemory(memory)
                Logger.i(Logger.Tags.VM, "addMemory success")
            } catch (e: Exception) {
                Logger.e(Logger.Tags.VM, "addMemory failed", e)
            }
        }
    }
    
    fun deleteMemory(memoryId: Long) {
        Logger.d(Logger.Tags.VM, "deleteMemory(memoryId=$memoryId)")
        viewModelScope.launch {
            try {
                memoryRepository.deleteMemory(memoryId)
                Logger.i(Logger.Tags.VM, "deleteMemory success: $memoryId")
            } catch (e: Exception) {
                Logger.e(Logger.Tags.VM, "deleteMemory failed: $memoryId", e)
            }
        }
    }
}

data class MemoryUiState(
    val isLoading: Boolean = false
)
