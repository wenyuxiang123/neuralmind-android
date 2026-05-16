package com.neuralmind.core

import android.app.ActivityManager
import android.content.Context
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class MemoryMonitor(private val context: Context) {
    private val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    private val _memoryPressure = MutableStateFlow(false)
    val memoryPressure: StateFlow<Boolean> = _memoryPressure.asStateFlow()
    
    private val _memoryUsagePercent = MutableStateFlow(0f)
    val memoryUsagePercent: StateFlow<Float> = _memoryUsagePercent.asStateFlow()
    
    fun startMonitoring(intervalMs: Long = 5000L) {
        scope.launch {
            while (isActive) {
                val info = ActivityManager.MemoryInfo()
                activityManager.getMemoryInfo(info)
                val usedPercent = (info.totalMem - info.availMem).toFloat() / info.totalMem.toFloat() * 100f
                _memoryUsagePercent.value = usedPercent
                _memoryPressure.value = usedPercent >= 80f
                if (usedPercent >= 80f) {
                    Logger.w(Logger.Tags.VM, "Memory pressure detected: ${usedPercent.toInt()}%")
                }
                delay(intervalMs)
            }
        }
    }
    
    fun stopMonitoring() {
        scope.cancel()
    }
    
    fun isMemoryPressure(): Boolean = _memoryUsagePercent.value >= 80f
    
    fun getMemoryUsagePercent(): Float = _memoryUsagePercent.value
}
