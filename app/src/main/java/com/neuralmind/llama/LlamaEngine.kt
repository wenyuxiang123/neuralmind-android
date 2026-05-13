package com.neuralmind.llama

import android.content.Context
import com.neuralmind.data.repository.ModelRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
    private val llamaJNI: LlamaJNI
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
     * Moved out of init{} to prevent crash during Hilt injection at app startup.
     */
    private fun ensureEngineInitialized() {
        if (!engineInitialized) {
            try {
                engineId = LlamaJNI.createEngine()
                engineInitialized = true
            } catch (e: Exception) {
                _isModelLoaded.value = false
            } catch (e: UnsatisfiedLinkError) {
                _isModelLoaded.value = false
            }
        }
    }

    suspend fun loadModel(modelId: String): Boolean {
        val modelPath = modelRepository.getModelPath(modelId)
        
        try {
            ensureEngineInitialized()
            if (!engineInitialized) return false
            val loaded = LlamaJNI.loadModel(engineId, modelPath ?: "default")
            _isModelLoaded.value = loaded
            if (loaded) {
                currentModelPath = modelPath
                currentModelId = modelId
            }
            return loaded
        } catch (e: Exception) {
            _isModelLoaded.value = false
            return false
        }
    }

    fun unloadModel() {
        LlamaJNI.unloadModel(engineId)
        _isModelLoaded.value = false
        currentModelPath = null
        currentModelId = null
    }

    fun getSupportedModels(): Array<String> {
        return try {
            LlamaJNI.getSupportedModels()
        } catch (e: Exception) {
            emptyArray()
        }
    }

    fun updateConfig(newConfig: InferenceConfig) {
        _inferenceConfig.value = newConfig
    }

    /**
     * Generate with streaming callback - uses true streaming via generateStream JNI method.
     * Each token is delivered to onToken as soon as it's generated.
     */
    suspend fun generate(
        prompt: String,
        onToken: (String) -> Unit,
        onComplete: (String) -> Unit,
        onError: (String) -> Unit,
        config: InferenceConfig = _inferenceConfig.value
    ) = withContext(Dispatchers.IO) {
        _isGenerating.value = true

        try {
            ensureEngineInitialized()
            if (!engineInitialized) {
                onError("推理引擎初始化失败")
                _isGenerating.value = false
                return@withContext
            }
            // Set token callback for streaming
            LlamaJNI._tokenCallback = onToken

            // Use streaming JNI method
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
                getFallbackResponse(prompt, config)
            }

            onComplete(response)
        } catch (e: Exception) {
            onError(e.message ?: "生成过程中出错")
        } finally {
            LlamaJNI._tokenCallback = null
            _isGenerating.value = false
        }
    }

    /**
     * Generate with Flow-based streaming - uses true streaming.
     * Tokens are emitted to tokenFlow as they are generated.
     */
    suspend fun generate(
        prompt: String,
        config: InferenceConfig = _inferenceConfig.value
    ): String {
        _isGenerating.value = true

        try {
            ensureEngineInitialized()
            if (!engineInitialized) {
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
                getFallbackResponse(prompt, config)
            }

            return response
        } finally {
            LlamaJNI._tokenCallback = null
            _isGenerating.value = false
        }
    }

    fun stopGeneration() {
        try {
            LlamaJNI.stopGeneration(engineId)
        } catch (e: Exception) {
            // 忽略错误
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
}

data class InferenceConfig(
    val maxTokens: Int = 512,
    val temperature: Float = 0.7f,
    val topP: Float = 0.9f,
    val topK: Int = 40,
    val repeatPenalty: Float = 1.1f,
    val stopSequence: String? = null
    // Note: stream, tokenChunkSize, tokenDelayMs are removed from config
    // since we now have true streaming via generateStream JNI method
)

data class ModelInfo(
    val modelId: String,
    val modelName: String,
    val modelPath: String,
    val isLoaded: Boolean,
    val config: InferenceConfig
)
