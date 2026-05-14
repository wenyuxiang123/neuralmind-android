package com.neuralmind.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neuralmind.core.Logger
import com.neuralmind.data.repository.ModelRepository
import com.neuralmind.domain.model.AIModel
import com.neuralmind.domain.model.ModelCategory
import com.neuralmind.llama.LlamaEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ModelViewModel @Inject constructor(
    private val modelRepository: ModelRepository,
    private val llamaEngine: LlamaEngine
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(ModelUiState())
    val uiState: StateFlow<ModelUiState> = _uiState.asStateFlow()
    
    val allModels = modelRepository.getAllModels()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    
    val installedModels = modelRepository.getInstalledModels()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    
    fun getModelsByCategory(category: ModelCategory): Flow<List<AIModel>> {
        Logger.d(Logger.Tags.VM, "getModelsByCategory(category=${category.name})")
        return modelRepository.getModelsByCategory(category)
    }
    
    fun downloadModel(modelId: String) {
        Logger.d(Logger.Tags.VM, "downloadModel(modelId=$modelId)")
        viewModelScope.launch {
            _uiState.update { it.copy(downloadingModelId = modelId, downloadProgress = 0f) }
            
            try {
                modelRepository.downloadModel(modelId)
                _uiState.update { 
                    it.copy(downloadingModelId = null, downloadProgress = 1f) 
                }
                Logger.i(Logger.Tags.VM, "downloadModel success: $modelId")
                
                // Download complete, now load model into engine
                try {
                    val loaded = llamaEngine.loadModel(modelId)
                    if (loaded) {
                        Logger.i(Logger.Tags.VM, "Model loaded into engine: $modelId")
                    } else {
                        Logger.w(Logger.Tags.VM, "Failed to load model into engine: $modelId")
                    }
                } catch (e: Exception) {
                    Logger.e(Logger.Tags.VM, "Exception loading model into engine: $modelId", e)
                }
            } catch (e: Exception) {
                Logger.e(Logger.Tags.VM, "downloadModel failed: $modelId", e)
                _uiState.update { 
                    it.copy(downloadingModelId = null, downloadProgress = 0f) 
                }
            }
        }
    }
    
    fun deleteModel(modelId: String) {
        Logger.d(Logger.Tags.VM, "deleteModel(modelId=$modelId)")
        viewModelScope.launch {
            try {
                modelRepository.deleteModel(modelId)
                // If deleted model was loaded, unload from engine
                if (llamaEngine.isModelLoaded.value) {
                    llamaEngine.unloadModel()
                    Logger.i(Logger.Tags.VM, "Unloaded model from engine after delete: $modelId")
                }
                Logger.i(Logger.Tags.VM, "deleteModel success: $modelId")
            } catch (e: Exception) {
                Logger.e(Logger.Tags.VM, "deleteModel failed: $modelId", e)
            }
        }
    }
    
    fun selectModel(model: AIModel) {
        Logger.d(Logger.Tags.VM, "selectModel(model=${model.name})")
        _uiState.update {
            it.copy(selectedModelId = model.id)
        }
        Logger.i(Logger.Tags.VM, "selectModel success: ${model.name}")
    }
}

data class ModelUiState(
    val downloadingModelId: String? = null,
    val downloadProgress: Float = 0f,
    val selectedModelId: String? = null
)
