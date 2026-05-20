package com.neuralmind.core

import android.app.ActivityManager
import android.content.Context
import android.os.StatFs
import com.neuralmind.data.repository.ModelRepository
import com.neuralmind.domain.model.AIModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MemoryMonitor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val modelRepository: ModelRepository
) {
    private val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    private val _memoryPressure = MutableStateFlow(false)
    val memoryPressure: StateFlow<Boolean> = _memoryPressure.asStateFlow()
    
    private val _memoryUsagePercent = MutableStateFlow(0f)
    val memoryUsagePercent: StateFlow<Float> = _memoryUsagePercent.asStateFlow()
    
    private val _memoryInfo = MutableStateFlow(MemoryInfo())
    val memoryInfo: StateFlow<MemoryInfo> = _memoryInfo.asStateFlow()
    
    private val _recommendedModel = MutableStateFlow<AIModel?>(null)
    val recommendedModel: StateFlow<AIModel?> = _recommendedModel.asStateFlow()
    
    private val _inferenceTimeout = MutableStateFlow(false)
    val inferenceTimeout: StateFlow<Boolean> = _inferenceTimeout.asStateFlow()
    
    private var inferenceStartTime: Long = 0
    private var inferenceTimeoutJob: Job? = null
    private val defaultInferenceTimeoutMs = 60_000L
    
    data class MemoryInfo(
        val totalMem: Long = 0,
        val availMem: Long = 0,
        val usedMem: Long = 0,
        val usagePercent: Float = 0f,
        val isLowMemory: Boolean = false,
        val threshold: Long = 0,
        val totalStorage: Long = 0,
        val availStorage: Long = 0
    )
    
    init {
        updateMemoryInfo()
    }
    
    fun startMonitoring(intervalMs: Long = 5000L) {
        scope.launch {
            while (isActive) {
                updateMemoryInfo()
                checkMemoryPressure()
                recommendModel()
                delay(intervalMs)
            }
        }
    }
    
    fun stopMonitoring() {
        scope.cancel()
    }
    
    private fun updateMemoryInfo() {
        val info = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(info)
        
        val usedMem = info.totalMem - info.availMem
        val usagePercent = if (info.totalMem > 0) {
            (usedMem.toFloat() / info.totalMem.toFloat() * 100f)
        } else 0f
        
        val storageInfo = getStorageInfo()
        
        _memoryInfo.value = MemoryInfo(
            totalMem = info.totalMem,
            availMem = info.availMem,
            usedMem = usedMem,
            usagePercent = usagePercent,
            isLowMemory = info.lowMemory,
            threshold = info.threshold,
            totalStorage = storageInfo.first,
            availStorage = storageInfo.second
        )
    }
    
    private fun getStorageInfo(): Pair<Long, Long> {
        return try {
            val path = context.filesDir
            val stat = StatFs(path.absolutePath)
            val total = stat.blockSizeLong * stat.blockCountLong
            val available = stat.blockSizeLong * stat.availableBlocksLong
            Pair(total, available)
        } catch (e: Exception) {
            Logger.e(Logger.Tags.VM, "getStorageInfo failed", e)
            Pair(0L, 0L)
        }
    }
    
    private fun checkMemoryPressure() {
        val info = _memoryInfo.value
        val usedPercent = info.usagePercent
        
        _memoryPressure.value = usedPercent >= 80f || info.isLowMemory
        
        if (usedPercent >= 80f) {
            Logger.w(Logger.Tags.VM, "Memory pressure detected: ${usedPercent.toInt()}%")
        }
        if (info.isLowMemory) {
            Logger.w(Logger.Tags.VM, "System low memory!")
        }
    }
    
    private var lastRecommendedModelId: String? = null
    
    private fun recommendModel() {
        scope.launch {
            try {
                val memoryMB = _memoryInfo.value.availMem / (1024 * 1024)
                val models = modelRepository.getInstalledModelsSync()
                
                val recommended = models
                    .filter { it.isInstalled }
                    .filter { it.minRam <= memoryMB }
                    .maxByOrNull { it.parameters }
                    ?: models.firstOrNull { it.isInstalled }
                
                val previousModel = _recommendedModel.value
                
                _recommendedModel.value = recommended
                
                // 只在推荐模型变化时才输出日志
                if (recommended != null && recommended.id != lastRecommendedModelId) {
                    Logger.d(Logger.Tags.VM, "Recommended model: ${recommended.name} (${memoryMB}MB available)")
                    lastRecommendedModelId = recommended.id
                }
            } catch (e: Exception) {
                Logger.e(Logger.Tags.VM, "recommendModel failed", e)
            }
        }
    }
    
    fun isMemoryPressure(): Boolean = _memoryPressure.value
    
    fun getMemoryUsagePercent(): Float = _memoryUsagePercent.value
    
    fun startInferenceTimeout(timeoutMs: Long = defaultInferenceTimeoutMs) {
        inferenceStartTime = System.currentTimeMillis()
        inferenceTimeoutJob?.cancel()
        inferenceTimeoutJob = scope.launch {
            delay(timeoutMs)
            if (System.currentTimeMillis() - inferenceStartTime >= timeoutMs) {
                _inferenceTimeout.value = true
                Logger.w(Logger.Tags.VM, "Inference timeout detected: ${timeoutMs}ms exceeded")
            }
        }
        Logger.d(Logger.Tags.VM, "Inference timeout started: ${timeoutMs}ms")
    }
    
    fun stopInferenceTimeout() {
        inferenceTimeoutJob?.cancel()
        inferenceTimeoutJob = null
        _inferenceTimeout.value = false
        if (inferenceStartTime > 0) {
            val elapsed = System.currentTimeMillis() - inferenceStartTime
            Logger.d(Logger.Tags.VM, "Inference completed in ${elapsed}ms")
            inferenceStartTime = 0
        }
    }
    
    fun clearMemoryPressure() {
        Logger.i(Logger.Tags.VM, "Clearing memory pressure state")
        _memoryPressure.value = false
    }
    
    fun getAvailableMemoryMB(): Long {
        return _memoryInfo.value.availMem / (1024 * 1024)
    }
    
    fun getTotalMemoryMB(): Long {
        return _memoryInfo.value.totalMem / (1024 * 1024)
    }
    
    fun getRecommendedModel(): AIModel? {
        return _recommendedModel.value
    }
    
    fun isInferenceTimeout(): Boolean {
        return _inferenceTimeout.value
    }
}
