package com.neuralmind.device

import android.app.NotificationManager
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Context.NOTIFICATION_SERVICE
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings
import android.media.AudioManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 设备控制器
 * 
 * 注意：由于 Android 权限限制，以下功能需要用户手动在系统设置中操作：
 * - WiFi 开关 (Android 10+)
 * - 蓝牙开关 (需要 BLUETOOTH 权限)
 * - 亮度调节 (需要 WRITE_SETTINGS 权限)
 * - 飞行模式 (Android 4.2+)
 * 
 * 为了用户体验，这些操作会打开对应的系统设置页面让用户操作。
 */
@Singleton
class DeviceController @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val notificationManager = context.getSystemService(NOTIFICATION_SERVICE) as NotificationManager

    // ========== WiFi 相关 ==========

    fun isWifiEnabled(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val wifiState = connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
            wifiState?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) == true
        } else {
            @Suppress("DEPRECATION")
            wifiManager.isWifiEnabled
        }
    }

    /**
     * 打开 WiFi 设置页面
     * 由于 Android 10+ 限制应用直接控制 WiFi，改为打开系统设置
     */
    fun openWifiSettings() {
        val intent = Intent(Settings.ACTION_WIFI_SETTINGS)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    // 保留旧方法以保持兼容性，内部调用打开设置
    fun setWifiEnabled(enabled: Boolean) {
        // Android 10+ 无法直接控制 WiFi，改为打开设置
        openWifiSettings()
    }

    // ========== 蓝牙相关 ==========

    fun isBluetoothEnabled(): Boolean = bluetoothAdapter?.isEnabled == true

    /**
     * 打开蓝牙设置页面
     * 由于需要 BLUETOOTH 权限才能控制蓝牙，改为打开系统设置
     */
    fun openBluetoothSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ 使用新的蓝牙设置
            val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } else {
            val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }

    // 保留旧方法以保持兼容性，内部调用打开设置
    fun setBluetoothEnabled(enabled: Boolean) {
        // 需要 BLUETOOTH 权限才能直接控制，改为打开设置
        openBluetoothSettings()
    }

    // ========== 音量相关 ==========

    fun getWifiInfo(): WifiInfo {
        val wifiInfo = wifiManager.connectionInfo
        return WifiInfo(
            ssid = wifiInfo.ssid,
            bssid = wifiInfo.bssid,
            rssi = wifiInfo.rssi,
            networkId = wifiInfo.networkId,
            ipAddress = wifiInfo.ipAddress
        )
    }

    fun getVolume(stream: AudioStream): Int {
        val streamType = when (stream) {
            AudioStream.MEDIA -> AudioManager.STREAM_MUSIC
            AudioStream.RING -> AudioManager.STREAM_RING
            AudioStream.ALARM -> AudioManager.STREAM_ALARM
            AudioStream.NOTIFICATION -> AudioManager.STREAM_NOTIFICATION
        }
        return audioManager.getStreamVolume(streamType)
    }

    fun getMaxVolume(stream: AudioStream): Int {
        val streamType = when (stream) {
            AudioStream.MEDIA -> AudioManager.STREAM_MUSIC
            AudioStream.RING -> AudioManager.STREAM_RING
            AudioStream.ALARM -> AudioManager.STREAM_ALARM
            AudioStream.NOTIFICATION -> AudioManager.STREAM_NOTIFICATION
        }
        return audioManager.getStreamMaxVolume(streamType)
    }

    /**
     * 打开音量设置页面
     */
    fun openSoundSettings() {
        val intent = Intent(Settings.ACTION_SOUND_SETTINGS)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    fun setVolume(stream: AudioStream, volume: Int) {
        val streamType = when (stream) {
            AudioStream.MEDIA -> AudioManager.STREAM_MUSIC
            AudioStream.RING -> AudioManager.STREAM_RING
            AudioStream.ALARM -> AudioManager.STREAM_ALARM
            AudioStream.NOTIFICATION -> AudioManager.STREAM_NOTIFICATION
        }
        audioManager.setStreamVolume(streamType, volume, 0)
    }

    fun setVibrationEnabled(enabled: Boolean) {
        if (enabled) {
            audioManager.ringerMode = AudioManager.RINGER_MODE_VIBRATE
        } else {
            audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL
        }
    }

    // ========== 亮度相关 ==========

    fun getBrightness(): Int {
        return try {
            Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
        } catch (e: Exception) {
            100
        }
    }

    /**
     * 打开显示设置页面
     * 由于需要 WRITE_SETTINGS 权限才能直接设置亮度，改为打开系统设置
     */
    fun openDisplaySettings() {
        val intent = Intent(Settings.ACTION_DISPLAY_SETTINGS)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    fun setBrightness(brightness: Int) {
        // 需要 WRITE_SETTINGS 权限才能直接设置亮度，改为打开设置
        openDisplaySettings()
    }

    // ========== 电池相关 ==========

    fun getBatteryLevel(): Int {
        val batteryIntent = context.registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
        val level = batteryIntent?.getIntExtra("level", -1) ?: -1
        val scale = batteryIntent?.getIntExtra("scale", 100) ?: 100
        return (level * 100 / scale)
    }

    fun isCharging(): Boolean {
        val batteryIntent = context.registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
        val status = batteryIntent?.getIntExtra("status", -1) ?: -1
        return status == android.os.BatteryManager.BATTERY_STATUS_CHARGING ||
                status == android.os.BatteryManager.BATTERY_STATUS_FULL
    }

    // ========== 飞行模式相关 ==========

    /**
     * 打开网络设置页面
     * 飞行模式需要系统签名权限，改为打开设置让用户操作
     */
    fun openNetworkSettings() {
        val intent = Intent(Settings.ACTION_NETWORK_OPERATOR_SETTINGS)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    fun setAirplaneMode(enabled: Boolean) {
        // Android 4.2+ 需要系统签名权限，改为打开设置
        openNetworkSettings()
    }

    // ========== 应用相关 ==========

    suspend fun getInstalledApps(): List<AppInfo> = withContext(Dispatchers.IO) {
        val packageManager = context.packageManager
        val intent = android.content.Intent(android.content.Intent.ACTION_MAIN)
        intent.addCategory(android.content.Intent.CATEGORY_LAUNCHER)
        val resolveInfos = packageManager.queryIntentActivities(intent, 0)
        
        resolveInfos.map { resolveInfo ->
            AppInfo(
                packageName = resolveInfo.activityInfo.packageName,
                name = resolveInfo.loadLabel(packageManager).toString(),
                icon = resolveInfo.loadIcon(packageManager)
            )
        }
    }

    fun launchApp(packageName: String) {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName)
        intent?.let {
            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(it)
        }
    }

    // ========== 设备信息 ==========

    fun getDeviceInfo(): DeviceInfo {
        return DeviceInfo(
            model = Build.MODEL,
            brand = Build.BRAND,
            version = Build.VERSION.RELEASE,
            sdkVersion = Build.VERSION.SDK_INT,
            totalMemory = getTotalMemory(),
            availableMemory = getAvailableMemory()
        )
    }

    private fun getTotalMemory(): Long {
        val runtime = Runtime.getRuntime()
        return runtime.maxMemory()
    }

    private fun getAvailableMemory(): Long {
        val runtime = Runtime.getRuntime()
        return runtime.freeMemory()
    }

    fun getStorageInfo(): StorageInfo {
        val externalStorage = android.os.Environment.getExternalStorageDirectory()
        val stat = android.os.StatFs(externalStorage.path)
        val blockSize = stat.blockSizeLong
        val availableBlocks = stat.availableBlocksLong
        val totalBlocks = stat.blockCountLong
        return StorageInfo(
            totalStorage = totalBlocks * blockSize,
            availableStorage = availableBlocks * blockSize
        )
    }
}

enum class AudioStream {
    MEDIA,
    RING,
    ALARM,
    NOTIFICATION
}

data class WifiInfo(
    val ssid: String?,
    val bssid: String?,
    val rssi: Int,
    val networkId: Int,
    val ipAddress: Int
)

data class AppInfo(
    val packageName: String,
    val name: String,
    val icon: android.graphics.drawable.Drawable?
)

data class DeviceInfo(
    val model: String,
    val brand: String,
    val version: String,
    val sdkVersion: Int,
    val totalMemory: Long,
    val availableMemory: Long
)

data class StorageInfo(
    val totalStorage: Long,
    val availableStorage: Long
)
