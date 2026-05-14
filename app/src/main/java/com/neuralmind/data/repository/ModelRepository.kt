package com.neuralmind.data.repository

import android.content.Context
import com.neuralmind.core.Logger
import com.neuralmind.data.local.db.dao.ModelDao
import com.neuralmind.data.local.db.entity.ModelEntity
import com.neuralmind.domain.model.AIModel
import com.neuralmind.domain.model.ModelCategory
import com.neuralmind.network.ModelDownloader
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

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
        Logger.d(Logger.Tags.REPO, "getAllModels() called")
        return modelDao.getAllModels().map { entities ->
            entities.map { it.toDomain() }
        }
    }
    
    fun getInstalledModels(): Flow<List<AIModel>> {
        Logger.d(Logger.Tags.REPO, "getInstalledModels() called")
        return modelDao.getInstalledModels().map { entities ->
            entities.map { it.toDomain() }
        }
    }
    
    fun getModelsByCategory(category: ModelCategory): Flow<List<AIModel>> {
        Logger.d(Logger.Tags.REPO, "getModelsByCategory(category=${category.name})")
        return modelDao.getModelsByCategory(category.name).map { entities ->
            entities.map { it.toDomain() }
        }
    }
    
    suspend fun getModelById(id: String): AIModel? {
        Logger.d(Logger.Tags.REPO, "getModelById(id=$id)")
        return modelDao.getModelById(id)?.toDomain()
    }
    
    /**
     * Get the local file path for a downloaded model.
     * @param modelId The model identifier
     * @return The absolute path to the model file, or null if not installed / file missing
     */
    fun getModelPath(modelId: String): String? {
        Logger.d(Logger.Tags.REPO, "getModelPath(modelId=$modelId)")
        return modelDownloader.getModelPath(modelId).takeIf { it.exists() }?.absolutePath
    }
    
    suspend fun switchModel(modelId: String) {
        Logger.d(Logger.Tags.REPO, "switchModel(modelId=$modelId)")
        try {
            val model = getModelById(modelId)
            _currentModel.value = model
            Logger.i(Logger.Tags.REPO, "switchModel success: ${model?.name}")
        } catch (e: Exception) {
            Logger.e(Logger.Tags.REPO, "switchModel failed for modelId=$modelId", e)
        }
    }
    
    suspend fun insertDefaultModels() {
        Logger.d(Logger.Tags.REPO, "insertDefaultModels() called")
        try {
            if (modelDao.getModelById("llama3.2-1b") != null) {
                Logger.d(Logger.Tags.REPO, "Default models already exist, skipping")
                return
            }
            
            val defaultModels = listOf(
                // Mobile models - 适合手机的高效模型
                ModelEntity(
                    id = "llama3.2-1b",
                    name = "LLaMA 3.2 1B",
                    description = "Meta 高效小模型，多语言支持",
                    size = 850_000_000,
                    parameters = 1,
                    quantization = "Q4_K_M",
                    category = ModelCategory.MOBILE.name,
                    downloadUrl = "https://hf-mirror.com/hugging-quants/Llama-3.2-1B-Instruct-Q4_K_M-GGUF/resolve/main/llama-3.2-1b-instruct-q4_k_m.gguf",
                    checksum = "",
                    minRam = 1024,
                    minStorage = 1536,
                    recommendedRam = 2048,
                    supportsGpu = true,
                    supportsNnapi = true
                ),
                ModelEntity(
                    id = "qwen2.5-0.5b",
                    name = "Qwen2.5 0.5B",
                    description = "阿里通义轻量模型，中文能力强，极速运行",
                    size = 420_000_000,
                    parameters = 1,
                    quantization = "Q4_K_M",
                    category = ModelCategory.MOBILE.name,
                    downloadUrl = "https://hf-mirror.com/Qwen/Qwen2.5-0.5B-Instruct-GGUF/resolve/main/qwen2.5-0.5b-instruct-q4_k_m.gguf",
                    checksum = "",
                    minRam = 512,
                    minStorage = 768,
                    recommendedRam = 1024,
                    supportsGpu = true,
                    supportsNnapi = true
                ),
                ModelEntity(
                    id = "qwen2.5-1.5b",
                    name = "Qwen2.5 1.5B",
                    description = "阿里通义主流模型，中文能力出色",
                    size = 1_060_000_000,
                    parameters = 2,
                    quantization = "Q4_K_M",
                    category = ModelCategory.MOBILE.name,
                    downloadUrl = "https://hf-mirror.com/lmstudio-community/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/Qwen2.5-1.5B-Instruct-Q4_K_M.gguf",
                    checksum = "",
                    minRam = 1024,
                    minStorage = 1536,
                    recommendedRam = 2048,
                    supportsGpu = true,
                    supportsNnapi = true
                ),
                ModelEntity(
                    id = "gemma-2-2b",
                    name = "Gemma 2 2B",
                    description = "Google 高效语言模型，基于 Gemini 技术",
                    size = 1_840_000_000,
                    parameters = 2,
                    quantization = "Q4_K_M",
                    category = ModelCategory.MOBILE.name,
                    downloadUrl = "https://hf-mirror.com/lmstudio-community/gemma-2-2b-it-GGUF/resolve/main/gemma-2-2b-it-q4_k_m.gguf",
                    checksum = "",
                    minRam = 1536,
                    minStorage = 2560,
                    recommendedRam = 3072,
                    supportsGpu = true,
                    supportsNnapi = true
                ),
                // Text models - 通用文本生成模型
                ModelEntity(
                    id = "llama3.2-3b",
                    name = "LLaMA 3.2 3B",
                    description = "Meta 性能平衡模型，适合日常使用",
                    size = 2_020_000_000,
                    parameters = 3,
                    quantization = "Q4_K_M",
                    category = ModelCategory.TEXT.name,
                    downloadUrl = "https://hf-mirror.com/MaziyarPanahi/Llama-3.2-3B-Instruct-GGUF/resolve/main/Llama-3.2-3B-Instruct.Q4_K_M.gguf",
                    checksum = "",
                    minRam = 2048,
                    minStorage = 3072,
                    recommendedRam = 4096,
                    supportsGpu = true,
                    supportsNnapi = true
                ),
                ModelEntity(
                    id = "qwen2.5-3b",
                    name = "Qwen2.5 3B",
                    description = "阿里通义性能版，代码和数学能力强",
                    size = 1_930_000_000,
                    parameters = 3,
                    quantization = "Q4_K_M",
                    category = ModelCategory.TEXT.name,
                    downloadUrl = "https://hf-mirror.com/tensorblock/Qwen2.5-3B-Instruct-GGUF/resolve/main/Qwen2.5-3B-Instruct-Q4_K_M.gguf",
                    checksum = "",
                    minRam = 2048,
                    minStorage = 3072,
                    recommendedRam = 4096,
                    supportsGpu = true,
                    supportsNnapi = false
                ),
                ModelEntity(
                    id = "mistral-7b",
                    name = "Mistral 7B",
                    description = "高质量通用模型，支持函数调用",
                    size = 4_690_000_000,
                    parameters = 7,
                    quantization = "Q4_K_M",
                    category = ModelCategory.TEXT.name,
                    downloadUrl = "https://hf-mirror.com/bartowski/Mistral-7B-Instruct-v0.3-GGUF/resolve/main/Mistral-7B-Instruct-v0.3-Q4_K_M.gguf",
                    checksum = "",
                    minRam = 4096,
                    minStorage = 6144,
                    recommendedRam = 6144,
                    supportsGpu = true,
                    supportsNnapi = false
                ),
                ModelEntity(
                    id = "llama3.1-8b",
                    name = "LLaMA 3.1 8B",
                    description = "Meta 最新大模型，多语言支持，128K 上下文",
                    size = 5_280_000_000,
                    parameters = 8,
                    quantization = "Q4_K_M",
                    category = ModelCategory.TEXT.name,
                    downloadUrl = "https://hf-mirror.com/bartowski/Meta-Llama-3.1-8B-Instruct-GGUF/resolve/main/Meta-Llama-3.1-8B-Instruct-Q4_K_M.gguf",
                    checksum = "",
                    minRam = 5120,
                    minStorage = 7168,
                    recommendedRam = 8192,
                    supportsGpu = true,
                    supportsNnapi = false
                ),
                // Code models - 专业代码生成模型
                ModelEntity(
                    id = "phi-3.5-mini",
                    name = "Phi-3.5 Mini",
                    description = "Microsoft 高性能小模型，代码和推理能力强",
                    size = 2_560_000_000,
                    parameters = 3,
                    quantization = "Q4_K_M",
                    category = ModelCategory.CODE.name,
                    downloadUrl = "https://hf-mirror.com/bartowski/Phi-3.5-mini-instruct-GGUF/resolve/main/Phi-3.5-mini-instruct-Q4_K_M.gguf",
                    checksum = "",
                    minRam = 2048,
                    minStorage = 3584,
                    recommendedRam = 4096,
                    supportsGpu = true,
                    supportsNnapi = false
                ),
                ModelEntity(
                    id = "qwen2.5-coder-1.5b",
                    name = "Qwen2.5-Coder 1.5B",
                    description = "阿里代码专用模型，代码生成能力强",
                    size = 1_060_000_000,
                    parameters = 2,
                    quantization = "Q4_K_M",
                    category = ModelCategory.CODE.name,
                    downloadUrl = "https://hf-mirror.com/bartowski/Qwen2.5-Coder-1.5B-Instruct-GGUF/resolve/main/Qwen2.5-Coder-1.5B-Instruct-Q4_K_M.gguf",
                    checksum = "",
                    minRam = 1024,
                    minStorage = 1536,
                    recommendedRam = 2048,
                    supportsGpu = true,
                    supportsNnapi = false
                )
            )
            
            defaultModels.forEach { modelDao.insert(it) }
            Logger.i(Logger.Tags.REPO, "insertDefaultModels success: ${defaultModels.size} models inserted")
        } catch (e: Exception) {
            Logger.e(Logger.Tags.REPO, "insertDefaultModels failed", e)
        }
    }
    
    suspend fun downloadModel(modelId: String) {
        Logger.d(Logger.Tags.REPO, "downloadModel(modelId=$modelId)")
        try {
            val model = modelDao.getModelById(modelId)
            if (model == null) {
                Logger.w(Logger.Tags.REPO, "downloadModel: model not found, modelId=$modelId")
                return
            }
            
            modelDao.setDownloading(modelId, true, 0f)
            Logger.d(Logger.Tags.REPO, "downloadModel: set downloading=true for $modelId")
            
            try {
                val result = modelDownloader.downloadModel(modelId, model.downloadUrl)
                
                if (result.isSuccess) {
                    Logger.i(Logger.Tags.REPO, "downloadModel success: $modelId")
                    // 下载完成后自动切换为当前模型
                    switchModel(modelId)
                } else {
                    Logger.e(Logger.Tags.REPO, "downloadModel failed: $modelId, error=${result.exceptionOrNull()?.message}")
                }
            } catch (e: Exception) {
                Logger.e(Logger.Tags.REPO, "downloadModel exception for modelId=$modelId", e)
                try {
                    modelDao.setDownloading(modelId, false, 0f)
                } catch (dbEx: Exception) {
                    Logger.e(Logger.Tags.REPO, "Failed to reset download state for $modelId", dbEx)
                }
            }
        } catch (e: Exception) {
            Logger.e(Logger.Tags.REPO, "downloadModel error: modelId=$modelId", e)
        }
    }
    
    fun getDownloadProgress(modelId: String): Flow<Float> {
        Logger.d(Logger.Tags.REPO, "getDownloadProgress(modelId=$modelId)")
        return modelDownloader.downloadStates.map { states ->
            states[modelId]?.progress ?: 0f
        }
    }
    
    suspend fun toggleDownload(modelId: String) {
        Logger.d(Logger.Tags.REPO, "toggleDownload(modelId=$modelId)")
        try {
            val model = modelDao.getModelById(modelId)
            if (model == null) {
                Logger.w(Logger.Tags.REPO, "toggleDownload: model not found, modelId=$modelId")
                return
            }
            
            val state = modelDownloader.downloadStates.value[modelId]
            
            when {
                state?.isDownloading == true -> {
                    Logger.d(Logger.Tags.REPO, "toggleDownload: cancelling download for $modelId")
                    modelDownloader.cancelDownload(modelId)
                    modelDao.setDownloading(modelId, false, 0f)
                }
                model.isInstalled -> {
                    Logger.d(Logger.Tags.REPO, "toggleDownload: deleting model $modelId")
                    deleteModel(modelId)
                }
                else -> {
                    Logger.d(Logger.Tags.REPO, "toggleDownload: starting download for $modelId")
                    downloadModel(modelId)
                }
            }
        } catch (e: Exception) {
            Logger.e(Logger.Tags.REPO, "toggleDownload failed for modelId=$modelId", e)
        }
    }
    
    suspend fun deleteModel(modelId: String) {
        Logger.d(Logger.Tags.REPO, "deleteModel(modelId=$modelId)")
        try {
            modelDownloader.deleteModel(modelId)
            
            if (_currentModel.value?.id == modelId) {
                _currentModel.value = null
                Logger.d(Logger.Tags.REPO, "deleteModel: cleared current model")
            }
            Logger.i(Logger.Tags.REPO, "deleteModel success: $modelId")
        } catch (e: Exception) {
            Logger.e(Logger.Tags.REPO, "deleteModel failed for modelId=$modelId", e)
        }
    }
    
    suspend fun updateModelDownloaded(modelId: String, path: String) {
        Logger.d(Logger.Tags.REPO, "updateModelDownloaded(modelId=$modelId, path=$path)")
        try {
            modelDao.setInstalled(modelId, true, path)
            modelDao.setDownloading(modelId, false, 1f)
            Logger.i(Logger.Tags.REPO, "updateModelDownloaded success: $modelId")
        } catch (e: Exception) {
            Logger.e(Logger.Tags.REPO, "updateModelDownloaded failed for modelId=$modelId", e)
        }
    }
    
    suspend fun updateModelDeleted(modelId: String) {
        Logger.d(Logger.Tags.REPO, "updateModelDeleted(modelId=$modelId)")
        try {
            modelDao.setInstalled(modelId, false)
            modelDao.setDownloading(modelId, false, 0f)
            Logger.i(Logger.Tags.REPO, "updateModelDeleted success: $modelId")
        } catch (e: Exception) {
            Logger.e(Logger.Tags.REPO, "updateModelDeleted failed for modelId=$modelId", e)
        }
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
