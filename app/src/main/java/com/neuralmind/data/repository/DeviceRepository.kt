package com.neuralmind.data.repository

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.neuralmind.core.Logger
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
import com.neuralmind.domain.model.SendSmsAction
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
        Logger.d(Logger.Tags.REPO, "getDeviceStatus() called")
        try {
            val deviceInfo = deviceController.getDeviceInfo()
            val storageInfo = deviceController.getStorageInfo()
            Logger.d(Logger.Tags.REPO, "getDeviceStatus success: battery=${deviceController.getBatteryLevel()}%")
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
        } catch (e: Exception) {
            Logger.e(Logger.Tags.REPO, "getDeviceStatus failed", e)
            throw e
        }
    }
    
    suspend fun executeAction(action: DeviceAction) = withContext(Dispatchers.IO) {
        Logger.d(Logger.Tags.REPO, "executeAction(type=${action::class.simpleName})")
        try {
            when (action) {
                is WifiAction -> {
                    Logger.d(Logger.Tags.REPO, "executeAction: WifiAction(enable=${action.enable})")
                    deviceController.setWifiEnabled(action.enable)
                }
                is BluetoothAction -> {
                    Logger.d(Logger.Tags.REPO, "executeAction: BluetoothAction(enable=${action.enable})")
                    deviceController.setBluetoothEnabled(action.enable)
                }
                is BrightnessAction -> {
                    Logger.d(Logger.Tags.REPO, "executeAction: BrightnessAction(level=${action.level})")
                    deviceController.setBrightness(action.level)
                }
                is VolumeAction -> {
                    Logger.d(Logger.Tags.REPO, "executeAction: VolumeAction(stream=${action.stream}, level=${action.level})")
                    deviceController.setVolume(action.stream, action.level)
                }
                is LaunchAppAction -> {
                    Logger.d(Logger.Tags.REPO, "executeAction: LaunchAppAction(package=${action.packageName})")
                    deviceController.launchApp(action.packageName)
                }
                is SendSmsAction -> {
                    Logger.d(Logger.Tags.REPO, "executeAction: SendSmsAction(phone=${action.phoneNumber})")
                    sendSms(action.phoneNumber, action.message)
                }
                else -> {
                    Logger.w(Logger.Tags.REPO, "executeAction: Unhandled action type: ${action::class.simpleName}")
                }
            }
            Logger.i(Logger.Tags.REPO, "executeAction success: ${action::class.simpleName}")
        } catch (e: Exception) {
            Logger.e(Logger.Tags.REPO, "executeAction failed: ${action::class.simpleName}", e)
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
            Logger.i(Logger.Tags.REPO, "SMS sent to $phoneNumber")
        } catch (e: Exception) {
            Logger.e(Logger.Tags.REPO, "Failed to send SMS: ${e.message}")
        }
    }
    
    fun getAllRules(): Flow<List<AutomationRule>> {
        Logger.d(Logger.Tags.REPO, "getAllRules() called")
        return automationRuleDao.getAllRules().map { entities ->
            entities.map { it.toDomain() }
        }
    }
    
    fun getEnabledRules(): Flow<List<AutomationRule>> {
        Logger.d(Logger.Tags.REPO, "getEnabledRules() called")
        return automationRuleDao.getEnabledRules().map { entities ->
            entities.map { it.toDomain() }
        }
    }
    
    suspend fun addRule(rule: AutomationRule) {
        Logger.d(Logger.Tags.REPO, "addRule(name=${rule.name})")
        try {
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
            Logger.i(Logger.Tags.REPO, "addRule success: ${rule.name}")
        } catch (e: Exception) {
            Logger.e(Logger.Tags.REPO, "addRule failed: ${rule.name}", e)
        }
    }
    
    suspend fun deleteRule(ruleId: String) {
        Logger.d(Logger.Tags.REPO, "deleteRule(ruleId=$ruleId)")
        try {
            automationRuleDao.getRuleById(ruleId)?.let { rule ->
                automationRuleDao.delete(rule)
                Logger.i(Logger.Tags.REPO, "deleteRule success: $ruleId")
            } ?: Logger.w(Logger.Tags.REPO, "deleteRule: rule not found, id=$ruleId")
        } catch (e: Exception) {
            Logger.e(Logger.Tags.REPO, "deleteRule failed: $ruleId", e)
        }
    }
    
    suspend fun updateRule(rule: AutomationRule) {
        Logger.d(Logger.Tags.REPO, "updateRule(id=${rule.id})")
        try {
            val entity = automationRuleDao.getRuleById(rule.id)
            entity?.let {
                val updated = it.copy(
                    name = rule.name,
                    description = rule.description,
                    isEnabled = rule.isEnabled,
                    updatedAt = System.currentTimeMillis()
                )
                automationRuleDao.update(updated)
                Logger.i(Logger.Tags.REPO, "updateRule success: ${rule.id}")
            } ?: Logger.w(Logger.Tags.REPO, "updateRule: rule not found, id=${rule.id}")
        } catch (e: Exception) {
            Logger.e(Logger.Tags.REPO, "updateRule failed: ${rule.id}", e)
        }
    }
    
    suspend fun enableRule(ruleId: String) {
        Logger.d(Logger.Tags.REPO, "enableRule(ruleId=$ruleId)")
        try {
            automationRuleDao.setEnabled(ruleId, true)
            Logger.i(Logger.Tags.REPO, "enableRule success: $ruleId")
        } catch (e: Exception) {
            Logger.e(Logger.Tags.REPO, "enableRule failed: $ruleId", e)
        }
    }
    
    suspend fun disableRule(ruleId: String) {
        Logger.d(Logger.Tags.REPO, "disableRule(ruleId=$ruleId)")
        try {
            automationRuleDao.setEnabled(ruleId, false)
            Logger.i(Logger.Tags.REPO, "disableRule success: $ruleId")
        } catch (e: Exception) {
            Logger.e(Logger.Tags.REPO, "disableRule failed: $ruleId", e)
        }
    }
    
    suspend fun executeRule(ruleId: String) {
        Logger.d(Logger.Tags.REPO, "executeRule(ruleId=$ruleId)")
        try {
            automationRuleDao.recordTrigger(ruleId, System.currentTimeMillis())
            Logger.i(Logger.Tags.REPO, "executeRule success: $ruleId")
        } catch (e: Exception) {
            Logger.e(Logger.Tags.REPO, "executeRule failed: $ruleId", e)
        }
    }
    
    suspend fun insertDefaultRules() {
        Logger.d(Logger.Tags.REPO, "insertDefaultRules() called")
        try {
            if (automationRuleDao.getRuleById("night-mode") != null) {
                Logger.d(Logger.Tags.REPO, "Default rules already exist, skipping")
                return
            }
            
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
            Logger.i(Logger.Tags.REPO, "insertDefaultRules success: ${defaultRules.size} rules inserted")
        } catch (e: Exception) {
            Logger.e(Logger.Tags.REPO, "insertDefaultRules failed", e)
        }
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
            Logger.e(Logger.Tags.REPO, "parseTriggers failed", e)
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
            Logger.e(Logger.Tags.REPO, "parseActions failed", e)
            emptyList()
        }
    }
}
