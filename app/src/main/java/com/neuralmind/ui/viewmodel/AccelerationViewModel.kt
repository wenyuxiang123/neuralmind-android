package com.neuralmind.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neuralmind.core.Logger
import com.neuralmind.llama.HardwareAccelerationManager
import com.neuralmind.llama.HardwareAccelerationManager.AccelerationInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class AccelerationViewModel @Inject constructor(
    private val hardwareAccelerationManager: HardwareAccelerationManager
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(AccelerationUiState())
    val uiState: StateFlow<AccelerationUiState> = _uiState.asStateFlow()
    
    init {
        loadAccelerators()
    }
    
    fun loadAccelerators() {
        Logger.d(Logger.Tags.VM, "loadAccelerators()")
        viewModelScope.launch {
            try {
                val accelerators = hardwareAccelerationManager.getAvailableAccelerators()
                val selected = hardwareAccelerationManager.getSelectedAccelerator()
                _uiState.update {
                    it.copy(
                        accelerators = accelerators,
                        selectedAccelerator = selected
                    )
                }
                Logger.i(Logger.Tags.VM, "loadAccelerators success: ${accelerators.size} accelerators")
            } catch (e: Exception) {
                Logger.e(Logger.Tags.VM, "loadAccelerators failed", e)
            }
        }
    }
    
    fun selectAccelerator(type: HardwareAccelerationManager.AccelerationType) {
        Logger.d(Logger.Tags.VM, "selectAccelerator(type=$type)")
        hardwareAccelerationManager.selectAccelerator(type)
        loadAccelerators()
    }
}

data class AccelerationUiState(
    val accelerators: List<AccelerationInfo> = emptyList(),
    val selectedAccelerator: HardwareAccelerationManager.AccelerationType = HardwareAccelerationManager.AccelerationType.AUTO
)
