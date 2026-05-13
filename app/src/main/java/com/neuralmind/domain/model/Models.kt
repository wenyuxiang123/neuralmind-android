package com.neuralmind.domain.model

data class Message(
    val id: Long = 0,
    val conversationId: Long,
    val role: MessageRole,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val model: String? = null,
    val tokens: Int? = null,
    val metadata: Map<String, String>? = null
)

enum class MessageRole {
    USER,
    ASSISTANT,
    SYSTEM
}

data class Conversation(
    val id: Long = 0,
    val title: String,
    val model: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val messageCount: Int = 0,
    val isPinned: Boolean = false,
    val isArchived: Boolean = false
)

data class AIModel(
    val id: String,
    val name: String,
    val description: String,
    val size: Long,
    val parameters: Int,
    val quantization: String,
    val category: ModelCategory,
    val downloadUrl: String,
    val checksum: String,
    val minRam: Long,
    val minStorage: Long,
    val recommendedRam: Long,
    val supportsGpu: Boolean,
    val supportsNnapi: Boolean,
    val isInstalled: Boolean = false,
    val isDownloading: Boolean = false,
    val downloadProgress: Float = 0f
)

enum class ModelCategory {
    TEXT,
    CODE,
    VISION,
    AUDIO,
    MOBILE
}

data class Memory(
    val id: Long = 0,
    val layer: MemoryLayer,
    val content: String,
    val category: String,
    val importance: Int,
    val accessCount: Int = 0,
    val lastAccessed: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isActive: Boolean = true
)

enum class MemoryLayer(val level: Int, val description: String, val capacity: Int) {
    L1_WORKING(1, "当前对话上下文", 2),
    L2_SHORT_TERM(2, "最近对话记录", 10),
    L3_SESSION(3, "本次会话完整记录", 100),
    L4_SCHEDULE(4, "用户日程和事件", 50),
    L5_PERSONAL(5, "用户基本信息", 20),
    L6_PREFERENCE(6, "用户偏好设置", 30),
    L7_KNOWLEDGE(7, "用户知识技能", 100),
    L8_HABIT(8, "用户行为习惯", 50),
    L9_DEEP(9, "核心价值观和目标", 20)
}

data class Skill(
    val id: String,
    val name: String,
    val description: String,
    val icon: String,
    val category: SkillCategory,
    val version: String,
    val author: String,
    val permissions: List<String>,
    val isInstalled: Boolean = false,
    val isBuiltIn: Boolean = true,
    val downloadUrl: String? = null,
    val installedSize: Long = 0
)

enum class SkillCategory {
    SYSTEM,
    UTILITY,
    PRODUCTIVITY,
    LIFESTYLE,
    DEVELOPMENT
}

data class DeviceControl(
    val type: ControlType,
    val name: String,
    val isEnabled: Boolean,
    val value: Any? = null
)

enum class ControlType {
    WIFI,
    BLUETOOTH,
    MOBILE_DATA,
    LOCATION,
    AIRPLANE_MODE,
    HOTSPOT,
    NFC,
    FLASHLIGHT,
    SCREEN_TIMEOUT,
    BRIGHTNESS,
    VOLUME_MEDIA,
    VOLUME_RING,
    VOLUME_ALARM,
    VOLUME_NOTIFICATION
}

data class AutomationRule(
    val id: String,
    val name: String,
    val description: String,
    val triggers: List<Trigger>,
    val conditions: List<Condition>,
    val actions: List<DeviceAction>,
    val isEnabled: Boolean = true,
    val lastTriggered: Long? = null,
    val triggerCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

sealed class Trigger {
    abstract val id: String
    abstract val name: String
}

data class TimeTrigger(
    override val id: String = "",
    override val name: String = "时间触发",
    val time: String,
    val repeat: RepeatMode = RepeatMode.ONCE
) : Trigger()

enum class RepeatMode {
    ONCE,
    DAILY,
    WEEKLY,
    MONTHLY,
    CUSTOM
}

data class LocationTrigger(
    override val id: String = "",
    override val name: String = "位置触发",
    val latitude: Double,
    val longitude: Double,
    val radius: Float,
    val event: LocationEvent = LocationEvent.ENTER
) : Trigger()

enum class LocationEvent {
    ENTER,
    EXIT
}

data class BatteryTrigger(
    override val id: String = "",
    override val name: String = "电量触发",
    val threshold: Int,
    val event: BatteryEvent = BatteryEvent.BELOW
) : Trigger()

enum class BatteryEvent {
    BELOW,
    ABOVE
}

sealed class Condition
sealed class DeviceAction

data class WifiAction(val enable: Boolean) : DeviceAction()
data class BluetoothAction(val enable: Boolean) : DeviceAction()
data class BrightnessAction(val level: Int) : DeviceAction()
data class VolumeAction(val stream: com.neuralmind.device.AudioStream, val level: Int) : DeviceAction()
data class LaunchAppAction(val packageName: String) : DeviceAction()
data class SendSmsAction(val phoneNumber: String, val message: String) : DeviceAction()
data class SetAlarmAction(val time: String, val message: String) : DeviceAction()

data class ToolModule(
    val id: String,
    val name: String,
    val description: String,
    val version: String,
    val category: ToolCategory,
    val downloadSize: Long,
    val installedSize: Long,
    val icon: String,
    val downloadUrl: String,
    val isInstalled: Boolean = false,
    val isDownloading: Boolean = false,
    val downloadProgress: Float = 0f
)

enum class ToolCategory {
    EDITOR,
    TERMINAL,
    GIT,
    DATABASE,
    API_TESTER,
    FILE_MANAGER,
    NETWORK,
    PERFORMANCE,
    LOG_VIEWER
}

data class DeviceStatus(
    val wifiEnabled: Boolean,
    val bluetoothEnabled: Boolean,
    val batteryLevel: Int,
    val isCharging: Boolean,
    val brightness: Int,
    val mediaVolume: Int,
    val ringVolume: Int,
    val deviceModel: String,
    val androidVersion: String,
    val totalMemory: Long,
    val availableMemory: Long,
    val totalStorage: Long,
    val availableStorage: Long
)
