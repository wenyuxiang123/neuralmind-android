package com.neuralmind.llama

import android.content.Context
import com.neuralmind.data.repository.ModelRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LlamaEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val modelRepository: ModelRepository
) {
    private val _tokenFlow = MutableSharedFlow<String>()
    val tokenFlow: Flow<String> = _tokenFlow

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating

    private val _isModelLoaded = MutableStateFlow(false)
    val isModelLoaded: StateFlow<Boolean> = _isModelLoaded

    private var currentModelPath: String? = null

    suspend fun loadModel(modelId: String): Boolean {
        val modelPath = modelRepository.getModelPath(modelId)
        if (modelPath == null || !File(modelPath).exists()) {
            return false
        }

        try {
            _isModelLoaded.value = true
            currentModelPath = modelPath
            return true
        } catch (e: Exception) {
            _isModelLoaded.value = false
            return false
        }
    }

    fun unloadModel() {
        _isModelLoaded.value = false
        currentModelPath = null
    }

    suspend fun generate(
        prompt: String,
        maxTokens: Int = 512,
        temperature: Float = 0.7f,
        topP: Float = 0.9f,
        topK: Int = 40,
        repeatPenalty: Float = 1.1f,
        stopSequence: String? = null,
        stream: Boolean = true
    ): String {
        _isGenerating.value = true

        try {
            val response = simulateGenerate(prompt, maxTokens, temperature)

            if (stream) {
                response.chunked(1).forEach { token ->
                    _tokenFlow.emit(token)
                    kotlinx.coroutines.delay(50)
                }
            }

            return response
        } finally {
            _isGenerating.value = false
        }
    }

    private fun simulateGenerate(prompt: String, maxTokens: Int, temperature: Float): String {
        return when {
            prompt.contains("你好") || prompt.contains("hi") -> {
                "你好！我是 NeuralMind AI，很高兴为您服务。我可以帮助您进行对话、回答问题、执行自动化任务等。"
            }
            prompt.contains("时间") || prompt.contains("几点") -> {
                "现在是 ${java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date())}"
            }
            prompt.contains("模型") -> {
                "当前使用的是本地模型，所有推理都在设备上运行，保护您的隐私。"
            }
            else -> {
                "我收到了您的消息：\"${prompt}\"。\n\n作为本地运行的 AI，我可以：\n\n1. 回答问题\n2. 提供建议\n3. 执行设备控制任务\n4. 管理您的对话记忆\n5. 使用各种技能工具\n\n请告诉我您需要什么帮助？"
            }
        }
    }

    fun getModelInfo(): String? {
        return currentModelPath?.let { path ->
            "Model: ${File(path).nameWithoutExtension}\nPath: $path"
        }
    }
}
