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
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "DeviceController"

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
 * 所有系统服务获取均有 fallback，避免初始化崩溃。
 */
@Singleton
class DeviceController @Inject constructor(
    @ApplicationContext private val context: Context
) {
    // 懒加载系统服务，避免初始化崩溃
    private val wifiManager: WifiManager? by lazy {
        try {
            context.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get WifiManager", e)
            null
        }
    }
    
    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        try {
            BluetoothAdapter.getDefaultAdapter()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get BluetoothAdapter", e)
            null
        }
    }
    
    private val audioManager: AudioManager by lazy {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }
    
    private val connectivityManager: ConnectivityManager by lazy {
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    }
    
    private val notificationManager: NotificationManager by lazy {
        context.getSystemService(NOTIFICATION_SERVICE) as NotificationManager
    }

    // ========== WiFi 相关 ==========

    fun isWifiEnabled(): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val wifiState = connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
                wifiState?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) == true
            } else {
                @Suppress("DEPRECATION")
                wifiManager?.isWifiEnabled == true
            }
        } catch (e: Exception) {
            Log.e(TAG, "isWifiEnabled failed", e)
            false
        }
    }

    /**
     * 打开 WiFi 设置页面
     * 由于 Android 10+ 限制应用直接控制 WiFi，改为打开系统设置
     */
    fun openWifiSettings() {
        try {
            val intent = Intent(Settings.ACTION_WIFI_SETTINGS)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "openWifiSettings failed", e)
        }
    }

    // 保留旧方法以保持兼容性
    fun setWifiEnabled(enabled: Boolean) {
        openWifiSettings()
    }

    // ========== 蓝牙相关 ==========

    fun isBluetoothEnabled(): Boolean {
        return try {
            bluetoothAdapter?.isEnabled == true
        } catch (e: Exception) {
            Log.e(TAG, "isBluetoothEnabled failed", e)
            false
        }
    }

    /**
     * 打开蓝牙设置页面
     */
    fun openBluetoothSettings() {
        try {
            val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "openBluetoothSettings failed", e)
        }
    }

    // 保留旧方法以保持兼容性
    fun setBluetoothEnabled(enabled: Boolean) {
        openBluetoothSettings()
    }

    // ========== 音量相关 ==========

    fun getWifiInfo(): WifiInfo {
        return try {
            val wifiInfo = wifiManager?.connectionInfo
            WifiInfo(
                ssid = wifiInfo?.ssid,
                bssid = wifiInfo?.bssid,
                rssi = wifiInfo?.rssi ?: 0,
                networkId = wifiInfo?.networkId ?: -1,
                ipAddress = wifiInfo?.ipAddress ?: 0
            )
        } catch (e: Exception) {
            Log.e(TAG, "getWifiInfo failed", e)
            WifiInfo(null, null, 0, -1, 0)
        }
    }

    fun getVolume(stream: AudioStream): Int {
        return try {
            val streamType = when (stream) {
                AudioStream.MEDIA -> AudioManager.STREAM_MUSIC
                AudioStream.RING -> AudioManager.STREAM_RING
                AudioStream.ALARM -> AudioManager.STREAM_ALARM
                AudioStream.NOTIFICATION -> AudioManager.STREAM_NOTIFICATION
            }
            audioManager.getStreamVolume(streamType)
        } catch (e: Exception) {
            Log.e(TAG, "getVolume failed", e)
            0
        }
    }

    fun getMaxVolume(stream: AudioStream): Int {
        return try {
            val streamType = when (stream) {
                AudioStream.MEDIA -> AudioManager.STREAM_MUSIC
                AudioStream.RING -> AudioManager.STREAM_RING
                AudioStream.ALARM -> AudioManager.STREAM_ALARM
                AudioStream.NOTIFICATION -> AudioManager.STREAM_NOTIFICATION
            }
            audioManager.getStreamMaxVolume(streamType)
        } catch (e: Exception) {
            Log.e(TAG, "getMaxVolume failed", e)
            15
        }
    }

    fun openSoundSettings() {
        try {
            val intent = Intent(Settings.ACTION_SOUND_SETTINGS)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "openSoundSettings failed", e)
        }
    }

    fun setVolume(stream: AudioStream, volume: Int) {
        try {
            val streamType = when (stream) {
                AudioStream.MEDIA -> AudioManager.STREAM_MUSIC
                AudioStream.RING -> AudioManager.STREAM_RING
                AudioStream.ALARM -> AudioManager.STREAM_ALARM
                AudioStream.NOTIFICATION -> AudioManager.STREAM_NOTIFICATION
            }
            audioManager.setStreamVolume(streamType, volume, 0)
        } catch (e: Exception) {
            Log.e(TAG, "setVolume failed", e)
        }
    }

    fun setVibrationEnabled(enabled: Boolean) {
        try {
            if (enabled) {
                audioManager.ringerMode = AudioManager.RINGER_MODE_VIBRATE
            } else {
                audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL
            }
        } catch (e: Exception) {
            Log.e(TAG, "setVibrationEnabled failed", e)
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

    fun openDisplaySettings() {
        try {
            val intent = Intent(Settings.ACTION_DISPLAY_SETTINGS)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "openDisplaySettings failed", e)
        }
    }

    fun setBrightness(brightness: Int) {
        openDisplaySettings()
    }

    // ========== 电池相关 ==========

    fun getBatteryLevel(): Int {
        return try {
            val batteryIntent = context.registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
            val level = batteryIntent?.getIntExtra("level", -1) ?: -1
            val scale = batteryIntent?.getIntExtra("scale", 100) ?: 100
            if (level >= 0 && scale > 0) (level * 100 / scale) else 100
        } catch (e: Exception) {
            Log.e(TAG, "getBatteryLevel failed", e)
            100
        }
    }

    fun isCharging(): Boolean {
        return try {
            val batteryIntent = context.registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
            val status = batteryIntent?.getIntExtra("status", -1) ?: -1
            status == android.os.BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == android.os.BatteryManager.BATTERY_STATUS_FULL
        } catch (e: Exception) {
            Log.e(TAG, "isCharging failed", e)
            false
        }
    }

    // ========== 飞行模式相关 ==========

    fun openNetworkSettings() {
        try {
            val intent = Intent(Settings.ACTION_NETWORK_OPERATOR_SETTINGS)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "openNetworkSettings failed", e)
        }
    }

    fun setAirplaneMode(enabled: Boolean) {
        openNetworkSettings()
    }

    // ========== 应用相关 ==========

    suspend fun getInstalledApps(): List<AppInfo> = withContext(Dispatchers.IO) {
        try {
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
        } catch (e: Exception) {
            Log.e(TAG, "getInstalledApps failed", e)
            emptyList()
        }
    }

    fun launchApp(packageName: String) {
        try {
            val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            intent?.let {
                intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(it)
            }
        } catch (e: Exception) {
            Log.e(TAG, "launchApp failed", e)
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
        return try {
            val externalStorage = android.os.Environment.getExternalStorageDirectory()
            val stat = android.os.StatFs(externalStorage.path)
            val blockSize = stat.blockSizeLong
            val availableBlocks = stat.availableBlocksLong
            val totalBlocks = stat.blockCountLong
            StorageInfo(
                totalStorage = totalBlocks * blockSize,
                availableStorage = availableBlocks * blockSize
            )
        } catch (e: Exception) {
            Log.e(TAG, "getStorageInfo failed", e)
            StorageInfo(0L, 0L)
        }
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
