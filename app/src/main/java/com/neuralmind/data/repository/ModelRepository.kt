package com.neuralmind.data.repository

import android.content.Context
import com.neuralmind.data.local.db.dao.ModelDao
import com.neuralmind.data.local.db.entity.ModelEntity
import com.neuralmind.domain.model.AIModel
import com.neuralmind.domain.model.ModelCategory
import com.neuralmind.network.ModelDownloader
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class ModelRepository @Inject constructor(
    private val modelDao: ModelDao,
    private val context: Context,
    private val modelDownloader: ModelDownloader
) {
    private val modelsDir = File(context.filesDir, "models")
    
    private val _currentModel = MutableStateFlow<AIModel?>(null)
    val currentModel: StateFlow<AIModel?> = _currentModel

    init {
        if (!modelsDir.exists()) {
            modelsDir.mkdirs()
        }
    }

    fun getAllModels(): Flow<List<AIModel>> {
        return modelDao.getAllModels().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    fun getInstalledModels(): Flow<List<AIModel>> {
        return modelDao.getInstalledModels().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    fun getModelsByCategory(category: ModelCategory): Flow<List<AIModel>> {
        return modelDao.getModelsByCategory(category.name).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    suspend fun getModelById(id: String): AIModel? {
        return modelDao.getModelById(id)?.toDomain()
    }

    suspend fun switchModel(modelId: String) {
        val model = getModelById(modelId)
        _currentModel.value = model
    }

    suspend fun insertDefaultModels() {
        if (modelDao.getModelById("llama3.2-1b") != null) return

        val defaultModels = listOf(
            ModelEntity(
                id = "llama3.2-1b",
                name = "LLaMA 3.2 1B",
                description = "适合手机的高效模型，轻量快速",
                size = 1_300_000_000,
                parameters = 1,
                quantization = "Q4_K_M",
                category = ModelCategory.MOBILE.name,
                downloadUrl = "https://huggingface.co/quantities/llama3.2-1b-q4_k_m/resolve/main/llama3.2-1b-q4_k_m.gguf",
                checksum = "",
                minRam = 1024,
                minStorage = 2048,
                recommendedRam = 2048,
                supportsGpu = true,
                supportsNnapi = true
            ),
            ModelEntity(
                id = "llama3.2-3b",
                name = "LLaMA 3.2 3B",
                description = "性能与速度平衡的模型",
                size = 3_100_000_000,
                parameters = 3,
                quantization = "Q4_K_M",
                category = ModelCategory.MOBILE.name,
                downloadUrl = "https://huggingface.co/quantities/llama3.2-3b-q4_k_m/resolve/main/llama3.2-3b-q4_k_m.gguf",
                checksum = "",
                minRam = 2048,
                minStorage = 4096,
                recommendedRam = 4096,
                supportsGpu = true,
                supportsNnapi = true
            ),
            ModelEntity(
                id = "gemma-2b",
                name = "Gemma 2B",
                description = "Google 高效语言模型",
                size = 1_700_000_000,
                parameters = 2,
                quantization = "Q4_K_M",
                category = ModelCategory.MOBILE.name,
                downloadUrl = "https://huggingface.co/google/gemma-2-2b-it-q4_k_m/resolve/main/gemma-2-2b-it-q4_k_m.gguf",
                checksum = "",
                minRam = 1536,
                minStorage = 2560,
                recommendedRam = 3072,
                supportsGpu = true,
                supportsNnapi = true
            ),
            ModelEntity(
                id = "phi-2.5",
                name = "Phi-2.5 3B",
                description = "Microsoft 高效小模型",
                size = 2_700_000_000,
                parameters = 3,
                quantization = "Q4_K_M",
                category = ModelCategory.MOBILE.name,
                downloadUrl = "https://huggingface.co/microsoft/phi-2.5-3b-q4_k_m/resolve/main/phi-2.5-3b-q4_k_m.gguf",
                checksum = "",
                minRam = 2048,
                minStorage = 3584,
                recommendedRam = 4096,
                supportsGpu = true,
                supportsNnapi = true
            ),
            ModelEntity(
                id = "qwen2.5-0.5b",
                name = "Qwen2.5 0.5B",
                description = "阿里高效小模型，极快速度",
                size = 700_000_000,
                parameters = 1,
                quantization = "Q4_K_M",
                category = ModelCategory.MOBILE.name,
                downloadUrl = "https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-q4_k_m/resolve/main/qwen2.5-0.5b-instruct-q4_k_m.gguf",
                checksum = "",
                minRam = 768,
                minStorage = 1024,
                recommendedRam = 1536,
                supportsGpu = true,
                supportsNnapi = true
            ),
            ModelEntity(
                id = "mistral-7b",
                name = "Mistral 7B",
                description = "高质量通用模型",
                size = 7_000_000_000,
                parameters = 7,
                quantization = "Q4_K_M",
                category = ModelCategory.TEXT.name,
                downloadUrl = "https://huggingface.co/mistralai/Mistral-7B-Instruct-v0.3-q4_k_m/resolve/main/mistral-7b-instruct-v0.3-q4_k_m.gguf",
                checksum = "",
                minRam = 4096,
                minStorage = 8192,
                recommendedRam = 6144,
                supportsGpu = true,
                supportsNnapi = false
            ),
            ModelEntity(
                id = "llama3.1-8b",
                name = "LLaMA 3.1 8B",
                description = "最新的 Meta 大模型",
                size = 8_200_000_000,
                parameters = 8,
                quantization = "Q4_K_M",
                category = ModelCategory.TEXT.name,
                downloadUrl = "https://huggingface.co/quantities/llama3.1-8b-instruct-q4_k_m/resolve/main/llama3.1-8b-instruct-q4_k_m.gguf",
                checksum = "",
                minRam = 5120,
                minStorage = 10240,
                recommendedRam = 8192,
                supportsGpu = true,
                supportsNnapi = false
            ),
            ModelEntity(
                id = "codellama-7b",
                name = "CodeLLaMA 7B",
                description = "专业代码生成模型",
                size = 7_100_000_000,
                parameters = 7,
                quantization = "Q4_K_M",
                category = ModelCategory.CODE.name,
                downloadUrl = "https://huggingface.co/codellama/CodeLlama-7B-Instruct-q4_k_m/resolve/main/codellama-7b-instruct-q4_k_m.gguf",
                checksum = "",
                minRam = 4096,
                minStorage = 8192,
                recommendedRam = 6144,
                supportsGpu = true,
                supportsNnapi = false
            ),
            ModelEntity(
                id = "llava-7b",
                name = "LLaVA 7B",
                description = "视觉语言多模态模型",
                size = 7_300_000_000,
                parameters = 7,
                quantization = "Q4_K_M",
                category = ModelCategory.VISION.name,
                downloadUrl = "https://huggingface.co/liuhaotian/LLaVA-1.6-7B-q4_k_m/resolve/main/llava-1.6-7b-q4_k_m.gguf",
                checksum = "",
                minRam = 4096,
                minStorage = 9216,
                recommendedRam = 6144,
                supportsGpu = true,
                supportsNnapi = false
            ),
            ModelEntity(
                id = "whisper-tiny",
                name = "Whisper Tiny",
                description = "语音识别模型",
                size = 750_000_000,
                parameters = 1,
                quantization = "Q4_K_M",
                category = ModelCategory.AUDIO.name,
                downloadUrl = "https://huggingface.co/whisper-tiny-q4_k_m/resolve/main/whisper-tiny-q4_k_m.gguf",
                checksum = "",
                minRam = 1024,
                minStorage = 1536,
                recommendedRam = 2048,
                supportsGpu = false,
                supportsNnapi = true
            )
        )
        defaultModels.forEach { modelDao.insert(it) }
    }

    suspend fun downloadModel(modelId: String) {
        val model = modelDao.getModelById(modelId) ?: return
        modelDao.setDownloading(modelId, true, 0f)
        
        try {
            val result = modelDownloader.downloadModel(modelId, model.downloadUrl)
            
            if (result.isSuccess) {
                if (_currentModel.value == null) {
                    switchModel(modelId)
                }
            }
        } catch (e: Exception) {
            modelDao.setDownloading(modelId, false, 0f)
        }
    }

    fun getDownloadProgress(modelId: String): Flow<Float> {
        return modelDownloader.downloadStates.map { states ->
            states[modelId]?.progress ?: 0f
        }
    }

    suspend fun toggleDownload(modelId: String) {
        val model = modelDao.getModelById(modelId) ?: return
        val state = modelDownloader.downloadStates.value[modelId]
        
        if (state?.isDownloading == true) {
            modelDownloader.cancelDownload(modelId)
            modelDao.setDownloading(modelId, false, 0f)
        } else if (model.isInstalled) {
            deleteModel(modelId)
        } else {
            downloadModel(modelId)
        }
    }

    suspend fun deleteModel(modelId: String) {
        modelDownloader.deleteModel(modelId)
        
        if (_currentModel.value?.id == modelId) {
            _currentModel.value = null
        }
    }

    fun getModelPath(modelId: String): String? {
        return modelDownloader.getModelPath(modelId).takeIf { it.exists() }?.absolutePath
    }

    suspend fun updateModelDownloaded(modelId: String, path: String) {
        modelDao.setInstalled(modelId, true, path)
        modelDao.setDownloading(modelId, false, 1f)
    }

    suspend fun updateModelDeleted(modelId: String) {
        modelDao.setInstalled(modelId, false)
        modelDao.setDownloading(modelId, false, 0f)
    }

    private fun ModelEntity.toDomain() = AIModel(
        id = id,
        name = name,
        description = description,
        size = size,
        parameters = parameters,
        quantization = quantization,
        category = ModelCategory.valueOf(category),
        downloadUrl = downloadUrl,
        checksum = checksum,
        minRam = minRam,
        minStorage = minStorage,
        recommendedRam = recommendedRam,
        supportsGpu = supportsGpu,
        supportsNnapi = supportsNnapi,
        isInstalled = isInstalled,
        isDownloading = isDownloading,
        downloadProgress = downloadProgress
    )
}
