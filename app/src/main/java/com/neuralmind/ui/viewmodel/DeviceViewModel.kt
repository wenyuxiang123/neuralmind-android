package com.neuralmind.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neuralmind.core.Logger
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
        Logger.d(Logger.Tags.VM, "refreshDeviceState()")
        viewModelScope.launch {
            try {
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
                Logger.i(Logger.Tags.VM, "refreshDeviceState success: battery=${_uiState.value.batteryLevel}%")
            } catch (e: Exception) {
                Logger.e(Logger.Tags.VM, "refreshDeviceState failed", e)
            }
        }
    }
    
    /**
     * 打开 WiFi 设置页面
     */
    fun openWifiSettings() {
        Logger.d(Logger.Tags.VM, "openWifiSettings()")
        deviceController.openWifiSettings()
        // 延迟刷新状态，因为用户可能在设置中改变了状态
        viewModelScope.launch {
            kotlinx.coroutines.delay(1000)
            refreshDeviceState()
        }
    }
    
    /**
     * 打开蓝牙设置页面
     */
    fun openBluetoothSettings() {
        Logger.d(Logger.Tags.VM, "openBluetoothSettings()")
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
        Logger.d(Logger.Tags.VM, "openSoundSettings()")
        deviceController.openSoundSettings()
    }
    
    /**
     * 打开显示设置页面
     */
    fun openDisplaySettings() {
        Logger.d(Logger.Tags.VM, "openDisplaySettings()")
        deviceController.openDisplaySettings()
    }
    
    // 保留旧的方法名以保持兼容性，内部调用新的方法
    fun toggleWifi() {
        Logger.d(Logger.Tags.VM, "toggleWifi()")
        openWifiSettings()
    }
    
    fun toggleBluetooth() {
        Logger.d(Logger.Tags.VM, "toggleBluetooth()")
        openBluetoothSettings()
    }
    
    fun setMediaVolume(volume: Int) {
        Logger.d(Logger.Tags.VM, "setMediaVolume(volume=$volume)")
        viewModelScope.launch {
            try {
                deviceController.setVolume(AudioStream.MEDIA, volume)
                _uiState.update { it.copy(mediaVolume = volume) }
                Logger.i(Logger.Tags.VM, "setMediaVolume success: $volume")
            } catch (e: Exception) {
                Logger.e(Logger.Tags.VM, "setMediaVolume failed", e)
            }
        }
    }
    
    fun setBrightness(brightness: Int) {
        // 由于需要 WRITE_SETTINGS 权限，改为打开设置
        Logger.d(Logger.Tags.VM, "setBrightness(brightness=$brightness) - opening settings")
        openDisplaySettings()
    }
    
    fun toggleRule(ruleId: String) {
        Logger.d(Logger.Tags.VM, "toggleRule(ruleId=$ruleId)")
        viewModelScope.launch {
            try {
                val rule = automationRules.find { it.id == ruleId }
                if (rule != null) {
                    if (rule.isEnabled) {
                        deviceRepository.disableRule(ruleId)
                    } else {
                        deviceRepository.enableRule(ruleId)
                    }
                    Logger.i(Logger.Tags.VM, "toggleRule success: $ruleId")
                }
            } catch (e: Exception) {
                Logger.e(Logger.Tags.VM, "toggleRule failed: $ruleId", e)
            }
        }
    }
    
    fun deleteRule(ruleId: String) {
        Logger.d(Logger.Tags.VM, "deleteRule(ruleId=$ruleId)")
        viewModelScope.launch {
            try {
                deviceRepository.deleteRule(ruleId)
                Logger.i(Logger.Tags.VM, "deleteRule success: $ruleId")
            } catch (e: Exception) {
                Logger.e(Logger.Tags.VM, "deleteRule failed: $ruleId", e)
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
