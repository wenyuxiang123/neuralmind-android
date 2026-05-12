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
        }
    }

    fun deleteTool(toolId: String) {
        viewModelScope.launch {
        }
    }

    fun launchTool(tool: ToolModule) {
    }
}

data class ToolkitUiState(
    val isLoading: Boolean = false
)
