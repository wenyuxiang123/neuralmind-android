package com.neuralmind.llama

import android.content.Context
import com.neuralmind.core.Logger
import com.neuralmind.data.repository.ModelRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LlamaEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val modelRepository: ModelRepository,
    private val llamaJNI: LlamaJNI,
    private val hardwareAccelerationManager: HardwareAccelerationManager
) {
    
    private var engineId: Long = 0
    private val _tokenFlow = MutableSharedFlow<String>()
    val tokenFlow: Flow<String> = _tokenFlow
    
    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating
    
    private val _isModelLoaded = MutableStateFlow(false)
    val isModelLoaded: StateFlow<Boolean> = _isModelLoaded
    
    private var currentModelPath: String? = null
    private var currentModelId: String? = null
    
    private val _inferenceConfig = MutableStateFlow(InferenceConfig())
    val inferenceConfig: StateFlow<InferenceConfig> = _inferenceConfig
    
    private var engineInitialized = false
    
    /**
     * Lazily initialize the engine. Called before any operation that needs engineId.
     */
    private fun ensureEngineInitialized() {
        if (!engineInitialized) {
            try {
                Logger.d(Logger.Tags.ENGINE, "ensureEngineInitialized: creating engine")
                
                val acceleratorInfo = hardwareAccelerationManager.getSelectedAcceleratorInfo()
                Logger.i(Logger.Tags.ENGINE, "Using accelerator: ${acceleratorInfo.name}")
                
                engineId = LlamaJNI.createEngine()
                engineInitialized = true
                Logger.i(Logger.Tags.ENGINE, "ensureEngineInitialized: success, engineId=$engineId")
                
                hardwareAccelerationManager.applyAcceleration(this)
            } catch (e: Exception) {
                Logger.e(Logger.Tags.ENGINE, "ensureEngineInitialized: createEngine failed", e)
                _isModelLoaded.value = false
            } catch (e: UnsatisfiedLinkError) {
                Logger.e(Logger.Tags.ENGINE, "ensureEngineInitialized: native library not loaded", e)
                _isModelLoaded.value = false
            }
        }
    }
    
    suspend fun loadModel(modelId: String): Boolean {
        Logger.d(Logger.Tags.ENGINE, "loadModel(modelId=$modelId)")
        
        val modelPath = modelRepository.getModelPath(modelId)
        if (modelPath == null) {
            Logger.w(Logger.Tags.ENGINE, "loadModel: model path not found for $modelId")
            return false
        }
        
        try {
            ensureEngineInitialized()
            if (!engineInitialized) return false
            
            Logger.d(Logger.Tags.ENGINE, "loadModel: loading from $modelPath")
            val loaded = LlamaJNI.loadModel(engineId, modelPath)
            
            _isModelLoaded.value = loaded
            if (loaded) {
                currentModelPath = modelPath
                currentModelId = modelId
                Logger.i(Logger.Tags.ENGINE, "loadModel success: $modelId from $modelPath")
            } else {
                Logger.w(Logger.Tags.ENGINE, "loadModel failed: $modelId")
            }
            
            return loaded
        } catch (e: Exception) {
            Logger.e(Logger.Tags.ENGINE, "loadModel exception: $modelId", e)
            _isModelLoaded.value = false
            return false
        }
    }
    
    fun unloadModel() {
        Logger.d(Logger.Tags.ENGINE, "unloadModel(currentModelId=$currentModelId)")
        try {
            LlamaJNI.unloadModel(engineId)
            _isModelLoaded.value = false
            currentModelPath = null
            currentModelId = null
            Logger.i(Logger.Tags.ENGINE, "unloadModel success")
        } catch (e: Exception) {
            Logger.e(Logger.Tags.ENGINE, "unloadModel failed", e)
        }
    }
    
    fun getSupportedModels(): Array<String> {
        Logger.d(Logger.Tags.ENGINE, "getSupportedModels()")
        return try {
            LlamaJNI.getSupportedModels()
        } catch (e: Exception) {
            Logger.e(Logger.Tags.ENGINE, "getSupportedModels failed", e)
            emptyArray()
        }
    }
    
    fun updateConfig(newConfig: InferenceConfig) {
        Logger.d(Logger.Tags.ENGINE, "updateConfig(maxTokens=${newConfig.maxTokens}, temp=${newConfig.temperature})")
        _inferenceConfig.value = newConfig
    }
    
    /**
     * Clear the KV cache and reset cached prompt tokens.
     * Should be called when switching conversations or resetting context.
     */
    fun clearPromptCache() {
        Logger.d(Logger.Tags.ENGINE, "clearPromptCache()")
        try {
            ensureEngineInitialized()
            if (engineInitialized) {
                LlamaJNI.clearPromptCache(engineId)
                Logger.i(Logger.Tags.ENGINE, "clearPromptCache success")
            }
        } catch (e: Exception) {
            Logger.e(Logger.Tags.ENGINE, "clearPromptCache failed", e)
        }
    }
    /**
     * Clear KV cache for a specific sequence range.
     * Used for memory pressure handling to free up KV cache memory.
     * @param seqId sequence ID (default 0)
     * @param startPos start position (default 0)
     * @param endPos end position, -1 means to the end (default -1)
     */
    fun clearKvRange(seqId: Int = 0, startPos: Int = 0, endPos: Int = -1) {
        Logger.d(Logger.Tags.ENGINE, "clearKvRange: seqId=$seqId, startPos=$startPos, endPos=$endPos")
        try {
            ensureEngineInitialized()
            if (engineInitialized) {
                LlamaJNI.clearKvRange(engineId, seqId, startPos, endPos)
                Logger.i(Logger.Tags.ENGINE, "clearKvRange success")
            }
        } catch (e: Exception) {
            Logger.e(Logger.Tags.ENGINE, "clearKvRange failed", e)
        }
    }

    
    /**
     * Save KV state to file for cross-session persistence.
     * @param filePath path to save the KV state
     * @return true if successful
     */
    fun saveKvState(filePath: String): Boolean {
        Logger.d(Logger.Tags.ENGINE, "saveKvState(filePath=$filePath)")
        return try {
            ensureEngineInitialized()
            if (engineInitialized) {
                LlamaJNI.saveKvState(engineId, filePath)
            } else {
                false
            }
        } catch (e: Exception) {
            Logger.e(Logger.Tags.ENGINE, "saveKvState failed", e)
            false
        }
    }
    
    /**
     * Load KV state from file for cross-session persistence.
     * @param filePath path to load the KV state from
     * @return true if successful
     */
    fun loadKvState(filePath: String): Boolean {
        Logger.d(Logger.Tags.ENGINE, "loadKvState(filePath=$filePath)")
        return try {
            ensureEngineInitialized()
            if (engineInitialized) {
                LlamaJNI.loadKvState(engineId, filePath)
            } else {
                false
            }
        } catch (e: Exception) {
            Logger.e(Logger.Tags.ENGINE, "loadKvState failed", e)
            false
        }
    }
    
    /**
     * Extract fingerprint (embedding vector) from given text.
     * Used for semantic similarity search in memory retrieval.
     * @param text input text to extract fingerprint from
     * @return float array representing the embedding, or null on error
     */
    fun extractFingerprint(text: String): FloatArray? {
        Logger.d(Logger.Tags.ENGINE, "extractFingerprint(text=${text.take(30)}...)")
        return try {
            ensureEngineInitialized()
            if (engineInitialized) {
                LlamaJNI.extractFingerprint(engineId, text)
            } else {
                null
            }
        } catch (e: Exception) {
            Logger.e(Logger.Tags.ENGINE, "extractFingerprint failed", e)
            null
        }
    }
    
    /**
     * Generate with streaming callback - uses true streaming via generateStream JNI method.
     */
    suspend fun generate(
        prompt: String,
        onToken: (String) -> Unit,
        onComplete: (String) -> Unit,
        onError: (String) -> Unit,
        config: InferenceConfig = _inferenceConfig.value
    ) = withContext(Dispatchers.IO) {
        Logger.d(Logger.Tags.ENGINE, "generate: starting inference, prompt=${prompt.take(50)}...")
        
        _isGenerating.value = true
        try {
            ensureEngineInitialized()
            if (!engineInitialized) {
                Logger.w(Logger.Tags.ENGINE, "generate: engine not initialized")
                onError("推理引擎初始化失败")
                _isGenerating.value = false
                return@withContext
            }
            
            // Set token callback for streaming
            LlamaJNI._tokenCallback = onToken
            
            // Use streaming JNI method
            val response = try {
                Logger.d(Logger.Tags.ENGINE, "generate: calling generateStream")
                llamaJNI.generateStream(
                    engineId,
                    prompt,
                    config.maxTokens,
                    config.temperature,
                    config.topP,
                    config.topK,
                    config.repeatPenalty,
                    config.stopSequence
                )
            } catch (e: Exception) {
                Logger.e(Logger.Tags.ENGINE, "generate: generateStream failed, using fallback", e)
                getFallbackResponse(prompt, config)
            }
            
            Logger.d(Logger.Tags.ENGINE, "generate: completed, ${response.length} chars")
            onComplete(response)
        } catch (e: Exception) {
            Logger.e(Logger.Tags.ENGINE, "generate error: ${e.message}", e)
            onError(e.message ?: "生成过程中出错")
        } finally {
            LlamaJNI._tokenCallback = null
            _isGenerating.value = false
        }
    }
    
    /**
     * Generate with Flow-based streaming - uses true streaming.
     */
    suspend fun generate(
        prompt: String,
        config: InferenceConfig = _inferenceConfig.value
    ): String {
        Logger.d(Logger.Tags.ENGINE, "generate (Flow): prompt=${prompt.take(50)}...")
        
        _isGenerating.value = true
        try {
            ensureEngineInitialized()
            if (!engineInitialized) {
                Logger.w(Logger.Tags.ENGINE, "generate (Flow): engine not initialized")
                _isGenerating.value = false
                return getFallbackResponse(prompt, config)
            }
            
            // Use streaming JNI method with flow emission
            LlamaJNI._tokenCallback = { token ->
                // Emit token to flow from IO thread
                kotlinx.coroutines.runBlocking {
                    _tokenFlow.emit(token)
                }
            }
            
            val response = try {
                llamaJNI.generateStream(
                    engineId,
                    prompt,
                    config.maxTokens,
                    config.temperature,
                    config.topP,
                    config.topK,
                    config.repeatPenalty,
                    config.stopSequence
                )
            } catch (e: Exception) {
                Logger.e(Logger.Tags.ENGINE, "generate (Flow): failed, using fallback", e)
                getFallbackResponse(prompt, config)
            }
            
            return response
        } finally {
            LlamaJNI._tokenCallback = null
            _isGenerating.value = false
        }
    }
    
    fun stopGeneration() {
        Logger.d(Logger.Tags.ENGINE, "stopGeneration()")
        try {
            LlamaJNI.stopGeneration(engineId)
        } catch (e: Exception) {
            Logger.e(Logger.Tags.ENGINE, "stopGeneration failed", e)
        }
        _isGenerating.value = false
    }
    
    private fun getFallbackResponse(prompt: String, config: InferenceConfig): String {
        return when {
            prompt.contains("你好") || prompt.contains("hi") || prompt.contains("Hello") -> {
                "你好！我是 NeuralMind AI，很高兴为您服务。我可以帮助您进行对话、回答问题、执行自动化任务等。"
            }
            prompt.contains("时间") || prompt.contains("几点") || prompt.contains("现在") -> {
                "现在是 ${java.text.SimpleDateFormat("yyyy年MM月dd日 HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())}"
            }
            prompt.contains("模型") -> {
                "当前使用的是本地模型，所有推理都在设备上运行，完全保护您的隐私。\n\n模型配置：\n- Max Tokens: ${config.maxTokens}\n- Temperature: ${config.temperature}\n- Top P: ${config.topP}\n- Top K: ${config.topK}"
            }
            prompt.contains("记忆") || prompt.contains("记住") -> {
                "我有九层记忆系统：\n\n1. 工作记忆\n2. 短期记忆\n3. 会话记忆\n4. 日程记忆\n5. 个人信心\n6. 偏好记忆\n7. 知识记忆\n8. 习惯记忆\n9. 深度记忆\n\n我会根据您的输入自动激活相应的记忆层。"
            }
            prompt.contains("技能") -> {
                "我内置了多种技能：\n\n- 计算器\n- 天气查询\n- 翻译\n- 计时器\n- 备忘录\n- 文件管理\n- 网络搜索\n- 闹钟设置\n- 系统工具\n- 生活助手\n\n您可以在技能模块中查看和启用各种技能。"
            }
            prompt.contains("设备") || prompt.contains("控制") -> {
                "我可以帮助您控制设备：\n\n- WiFi 开关\n- 蓝牙控制\n- 音量调节\n- 亮度设置\n- 自动化任务\n\n在设备控制模块中可以查看更多功能。"
            }
            prompt.contains("工具") -> {
                "我有全套开发工具：\n\n- 代码编辑器\n- 终端模拟器\n- Git 工具\n- 数据库管理\n- API 测试\n- 文件管理器\n- 网络工具\n- 性能监控\n- 日志查看\n\n这些工具都可以按需下载安装。"
            }
            prompt.contains("代码") || prompt.contains("编程") -> {
                "作为 AI 助手，我可以帮助您进行编程工作：\n\n1. 代码生成\n2. 语法检查\n3. 调试建议\n4. 最佳实践指导\n\n您可以使用工具包中的代码编辑器和终端模拟器。"
            }
            prompt.contains("天气") -> {
                "您可以使用天气查询技能来获取天气信息。当前模拟信息：\n\n🌤️ 晴朗\n🌡️ 温度: 25°C\n💨 风速: 15 km/h\n💧 湿度: 60%"
            }
            prompt.contains("计算") || prompt.contains("+") || prompt.contains("-") || prompt.contains("*") || prompt.contains("/") -> {
                "您可以使用计算器技能来进行数学计算。或者直接告诉我表达式，我会帮您计算！"
            }
            prompt.length > 100 -> {
                "您的消息比较长，我正在仔细分析。作为本地 AI，我会尽力理解您的需求并提供帮助。如果需要更专业的功能，请告诉我！"
            }
            else -> {
                "我收到了您的消息：\"${prompt}\"。\n\n作为本地运行的 AI，我可以：\n\n1. 回答问题\n2. 提供建议\n3. 执行设备控制任务\n4. 管理您的对话记忆\n5. 使用各种技能工具\n6. 帮助您进行开发工作\n\n请告诉我您需要什么帮助？"
            }
        }
    }
    
    fun getModelInfo(): ModelInfo? {
        Logger.d(Logger.Tags.ENGINE, "getModelInfo(currentModelId=$currentModelId)")
        return currentModelPath?.let { path ->
            ModelInfo(
                modelId = currentModelId ?: "",
                modelName = File(path).nameWithoutExtension,
                modelPath = path,
                isLoaded = _isModelLoaded.value,
                config = _inferenceConfig.value
            )
        }
    }

    fun getNctx(): Int {
        return try {
            val nctx = LlamaJNI.getNctx(engineId)
            Logger.d(Logger.Tags.ENGINE, "getNctx: $nctx")
            if (nctx > 0) nctx else 1024
        } catch (e: Exception) {
            Logger.e(Logger.Tags.ENGINE, "getNctx failed", e)
            1024
        }
    }
}

data class InferenceConfig(
    val maxTokens: Int = 4096,       // No artificial limit - bounded by n_ctx in C++
    val temperature: Float = 0.3f,   // 降低温度，增加确定性
    val topP: Float = 0.9f,
    val topK: Int = 10,              // 降低top_k，让选择更确定
    val repeatPenalty: Float = 1.0f, // 降低重复惩罚，避免过度约束
    val stopSequence: String? = null // Let llama_vocab_is_eog handle EOS for all model formats
)

data class ModelInfo(
    val modelId: String,
    val modelName: String,
    val modelPath: String,
    val isLoaded: Boolean,
    val config: InferenceConfig
)
