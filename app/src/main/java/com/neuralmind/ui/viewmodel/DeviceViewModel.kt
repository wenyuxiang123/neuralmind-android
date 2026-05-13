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

/**
 * 设备控制 ViewModel
 * 
 * 由于 Android 权限限制，WiFi、蓝牙、亮度等控制需要打开系统设置页面让用户操作。
 * 这些方法会调用 DeviceController 中对应的 openXxxSettings 方法。
 */
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

    /**
     * 打开 WiFi 设置页面
     * 由于 Android 10+ 限制应用直接控制 WiFi，改为打开系统设置
     */
    fun openWifiSettings() {
        deviceController.openWifiSettings()
        // 延迟刷新状态，因为用户可能在设置中改变了状态
        viewModelScope.launch {
            kotlinx.coroutines.delay(1000)
            refreshDeviceState()
        }
    }

    /**
     * 打开蓝牙设置页面
     * 由于权限限制，改为打开系统设置
     */
    fun openBluetoothSettings() {
        deviceController.openBluetoothSettings()
        // 延迟刷新状态
        viewModelScope.launch {
            kotlinx.coroutines.delay(1000)
            refreshDeviceState()
        }
    }

    /**
     * 打开音量设置页面
     */
    fun openSoundSettings() {
        deviceController.openSoundSettings()
    }

    /**
     * 打开显示设置页面
     * 由于权限限制，改为打开系统设置
     */
    fun openDisplaySettings() {
        deviceController.openDisplaySettings()
    }

    // 保留旧的方法名以保持兼容性，内部调用新的方法
    fun toggleWifi() {
        openWifiSettings()
    }

    fun toggleBluetooth() {
        openBluetoothSettings()
    }

    fun setMediaVolume(volume: Int) {
        viewModelScope.launch {
            deviceController.setVolume(AudioStream.MEDIA, volume)
            _uiState.update { it.copy(mediaVolume = volume) }
        }
    }

    fun setBrightness(brightness: Int) {
        // 由于需要 WRITE_SETTINGS 权限，改为打开设置
        openDisplaySettings()
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
