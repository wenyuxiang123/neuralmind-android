package com.neuralmind.device

import android.app.NotificationManager
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Context.NOTIFICATION_SERVICE
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

@Singleton
class DeviceController @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val notificationManager = context.getSystemService(NOTIFICATION_SERVICE) as NotificationManager

    fun isWifiEnabled(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val wifiState = connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
            wifiState?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) == true
        } else {
            @Suppress("DEPRECATION")
            wifiManager.isWifiEnabled
        }
    }

    fun setWifiEnabled(enabled: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10+ 需要用户手动操作
        } else {
            @Suppress("DEPRECATION")
            wifiManager.isWifiEnabled = enabled
        }
    }

    fun isBluetoothEnabled(): Boolean = bluetoothAdapter?.isEnabled == true

    fun setBluetoothEnabled(enabled: Boolean) {
        if (enabled) {
            bluetoothAdapter?.enable()
        } else {
            bluetoothAdapter?.disable()
        }
    }

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

    fun getBrightness(): Int {
        return try {
            Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
        } catch (e: Exception) {
            100
        }
    }

    fun setBrightness(brightness: Int) {
        try {
            Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, brightness)
        } catch (e: Exception) {
        }
    }

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

    fun setAirplaneMode(enabled: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            // Android 4.2+ 需要用户手动操作
        } else {
            @Suppress("DEPRECATION")
            Settings.Global.putInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, if (enabled) 1 else 0)
        }
    }

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
