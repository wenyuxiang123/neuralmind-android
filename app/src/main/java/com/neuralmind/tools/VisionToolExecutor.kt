package com.neuralmind.tools

import android.content.Context
import com.neuralmind.core.Logger
import com.neuralmind.device.ScreenCaptureManager
import com.neuralmind.llama.VisionEngine
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VisionToolExecutor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val screenCaptureManager: ScreenCaptureManager,
    private val visionEngine: VisionEngine
) {
    
    companion object {
        private const val TAG = "VisionToolExecutor"
    }
    
    data class VisionToolResult(
        val success: Boolean,
        val message: String,
        val data: String = ""
    )
    
    fun analyzeScreen(task: String): VisionToolResult {
        Logger.i(TAG, "=== analyzeScreen START ===")
        Logger.d(TAG, "analyzeScreen: task='$task'")
        
        return try {
            if (!visionEngine.isVisionModelLoaded()) {
                Logger.w(TAG, "Vision model not loaded")
                return VisionToolResult(false, "视觉模型未加载，请先下载并启用 MiniCPM-V 模型")
            }
            
            val screenshotResult = runBlocking {
                screenCaptureManager.captureScreen()
            }
            
            if (!screenshotResult.success || screenshotResult.filePath == null) {
                Logger.e(TAG, "Failed to capture screen: ${screenshotResult.error}")
                return VisionToolResult(false, "截图失败: ${screenshotResult.error ?: "未知错误"}")
            }
            
            val screenshotPath = screenshotResult.filePath
            Logger.d(TAG, "Screenshot saved to: $screenshotPath")
            
            val result = runBlocking {
                visionEngine.analyzeScreen(screenshotPath, task)
            }
            
            Logger.i(TAG, "Screen analysis result: ${result.text.take(200)}")
            Logger.i(TAG, "=== analyzeScreen END ===")
            
            VisionToolResult(
                success = true,
                message = "屏幕分析完成:\n${result.text}",
                data = result.text
            )
        } catch (e: Exception) {
            Logger.e(TAG, "Error analyzing screen", e)
            VisionToolResult(false, "屏幕分析出错: ${e.message}")
        }
    }
    
    fun analyzeCurrentScreen(): VisionToolResult {
        Logger.i(TAG, "=== analyzeCurrentScreen START ===")
        
        return try {
            if (!visionEngine.isVisionModelLoaded()) {
                Logger.w(TAG, "Vision model not loaded")
                return VisionToolResult(false, "视觉模型未加载，请先下载并启用 MiniCPM-V 模型")
            }
            
            val screenshotResult = runBlocking {
                screenCaptureManager.captureScreen()
            }
            
            if (!screenshotResult.success || screenshotResult.filePath == null) {
                Logger.e(TAG, "Failed to capture screen: ${screenshotResult.error}")
                return VisionToolResult(false, "截图失败: ${screenshotResult.error ?: "未知错误"}")
            }
            
            val result = runBlocking {
                visionEngine.describeScene(screenshotResult.filePath, VisionEngine.DetailLevel.DETAILED)
            }
            
            Logger.i(TAG, "Screen description: ${result.text.take(200)}")
            Logger.i(TAG, "=== analyzeCurrentScreen END ===")
            
            VisionToolResult(
                success = true,
                message = "当前屏幕内容:\n${result.text}",
                data = result.text
            )
        } catch (e: Exception) {
            Logger.e(TAG, "Error analyzing current screen", e)
            VisionToolResult(false, "屏幕分析出错: ${e.message}")
        }
    }
    
    fun recognizeScreenText(): VisionToolResult {
        Logger.i(TAG, "=== recognizeScreenText START ===")
        
        return try {
            if (!visionEngine.isVisionModelLoaded()) {
                Logger.w(TAG, "Vision model not loaded")
                return VisionToolResult(false, "视觉模型未加载，请先下载并启用 MiniCPM-V 模型")
            }
            
            val screenshotResult = runBlocking {
                screenCaptureManager.captureScreen()
            }
            
            if (!screenshotResult.success || screenshotResult.filePath == null) {
                Logger.e(TAG, "Failed to capture screen: ${screenshotResult.error}")
                return VisionToolResult(false, "截图失败: ${screenshotResult.error ?: "未知错误"}")
            }
            
            val result = runBlocking {
                visionEngine.recognizeText(screenshotResult.filePath, "auto")
            }
            
            Logger.i(TAG, "Recognized text: ${result.text.take(200)}")
            Logger.i(TAG, "=== recognizeScreenText END ===")
            
            VisionToolResult(
                success = true,
                message = "屏幕文字识别结果:\n${result.text}",
                data = result.text
            )
        } catch (e: Exception) {
            Logger.e(TAG, "Error recognizing screen text", e)
            VisionToolResult(false, "文字识别出错: ${e.message}")
        }
    }
    
    fun findOnScreen(objectName: String): VisionToolResult {
        Logger.i(TAG, "=== findOnScreen START ===")
        Logger.d(TAG, "findOnScreen: objectName='$objectName'")
        
        return try {
            if (!visionEngine.isVisionModelLoaded()) {
                Logger.w(TAG, "Vision model not loaded")
                return VisionToolResult(false, "视觉模型未加载，请先下载并启用 MiniCPM-V 模型")
            }
            
            val screenshotResult = runBlocking {
                screenCaptureManager.captureScreen()
            }
            
            if (!screenshotResult.success || screenshotResult.filePath == null) {
                Logger.e(TAG, "Failed to capture screen: ${screenshotResult.error}")
                return VisionToolResult(false, "截图失败: ${screenshotResult.error ?: "未知错误"}")
            }
            
            val result = runBlocking {
                visionEngine.findObject(screenshotResult.filePath, objectName)
            }
            
            Logger.i(TAG, "Find result: ${result.text.take(200)}")
            Logger.i(TAG, "=== findOnScreen END ===")
            
            VisionToolResult(
                success = true,
                message = "查找 \"$objectName\" 结果:\n${result.text}",
                data = result.text
            )
        } catch (e: Exception) {
            Logger.e(TAG, "Error finding on screen", e)
            VisionToolResult(false, "查找出错: ${e.message}")
        }
    }
}
