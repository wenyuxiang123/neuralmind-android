package com.neuralmind.data.repository

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.neuralmind.data.local.db.dao.AutomationRuleDao
import com.neuralmind.data.local.db.entity.AutomationRuleEntity
import com.neuralmind.device.DeviceController
import com.neuralmind.domain.model.AutomationRule
import com.neuralmind.domain.model.BluetoothAction
import com.neuralmind.domain.model.BrightnessAction
import com.neuralmind.domain.model.DeviceAction
import com.neuralmind.domain.model.DeviceStatus
import com.neuralmind.domain.model.LaunchAppAction
import com.neuralmind.domain.model.LocationTrigger
import com.neuralmind.domain.model.TimeTrigger
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
    private val gson = Gson()

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
            is SendSmsAction -> sendSms(action.phoneNumber, action.message)
            else -> {
                // Log unhandled action types for future implementation
                android.util.Log.w("DeviceRepository", "Unhandled action type: ${action::class.simpleName}")
            }
        }
    }

    /**
     * Send SMS using Android SmsManager.
     * Requires android.permission.SEND_SMS permission.
     */
    @Suppress("DEPRECATION")
    private suspend fun sendSms(phoneNumber: String, message: String) {
        try {
            val smsManager = android.telephony.SmsManager.getDefault()
            val parts = smsManager.divideMessage(message)
            if (parts.size == 1) {
                smsManager.sendTextMessage(phoneNumber, null, message, null, null)
            } else {
                smsManager.sendMultipartTextMessage(phoneNumber, null, parts, null, null)
            }
            android.util.Log.d("DeviceRepository", "SMS sent to $phoneNumber")
        } catch (e: Exception) {
            android.util.Log.e("DeviceRepository", "Failed to send SMS: ${e.message}")
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
        val triggers = parseTriggers(triggersJson)
        val actions = parseActions(actionsJson)
        
        return AutomationRule(
            id = id,
            name = name,
            description = description,
            triggers = triggers,
            conditions = emptyList(),
            actions = actions,
            isEnabled = isEnabled,
            lastTriggered = lastTriggered,
            triggerCount = triggerCount,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }

    private fun parseTriggers(json: String): List<com.neuralmind.domain.model.Trigger> {
        return try {
            val array = gson.fromJson(json, JsonArray::class.java) ?: return emptyList()
            array.mapNotNull { element ->
                val obj = element.asJsonObject
                when (obj.get("type")?.asString) {
                    "time" -> TimeTrigger(
                        id = obj.get("id")?.asString ?: "",
                        name = obj.get("name")?.asString ?: "时间触发",
                        time = obj.get("time")?.asString ?: "",
                        repeat = com.neuralmind.domain.model.RepeatMode.valueOf(
                            obj.get("repeat")?.asString ?: "ONCE"
                        )
                    )
                    "location" -> LocationTrigger(
                        id = obj.get("id")?.asString ?: "",
                        name = obj.get("name")?.asString ?: "位置触发",
                        latitude = obj.get("latitude")?.asDouble ?: 0.0,
                        longitude = obj.get("longitude")?.asDouble ?: 0.0,
                        radius = obj.get("radius")?.asFloat ?: 100f,
                        event = com.neuralmind.domain.model.LocationEvent.valueOf(
                            obj.get("event")?.asString ?: "ENTER"
                        )
                    )
                    else -> null
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun parseActions(json: String): List<DeviceAction> {
        return try {
            val array = gson.fromJson(json, JsonArray::class.java) ?: return emptyList()
            array.mapNotNull { element ->
                val obj = element.asJsonObject
                when (obj.get("type")?.asString) {
                    "wifi" -> WifiAction(obj.get("enable")?.asBoolean ?: true)
                    "bluetooth" -> BluetoothAction(obj.get("enable")?.asBoolean ?: true)
                    "brightness" -> BrightnessAction(obj.get("level")?.asInt ?: 50)
                    "volume" -> VolumeAction(
                        stream = com.neuralmind.device.AudioStream.valueOf(
                            obj.get("stream")?.asString ?: "MEDIA"
                        ),
                        level = obj.get("level")?.asInt ?: 10
                    )
                    "launch_app" -> LaunchAppAction(
                        obj.get("packageName")?.asString ?: ""
                    )
                    else -> null
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
