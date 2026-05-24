package com.neuralmind.llama

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import com.neuralmind.core.Logger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VisionEngine @Inject constructor(
    @ApplicationContext private val context: Context
) {
    
    private var visionModelLoaded = false
    private var visionEngineId: Long = -1
    
    private val visionModelsDir: File
        get() = File(context.filesDir, "models/vision").apply { mkdirs() }
    
    data class VisionModelInfo(
        val modelId: String,
        val modelPath: String,
        val mmprojPath: String,
        val isLoaded: Boolean
    )
    
    data class VisionResult(
        val text: String,
        val confidence: Float = 1.0f,
        val processingTimeMs: Long = 0
    )
    
    fun isVisionModelLoaded(): Boolean = visionModelLoaded
    
    suspend fun loadVisionModel(modelPath: String, mmprojPath: String): Boolean = withContext(Dispatchers.IO) {
        Logger.i(Logger.Tags.ENGINE, "Loading vision model: $modelPath")
        Logger.i(Logger.Tags.ENGINE, "MMProj path: $mmprojPath")
        
        try {
            if (!File(modelPath).exists()) {
                Logger.e(Logger.Tags.ENGINE, "Vision model file not found: $modelPath")
                return@withContext false
            }
            
            if (!File(mmprojPath).exists()) {
                Logger.e(Logger.Tags.ENGINE, "MMProj file not found: $mmprojPath")
                return@withContext false
            }
            
            val success = LlamaJNI.loadVisionModel(modelPath, mmprojPath)
            visionModelLoaded = success
            
            if (success) {
                Logger.i(Logger.Tags.ENGINE, "Vision model loaded successfully")
            } else {
                Logger.e(Logger.Tags.ENGINE, "Failed to load vision model")
            }
            
            return@withContext success
        } catch (e: Exception) {
            Logger.e(Logger.Tags.ENGINE, "Error loading vision model", e)
            return@withContext false
        }
    }
    
    fun unloadVisionModel() {
        if (visionModelLoaded) {
            try {
                LlamaJNI.unloadVisionModel()
                visionModelLoaded = false
                Logger.i(Logger.Tags.ENGINE, "Vision model unloaded")
            } catch (e: Exception) {
                Logger.e(Logger.Tags.ENGINE, "Error unloading vision model", e)
            }
        }
    }
    
    suspend fun analyzeImage(
        imagePath: String,
        prompt: String,
        config: VisionConfig = VisionConfig()
    ): VisionResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        
        Logger.d(Logger.Tags.ENGINE, "Analyzing image: $imagePath")
        Logger.d(Logger.Tags.ENGINE, "Prompt: $prompt")
        
        try {
            if (!visionModelLoaded) {
                Logger.w(Logger.Tags.ENGINE, "Vision model not loaded")
                return@withContext VisionResult("视觉模型未加载", 0f, 0)
            }
            
            val imageFile = File(imagePath)
            if (!imageFile.exists()) {
                Logger.e(Logger.Tags.ENGINE, "Image file not found: $imagePath")
                return@withContext VisionResult("图片文件不存在", 0f, 0)
            }
            
            val result = LlamaJNI.analyzeImage(
                imagePath,
                prompt,
                config.maxTokens,
                config.temperature,
                config.topP
            )
            
            val processingTime = System.currentTimeMillis() - startTime
            Logger.i(Logger.Tags.ENGINE, "Image analysis completed in ${processingTime}ms")
            
            return@withContext VisionResult(
                text = result,
                confidence = 1.0f,
                processingTimeMs = processingTime
            )
        } catch (e: Exception) {
            Logger.e(Logger.Tags.ENGINE, "Error analyzing image", e)
            return@withContext VisionResult("图像分析出错: ${e.message}", 0f, 0)
        }
    }
    
    suspend fun analyzeBitmap(
        bitmap: Bitmap,
        prompt: String,
        config: VisionConfig = VisionConfig()
    ): VisionResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        
        try {
            val tempFile = File(visionModelsDir, "temp_${System.currentTimeMillis()}.jpg")
            FileOutputStream(tempFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }
            
            val result = analyzeImage(tempFile.absolutePath, prompt, config)
            
            tempFile.delete()
            
            return@withContext result.copy(processingTimeMs = System.currentTimeMillis() - startTime)
        } catch (e: Exception) {
            Logger.e(Logger.Tags.ENGINE, "Error analyzing bitmap", e)
            return@withContext VisionResult("图像分析出错: ${e.message}", 0f, 0)
        }
    }
    
    suspend fun recognizeText(
        imagePath: String,
        language: String = "auto"
    ): VisionResult = withContext(Dispatchers.IO) {
        val prompt = when (language) {
            "zh" -> "请识别图片中的所有中文文字，完整转录，不要遗漏任何文字。"
            "en" -> "Please recognize all English text in the image, transcribe completely."
            "mixed" -> "请识别图片中的所有文字（中文和英文），完整转录。"
            else -> "请识别图片中的所有文字，完整转录，包括所有细节。"
        }
        return@withContext analyzeImage(imagePath, prompt)
    }
    
    suspend fun describeScene(
        imagePath: String,
        detail: DetailLevel = DetailLevel.NORMAL
    ): VisionResult = withContext(Dispatchers.IO) {
        val prompt = when (detail) {
            DetailLevel.SIMPLE -> "请简单描述这张图片的主要内容。"
            DetailLevel.NORMAL -> "请详细描述这张图片的内容，包括场景、物体、颜色等细节。"
            DetailLevel.DETAILED -> "请尽可能详细地描述这张图片的所有内容，包括场景、物体、人物、动作、颜色、光线、背景等所有可见的细节。"
        }
        return@withContext analyzeImage(imagePath, prompt)
    }
    
    suspend fun findObject(
        imagePath: String,
        objectName: String
    ): VisionResult = withContext(Dispatchers.IO) {
        val prompt = "在这张图片中查找 \"$objectName\"，如果找到了请描述它的位置（上下左右中间等）和外观特征。如果没有找到，请说明没有看到这个物体。"
        return@withContext analyzeImage(imagePath, prompt)
    }
    
    suspend fun analyzeScreen(
        screenshotPath: String,
        task: String
    ): VisionResult = withContext(Dispatchers.IO) {
        Logger.i(Logger.Tags.ENGINE, "Analyzing screen for task: $task")
        return@withContext analyzeImage(
            screenshotPath,
            "这是一个手机屏幕截图。$task\n\n请分析这个屏幕并给出详细描述，包括：\n1. 当前显示的界面类型（桌面、应用、信息流等）\n2. 主要内容\n3. 可交互的元素（按钮、图标、输入框等）\n4. 与任务相关的具体信息"
        )
    }
    
    private fun bitmapToBase64(bitmap: Bitmap, quality: Int = 80): String {
        val byteArrayOutputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, byteArrayOutputStream)
        val byteArray = byteArrayOutputStream.toByteArray()
        return Base64.encodeToString(byteArray, Base64.DEFAULT)
    }
    
    enum class DetailLevel {
        SIMPLE,
        NORMAL,
        DETAILED
    }
    
    data class VisionConfig(
        val maxTokens: Int = 1024,
        val temperature: Float = 0.3f,
        val topP: Float = 0.9f
    )
}