package com.neuralmind.llama

import android.content.Context
import android.os.Build
import com.neuralmind.core.Logger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HardwareAccelerationManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    
    enum class AccelerationType {
        CPU,
        GPU_OPENCL,
        GPU_VULKAN,
        NPU_HEXAGON,
        NPU_NNAPI,
        AUTO
    }
    
    data class AccelerationInfo(
        val type: AccelerationType,
        val name: String,
        val supported: Boolean,
        val recommended: Boolean,
        val description: String
    )
    
    private val detectedAccelerators = mutableListOf<AccelerationInfo>()
    private var selectedAccelerator: AccelerationType = AccelerationType.AUTO
    
    init {
        detectHardwareAccelerators()
    }
    
    private fun detectHardwareAccelerators() {
        Logger.i(Logger.Tags.ENGINE, "Detecting hardware accelerators")
        
        // CPU is always available
        detectedAccelerators.add(AccelerationInfo(
            type = AccelerationType.CPU,
            name = "CPU",
            supported = true,
            recommended = false,
            description = "通用处理器，兼容性最好"
        ))
        
        // Detect GPU OpenCL
        if (isOpenCLSupported()) {
            detectedAccelerators.add(AccelerationInfo(
                type = AccelerationType.GPU_OPENCL,
                name = "GPU (OpenCL)",
                supported = true,
                recommended = checkIfRecommended(AccelerationType.GPU_OPENCL),
                description = "图形处理器加速，支持大多数安卓设备"
            ))
        }
        
        // Detect GPU Vulkan
        if (isVulkanSupported()) {
            detectedAccelerators.add(AccelerationInfo(
                type = AccelerationType.GPU_VULKAN,
                name = "GPU (Vulkan)",
                supported = true,
                recommended = checkIfRecommended(AccelerationType.GPU_VULKAN),
                description = "现代图形API，性能更好"
            ))
        }
        
        // Detect Hexagon NPU
        if (isHexagonSupported()) {
            detectedAccelerators.add(AccelerationInfo(
                type = AccelerationType.NPU_HEXAGON,
                name = "NPU (Hexagon)",
                supported = true,
                recommended = checkIfRecommended(AccelerationType.NPU_HEXAGON),
                description = "高通Hexagon神经网络处理器"
            ))
        }
        
        // Detect NNAPI
        if (isNNAPISupported()) {
            detectedAccelerators.add(AccelerationInfo(
                type = AccelerationType.NPU_NNAPI,
                name = "NPU (NNAPI)",
                supported = true,
                recommended = checkIfRecommended(AccelerationType.NPU_NNAPI),
                description = "Android神经网络API，适配多种NPU"
            ))
        }
        
        Logger.i(Logger.Tags.ENGINE, "Detected ${detectedAccelerators.size} accelerators")
        detectedAccelerators.forEach {
            Logger.i(Logger.Tags.ENGINE, "  - ${it.name}: ${if (it.supported) "supported" else "not supported"} ${if (it.recommended) "(recommended)" else ""}")
        }
    }
    
    private fun isOpenCLSupported(): Boolean {
        return try {
            System.loadLibrary("OpenCL")
            true
        } catch (e: UnsatisfiedLinkError) {
            false
        }
    }
    
    private fun isVulkanSupported(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
    }
    
    private fun isHexagonSupported(): Boolean {
        val brand = Build.BRAND.lowercase()
        val model = Build.MODEL.lowercase()
        
        // Check for Qualcomm Snapdragon with Hexagon NPU
        val hasHexagon = arrayOf(
            "snapdragon 8 gen 1", "snapdragon 8 gen 2", "snapdragon 8 gen 3",
            "snapdragon 8 elite", "snapdragon 8+", "snapdragon 7 gen 1",
            "snapdragon 7 gen 2", "snapdragon 778g", "snapdragon 780g",
            "snapdragon 695", "snapdragon 690"
        ).any { 
            model.contains(it) || Build.PRODUCT.contains(it) || Build.DEVICE.contains(it)
        }
        
        return hasHexagon && hasHexagonLibrary()
    }
    
    private fun hasHexagonLibrary(): Boolean {
        val libPaths = arrayOf(
            "/system/lib/libQnnHtp.so",
            "/vendor/lib/libQnnHtp.so",
            "/system/lib64/libQnnHtp.so",
            "/vendor/lib64/libQnnHtp.so"
        )
        return libPaths.any { File(it).exists() }
    }
    
    private fun isNNAPISupported(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
    }
    
    private fun checkIfRecommended(type: AccelerationType): Boolean {
        val score = getAcceleratorScore(type)
        val otherScores = detectedAccelerators.filter { it.type != type }.map { getAcceleratorScore(it.type) }
        return score > otherScores.maxOrNull() ?: 0
    }
    
    private fun getAcceleratorScore(type: AccelerationType): Int {
        return when (type) {
            AccelerationType.NPU_HEXAGON -> 100
            AccelerationType.NPU_NNAPI -> 80
            AccelerationType.GPU_VULKAN -> 70
            AccelerationType.GPU_OPENCL -> 60
            AccelerationType.CPU -> 10
            AccelerationType.AUTO -> 0
        }
    }
    
    fun getAvailableAccelerators(): List<AccelerationInfo> {
        return detectedAccelerators.toList()
    }
    
    fun getRecommendedAccelerator(): AccelerationType {
        val recommended = detectedAccelerators.firstOrNull { it.recommended }
        return recommended?.type ?: AccelerationType.CPU
    }
    
    fun selectAccelerator(type: AccelerationType) {
        if (type == AccelerationType.AUTO) {
            selectedAccelerator = getRecommendedAccelerator()
        } else {
            val available = detectedAccelerators.find { it.type == type && it.supported }
            selectedAccelerator = available?.type ?: AccelerationType.CPU
        }
        Logger.i(Logger.Tags.ENGINE, "Selected accelerator: $selectedAccelerator")
    }
    
    fun getSelectedAccelerator(): AccelerationType {
        return selectedAccelerator
    }
    
    fun getSelectedAcceleratorInfo(): AccelerationInfo {
        return detectedAccelerators.firstOrNull { it.type == selectedAccelerator } 
            ?: detectedAccelerators.first { it.type == AccelerationType.CPU }
    }
    
    suspend fun applyAcceleration(engine: LlamaEngine): Boolean = withContext(Dispatchers.IO) {
        Logger.i(Logger.Tags.ENGINE, "Applying acceleration: $selectedAccelerator")
        
        return@withContext try {
            when (selectedAccelerator) {
                AccelerationType.CPU -> applyCPU()
                AccelerationType.GPU_OPENCL -> applyGPUOpenCL()
                AccelerationType.GPU_VULKAN -> applyGPUVulkan()
                AccelerationType.NPU_HEXAGON -> applyNPUHexagon()
                AccelerationType.NPU_NNAPI -> applyNpuNnapi()
                AccelerationType.AUTO -> applyRecommended()
            }
        } catch (e: Exception) {
            Logger.e(Logger.Tags.ENGINE, "Failed to apply $selectedAccelerator", e)
            false
        }
    }
    
    private fun applyCPU(): Boolean {
        Logger.i(Logger.Tags.ENGINE, "Using CPU mode")
        LlamaJNI.setAccelerationMode(0)
        return true
    }
    
    private fun applyGPUOpenCL(): Boolean {
        Logger.i(Logger.Tags.ENGINE, "Using GPU OpenCL mode")
        LlamaJNI.setAccelerationMode(1)
        return true
    }
    
    private fun applyGPUVulkan(): Boolean {
        Logger.i(Logger.Tags.ENGINE, "Using GPU Vulkan mode")
        LlamaJNI.setAccelerationMode(2)
        return true
    }
    
    private fun applyNPUHexagon(): Boolean {
        Logger.i(Logger.Tags.ENGINE, "Using Hexagon NPU mode")
        LlamaJNI.setAccelerationMode(3)
        return true
    }
    
    private fun applyNpuNnapi(): Boolean {
        Logger.i(Logger.Tags.ENGINE, "Using NNAPI mode")
        LlamaJNI.setAccelerationMode(4)
        return true
    }
    
    private fun applyRecommended(): Boolean {
        val recommended = getRecommendedAccelerator()
        selectedAccelerator = recommended
        return applyAccelerationInternal(recommended)
    }
    
    private fun applyAccelerationInternal(type: AccelerationType): Boolean {
        return when (type) {
            AccelerationType.CPU -> applyCPU()
            AccelerationType.GPU_OPENCL -> applyGPUOpenCL()
            AccelerationType.GPU_VULKAN -> applyGPUVulkan()
            AccelerationType.NPU_HEXAGON -> applyNPUHexagon()
            AccelerationType.NPU_NNAPI -> applyNpuNnapi()
            AccelerationType.AUTO -> applyRecommended()
        }
    }
    
    fun getDeviceInfo(): String {
        return """Device Info:
            |  Brand: ${Build.BRAND}
            |  Model: ${Build.MODEL}
            |  Product: ${Build.PRODUCT}
            |  Device: ${Build.DEVICE}
            |  SDK: ${Build.VERSION.SDK_INT}
            |  CPU: ${Build.HARDWARE}
            |  Selected Accelerator: $selectedAccelerator
        """.trimMargin()
    }
}