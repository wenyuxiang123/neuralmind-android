package com.neuralmind.data.repository

import com.neuralmind.data.local.db.dao.AutomationRuleDao
import com.neuralmind.data.local.db.entity.AutomationRuleEntity
import com.neuralmind.device.DeviceController
import com.neuralmind.domain.model.AutomationRule
import com.neuralmind.domain.model.BluetoothAction
import com.neuralmind.domain.model.BrightnessAction
import com.neuralmind.domain.model.DeviceAction
import com.neuralmind.domain.model.DeviceStatus
import com.neuralmind.domain.model.LaunchAppAction
import com.neuralmind.domain.model.VolumeAction
import com.neuralmind.domain.model.WifiAction
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeviceRepository @Inject constructor(
    private val deviceController: DeviceController,
    private val automationRuleDao: AutomationRuleDao,
    @ApplicationContext private val context: android.content.Context
) {
    suspend fun getDeviceStatus(): DeviceStatus = withContext(Dispatchers.IO) {
        val deviceInfo = deviceController.getDeviceInfo()
        val storageInfo = deviceController.getStorageInfo()
        DeviceStatus(
            wifiEnabled = deviceController.isWifiEnabled(),
            bluetoothEnabled = deviceController.isBluetoothEnabled(),
            batteryLevel = deviceController.getBatteryLevel(),
            isCharging = deviceController.isCharging(),
            brightness = deviceController.getBrightness(),
            mediaVolume = deviceController.getVolume(com.neuralmind.device.AudioStream.MEDIA),
            ringVolume = deviceController.getVolume(com.neuralmind.device.AudioStream.RING),
            deviceModel = deviceInfo.model,
            androidVersion = deviceInfo.version,
            totalMemory = deviceInfo.totalMemory,
            availableMemory = deviceInfo.availableMemory,
            totalStorage = storageInfo.totalStorage,
            availableStorage = storageInfo.availableStorage
        )
    }

    suspend fun executeAction(action: DeviceAction) = withContext(Dispatchers.IO) {
        when (action) {
            is WifiAction -> deviceController.setWifiEnabled(action.enable)
            is BluetoothAction -> deviceController.setBluetoothEnabled(action.enable)
            is BrightnessAction -> deviceController.setBrightness(action.level)
            is VolumeAction -> deviceController.setVolume(action.stream, action.level)
            is LaunchAppAction -> deviceController.launchApp(action.packageName)
            else -> {}
        }
    }

    fun getAllRules(): Flow<List<AutomationRule>> {
        return automationRuleDao.getAllRules().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    fun getEnabledRules(): Flow<List<AutomationRule>> {
        return automationRuleDao.getEnabledRules().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    suspend fun addRule(rule: AutomationRule) {
        val entity = AutomationRuleEntity(
            id = rule.id,
            name = rule.name,
            description = rule.description,
            isEnabled = rule.isEnabled,
            lastTriggered = rule.lastTriggered,
            triggerCount = rule.triggerCount,
            createdAt = rule.createdAt,
            updatedAt = rule.updatedAt
        )
        automationRuleDao.insert(entity)
    }

    suspend fun deleteRule(ruleId: String) {
        automationRuleDao.getRuleById(ruleId)?.let { rule ->
            automationRuleDao.delete(rule)
        }
    }

    suspend fun updateRule(rule: AutomationRule) {
        val entity = automationRuleDao.getRuleById(rule.id)
        entity?.let {
            val updated = it.copy(
                name = rule.name,
                description = rule.description,
                isEnabled = rule.isEnabled,
                updatedAt = System.currentTimeMillis()
            )
            automationRuleDao.update(updated)
        }
    }

    suspend fun enableRule(ruleId: String) {
        automationRuleDao.setEnabled(ruleId, true)
    }

    suspend fun disableRule(ruleId: String) {
        automationRuleDao.setEnabled(ruleId, false)
    }

    suspend fun executeRule(ruleId: String) {
        automationRuleDao.recordTrigger(ruleId, System.currentTimeMillis())
    }

    suspend fun insertDefaultRules() {
        if (automationRuleDao.getRuleById("night-mode") != null) return

        val defaultRules = listOf(
            AutomationRuleEntity(
                id = "night-mode",
                name = "夜间模式",
                description = "自动降低亮度",
                isEnabled = false,
                lastTriggered = null,
                triggerCount = 0,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            ),
            AutomationRuleEntity(
                id = "low-battery",
                name = "低电量模式",
                description = "电量低于20%时自动优化",
                isEnabled = true,
                lastTriggered = null,
                triggerCount = 0,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            ),
            AutomationRuleEntity(
                id = "morning-alarm",
                name = "早上好",
                description = "早晨自动开启WiFi",
                isEnabled = false,
                lastTriggered = null,
                triggerCount = 0,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
        )
        defaultRules.forEach { automationRuleDao.insert(it) }
    }

    private fun AutomationRuleEntity.toDomain(): AutomationRule {
        return AutomationRule(
            id = id,
            name = name,
            description = description,
            triggers = emptyList(),
            conditions = emptyList(),
            actions = emptyList(),
            isEnabled = isEnabled,
            lastTriggered = lastTriggered,
            triggerCount = triggerCount,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }
}
