package com.neuralmind.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neuralmind.data.repository.ToolkitRepository
import com.neuralmind.domain.model.ToolModule
import com.neuralmind.domain.model.ToolCategory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ToolkitViewModel @Inject constructor(
    private val toolkitRepository: ToolkitRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ToolkitUiState())
    val uiState: StateFlow<ToolkitUiState> = _uiState.asStateFlow()

    val tools = toolkitRepository.getAllTools()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val installedTools = toolkitRepository.getInstalledTools()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            toolkitRepository.insertDefaultTools()
        }
    }

    fun downloadTool(toolId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                toolkitRepository.installTool(toolId)
            } catch (e: Exception) {
                // 处理错误
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun deleteTool(toolId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                toolkitRepository.uninstallTool(toolId)
            } catch (e: Exception) {
                // 处理错误
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun launchTool(tool: ToolModule) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val result = toolkitRepository.executeTool(tool.id, emptyMap())
                _uiState.update { it.copy(isLoading = false, lastLaunchResult = result) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }
}

data class ToolkitUiState(
    val isLoading: Boolean = false,
    val lastLaunchResult: String? = null,
    val error: String? = null
)
