package com.neuralmind.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
        _activeMemoryLayers.value = _activeMemoryLayers.value.toMutableSet().apply {
            if (contains(layer)) {
                remove(layer)
            } else {
                add(layer)
            }
        }
    }

    fun addMemory(content: String, category: String, importance: Int, layer: MemoryLayer) {
        viewModelScope.launch {
            val memory = Memory(
                layer = layer,
                content = content,
                category = category,
                importance = importance
            )
            memoryRepository.addMemory(memory)
        }
    }

    fun deleteMemory(memoryId: Long) {
        viewModelScope.launch {
            memoryRepository.deleteMemory(memoryId)
        }
    }
}

data class MemoryUiState(
    val isLoading: Boolean = false
)
