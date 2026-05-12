package com.neuralmind.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neuralmind.data.repository.ModelRepository
import com.neuralmind.domain.model.AIModel
import com.neuralmind.domain.model.ModelCategory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ModelViewModel @Inject constructor(
    private val modelRepository: ModelRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ModelUiState())
    val uiState: StateFlow<ModelUiState> = _uiState.asStateFlow()

    val allModels = modelRepository.getAllModels()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val installedModels = modelRepository.getInstalledModels()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            modelRepository.insertDefaultModels()
        }
    }

    fun getModelsByCategory(category: ModelCategory): Flow<List<AIModel>> {
        return modelRepository.getModelsByCategory(category)
    }

    fun downloadModel(modelId: String) {
        viewModelScope.launch {
            modelRepository.downloadModel(modelId).collect { progress ->
                _uiState.update {
                    it.copy(
                        downloadingModelId = modelId,
                        downloadProgress = progress
                    )
                }
            }
        }
    }

    fun deleteModel(modelId: String) {
        viewModelScope.launch {
            modelRepository.deleteModel(modelId)
        }
    }

    fun selectModel(model: AIModel) {
        _uiState.update {
            it.copy(selectedModelId = model.id)
        }
    }
}

data class ModelUiState(
    val downloadingModelId: String? = null,
    val downloadProgress: Float = 0f,
    val selectedModelId: String? = null
)
