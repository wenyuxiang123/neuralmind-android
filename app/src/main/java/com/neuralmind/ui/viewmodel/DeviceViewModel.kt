package com.neuralmind.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neuralmind.data.repository.DeviceRepository
import com.neuralmind.device.AudioStream
import com.neuralmind.device.DeviceController
import com.neuralmind.domain.model.AutomationRule
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DeviceViewModel @Inject constructor(
    private val deviceRepository: DeviceRepository,
    private val deviceController: DeviceController
) : ViewModel() {

    private val _uiState = MutableStateFlow(DeviceUiState())
    val uiState: StateFlow<DeviceUiState> = _uiState.asStateFlow()

    val automationRules = listOf(
        AutomationRule(
            id = "wakeup",
            name = "起床场景",
            description = "每天早上7点自动执行",
            triggers = listOf(),
            conditions = listOf(),
            actions = listOf(),
            isEnabled = true
        )
    )

    init {
        refreshDeviceState()
    }

    fun refreshDeviceState() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isWifiEnabled = deviceController.isWifiEnabled(),
                    isBluetoothEnabled = deviceController.isBluetoothEnabled(),
                    batteryLevel = deviceController.getBatteryLevel(),
                    isCharging = deviceController.isCharging(),
                    mediaVolume = deviceController.getVolume(AudioStream.MEDIA),
                    maxMediaVolume = deviceController.getMaxVolume(AudioStream.MEDIA),
                    brightness = deviceController.getBrightness()
                )
            }
        }
    }

    fun toggleWifi() {
        viewModelScope.launch {
            val newState = !_uiState.value.isWifiEnabled
            deviceController.setWifiEnabled(newState)
            _uiState.update { it.copy(isWifiEnabled = newState) }
        }
    }

    fun toggleBluetooth() {
        viewModelScope.launch {
            val newState = !_uiState.value.isBluetoothEnabled
            deviceController.setBluetoothEnabled(newState)
            _uiState.update { it.copy(isBluetoothEnabled = newState) }
        }
    }

    fun setMediaVolume(volume: Int) {
        viewModelScope.launch {
            deviceController.setVolume(AudioStream.MEDIA, volume)
            _uiState.update { it.copy(mediaVolume = volume) }
        }
    }

    fun setBrightness(brightness: Int) {
        viewModelScope.launch {
            deviceController.setBrightness(brightness)
            _uiState.update { it.copy(brightness = brightness) }
        }
    }

    fun toggleRule(ruleId: String) {
        viewModelScope.launch {
            try {
                val rule = automationRules.find { it.id == ruleId }
                if (rule != null) {
                    if (rule.isEnabled) {
                        deviceRepository.disableRule(ruleId)
                    } else {
                        deviceRepository.enableRule(ruleId)
                    }
                }
            } catch (e: Exception) {
                // 处理错误
            }
        }
    }

    fun deleteRule(ruleId: String) {
        viewModelScope.launch {
            try {
                deviceRepository.deleteRule(ruleId)
            } catch (e: Exception) {
                // 处理错误
            }
        }
    }
}

data class DeviceUiState(
    val isWifiEnabled: Boolean = false,
    val isBluetoothEnabled: Boolean = false,
    val batteryLevel: Int = 100,
    val isCharging: Boolean = false,
    val mediaVolume: Int = 10,
    val maxMediaVolume: Int = 15,
    val brightness: Int = 100
)
