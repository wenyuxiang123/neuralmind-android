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
        return modelDao.getAllModels().map { entities -> entities.map { it.toDomain() } }
    }
    
    suspend fun getInstalledModelsSync(): List<AIModel> {
        return try { modelDao.getInstalledModelsOnce().map { it.toDomain() } }
        catch (e: Exception) { Logger.e(Logger.Tags.REPO, "getInstalledModelsSync failed", e); emptyList() }
    }
    
    fun getInstalledModels(): Flow<List<AIModel>> {
        Logger.d(Logger.Tags.REPO, "getInstalledModels() called")
        return modelDao.getInstalledModels().map { entities -> entities.map { it.toDomain() } }
    }
    
    fun getModelsByCategory(category: ModelCategory): Flow<List<AIModel>> {
        Logger.d(Logger.Tags.REPO, "getModelsByCategory(category=${category.name})")
        return modelDao.getModelsByCategory(category.name).map { entities -> entities.map { it.toDomain() } }
    }
    
    suspend fun getModelById(id: String): AIModel? {
        Logger.d(Logger.Tags.REPO, "getModelById(id=$id)")
        return modelDao.getModelById(id)?.toDomain()
    }
    
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
                createMobileModel("llama3.2-1b", "LLaMA 3.2 1B", "Meta 高效小模型，多语言支持", 850_000_000, 1,
                    "https://modelscope.cn/models/unsloth/Llama-3.2-1B-Instruct-GGUF/resolve/master/Llama-3.2-1B-Instruct-Q4_K_M.gguf", 1024, 1536, 2048),
                createMobileModel("qwen2.5-0.5b", "Qwen2.5 0.5B", "阿里通义轻量模型，中文能力强，极速运行", 420_000_000, 1,
                    "https://modelscope.cn/models/qwen/Qwen2.5-0.5B-Instruct-GGUF/resolve/master/qwen2.5-0.5b-instruct-q4_k_m.gguf", 512, 768, 1024),
                createMobileModel("qwen2.5-1.5b", "Qwen2.5 1.5B", "阿里通义主流模型，中文能力出色", 1_060_000_000, 2,
                    "https://modelscope.cn/models/qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/master/qwen2.5-1.5b-instruct-q4_k_m.gguf", 1024, 1536, 2048),
                createMobileModel("gemma-2-2b", "Gemma 2 2B", "Google 高效语言模型，基于 Gemini 技术", 1_840_000_000, 2,
                    "https://hf-mirror.com/lmstudio-community/gemma-2-2b-it-GGUF/resolve/main/gemma-2-2b-it-q4_k_m.gguf", 1536, 2560, 3072),
                
                createTextModel("llama3.2-3b", "LLaMA 3.2 3B", "Meta 性能平衡模型，适合日常使用", 2_020_000_000, 3,
                    "https://modelscope.cn/models/unsloth/Llama-3.2-3B-Instruct-GGUF/resolve/master/Llama-3.2-3B-Instruct-Q4_K_M.gguf", 2048, 3072, 4096),
                createTextModel("qwen2.5-3b", "Qwen2.5 3B", "阿里通义性能版，代码和数学能力强", 1_930_000_000, 3,
                    "https://modelscope.cn/models/qwen/Qwen2.5-3B-Instruct-GGUF/resolve/master/qwen2.5-3b-instruct-q4_k_m.gguf", 2048, 3072, 4096, nnapi = false),
                createTextModel("mistral-7b", "Mistral 7B", "高质量通用模型，支持函数调用", 4_690_000_000, 7,
                    "https://hf-mirror.com/bartowski/Mistral-7B-Instruct-v0.3-GGUF/resolve/main/Mistral-7B-Instruct-v0.3-Q4_K_M.gguf", 4096, 6144, 6144, nnapi = false),
                createTextModel("llama3.1-8b", "LLaMA 3.1 8B", "Meta 最新大模型，多语言支持，128K 上下文", 5_280_000_000, 8,
                    "https://modelscope.cn/models/AI-ModelScope/Meta-Llama-3.1-8B-Instruct-GGUF/resolve/master/Meta-Llama-3.1-8B-Instruct-Q4_K_M.gguf", 5120, 7168, 8192, nnapi = false),
                
                createCodeModel("phi-3.5-mini", "Phi-3.5 Mini", "Microsoft 高性能小模型，代码和推理能力强", 2_560_000_000, 3,
                    "https://modelscope.cn/models/LLM-Research/Phi-3.5-mini-instruct-GGUF/resolve/master/Phi-3.5-mini-instruct-Q4_K_M.gguf", 2048, 3584, 4096),
                createCodeModel("qwen2.5-coder-1.5b", "Qwen2.5-Coder 1.5B", "阿里代码专用模型，代码生成能力强", 1_060_000_000, 2,
                    "https://modelscope.cn/models/qwen/Qwen2.5-Coder-1.5B-Instruct-GGUF/resolve/master/qwen2.5-coder-1.5b-instruct-q4_k_m.gguf", 1024, 1536, 2048)
            )
            
            defaultModels.forEach { modelDao.insert(it) }
            Logger.i(Logger.Tags.REPO, "insertDefaultModels success: ${defaultModels.size} models inserted")
        } catch (e: Exception) {
            Logger.e(Logger.Tags.REPO, "insertDefaultModels failed", e)
        }
    }
    
    private fun createModel(id: String, name: String, desc: String, size: Long, params: Int, 
                           url: String, minRam: Int, minStorage: Int, recommendedRam: Int,
                           category: ModelCategory, nnapi: Boolean = true) = ModelEntity(
        id = id, name = name, description = desc, size = size, parameters = params,
        quantization = "Q4_K_M", category = category.name, downloadUrl = url,
        checksum = "", minRam = minRam, minStorage = minStorage, recommendedRam = recommendedRam,
        supportsGpu = true, supportsNnapi = nnapi
    )
    
    private fun createMobileModel(id: String, name: String, desc: String, size: Long, params: Int,
                                  url: String, minRam: Int, minStorage: Int, recommendedRam: Int) =
        createModel(id, name, desc, size, params, url, minRam, minStorage, recommendedRam, ModelCategory.MOBILE)
    
    private fun createTextModel(id: String, name: String, desc: String, size: Long, params: Int,
                                url: String, minRam: Int, minStorage: Int, recommendedRam: Int, nnapi: Boolean = true) =
        createModel(id, name, desc, size, params, url, minRam, minStorage, recommendedRam, ModelCategory.TEXT, nnapi)
    
    private fun createCodeModel(id: String, name: String, desc: String, size: Long, params: Int,
                                url: String, minRam: Int, minStorage: Int, recommendedRam: Int) =
        createModel(id, name, desc, size, params, url, minRam, minStorage, recommendedRam, ModelCategory.CODE, nnapi = false)
    
    suspend fun downloadModel(modelId: String) {
        Logger.d(Logger.Tags.REPO, "downloadModel(modelId=$modelId)")
        try {
            val model = modelDao.getModelById(modelId)
            if (model == null) { Logger.w(Logger.Tags.REPO, "downloadModel: model not found, modelId=$modelId"); return }
            
            modelDao.setDownloading(modelId, true, 0f)
            Logger.d(Logger.Tags.REPO, "downloadModel: set downloading=true for $modelId")
            
            try {
                val result = modelDownloader.downloadModel(modelId, model.downloadUrl)
                if (result.isSuccess) { Logger.i(Logger.Tags.REPO, "downloadModel success: $modelId"); switchModel(modelId) }
                else { Logger.e(Logger.Tags.REPO, "downloadModel failed: $modelId, error=${result.exceptionOrNull()?.message}") }
            } catch (e: Exception) {
                Logger.e(Logger.Tags.REPO, "downloadModel exception for modelId=$modelId", e)
                try { modelDao.setDownloading(modelId, false, 0f) } catch (dbEx: Exception) { Logger.e(Logger.Tags.REPO, "Failed to reset download state for $modelId", dbEx) }
            }
        } catch (e: Exception) { Logger.e(Logger.Tags.REPO, "downloadModel error: modelId=$modelId", e) }
    }
    
    fun getDownloadProgress(modelId: String): Flow<Float> {
        Logger.d(Logger.Tags.REPO, "getDownloadProgress(modelId=$modelId)")
        return modelDownloader.downloadStates.map { states -> states[modelId]?.progress ?: 0f }
    }
    
    suspend fun toggleDownload(modelId: String) {
        Logger.d(Logger.Tags.REPO, "toggleDownload(modelId=$modelId)")
        try {
            val model = modelDao.getModelById(modelId)
            if (model == null) { Logger.w(Logger.Tags.REPO, "toggleDownload: model not found, modelId=$modelId"); return }
            
            val state = modelDownloader.downloadStates.value[modelId]
            when {
                state?.isDownloading == true -> { Logger.d(Logger.Tags.REPO, "toggleDownload: cancelling download for $modelId"); modelDownloader.cancelDownload(modelId); modelDao.setDownloading(modelId, false, 0f) }
                model.isInstalled -> { Logger.d(Logger.Tags.REPO, "toggleDownload: deleting model $modelId"); deleteModel(modelId) }
                else -> { Logger.d(Logger.Tags.REPO, "toggleDownload: starting download for $modelId"); downloadModel(modelId) }
            }
        } catch (e: Exception) { Logger.e(Logger.Tags.REPO, "toggleDownload failed for modelId=$modelId", e) }
    }
    
    suspend fun deleteModel(modelId: String) {
        Logger.d(Logger.Tags.REPO, "deleteModel(modelId=$modelId)")
        try {
            modelDownloader.deleteModel(modelId)
            if (_currentModel.value?.id == modelId) { _currentModel.value = null; Logger.d(Logger.Tags.REPO, "deleteModel: cleared current model") }
            Logger.i(Logger.Tags.REPO, "deleteModel success: $modelId")
        } catch (e: Exception) { Logger.e(Logger.Tags.REPO, "deleteModel failed for modelId=$modelId", e) }
    }
    
    suspend fun updateModelDownloaded(modelId: String, path: String) {
        Logger.d(Logger.Tags.REPO, "updateModelDownloaded(modelId=$modelId, path=$path)")
        try { modelDao.setInstalled(modelId, true, path); modelDao.setDownloading(modelId, false, 1f); Logger.i(Logger.Tags.REPO, "updateModelDownloaded success: $modelId") }
        catch (e: Exception) { Logger.e(Logger.Tags.REPO, "updateModelDownloaded failed: modelId=$modelId", e) }
    }
    
    suspend fun updateModelDeleted(modelId: String) {
        Logger.d(Logger.Tags.REPO, "updateModelDeleted(modelId=$modelId)")
        try { modelDao.setInstalled(modelId, false); modelDao.setDownloading(modelId, false, 0f); Logger.i(Logger.Tags.REPO, "updateModelDeleted success: $modelId") }
        catch (e: Exception) { Logger.e(Logger.Tags.REPO, "updateModelDeleted failed: modelId=$modelId", e) }
    }
    
    private fun ModelEntity.toDomain() = AIModel(
        id = id, name = name, description = description, size = size, parameters = parameters, quantization = quantization,
        category = ModelCategory.valueOf(category), downloadUrl = downloadUrl, checksum = checksum, minRam = minRam,
        minStorage = minStorage, recommendedRam = recommendedRam, supportsGpu = supportsGpu, supportsNnapi = supportsNnapi,
        isInstalled = isInstalled, isDownloading = isDownloading, downloadProgress = downloadProgress
    )
}
