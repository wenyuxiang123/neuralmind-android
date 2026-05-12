package com.neuralmind.data.repository

import android.content.Context
import com.neuralmind.data.local.db.dao.ModelDao
import com.neuralmind.data.local.db.entity.ModelEntity
import com.neuralmind.domain.model.AIModel
import com.neuralmind.domain.model.ModelCategory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext

class ModelRepository @Inject constructor(
    private val modelDao: ModelDao,
    private val context: Context
) {
    private val client = OkHttpClient()
    private val modelsDir = File(context.filesDir, "models")

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

    suspend fun insertDefaultModels() {
        val defaultModels = listOf(
            ModelEntity(
                id = "llama3.2-1b",
                name = "LLaMA 3.2 1B",
                description = "适合手机的高效模型",
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
                description = "阿里高效小模型",
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
            )
        )
        defaultModels.forEach { modelDao.insert(it) }
    }

    suspend fun downloadModel(modelId: String): Flow<Float> = flow {
        val model = modelDao.getModelById(modelId) ?: return@flow
        modelDao.setDownloading(modelId, true, 0f)

        val file = File(modelsDir, "$modelId.gguf")

        try {
            val request = Request.Builder()
                .url(model.downloadUrl)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    modelDao.setDownloading(modelId, false, 0f)
                    return@flow
                }

                val body = response.body ?: return@flow
                val contentLength = body.contentLength()
                var bytesRead = 0L

                FileOutputStream(file).use { output ->
                    body.byteStream().use { input ->
                        val buffer = ByteArray(8192)
                        var read: Int
                        while (input.read(buffer).also { read = it } != -1) {
                            output.write(buffer, 0, read)
                            bytesRead += read
                            val progress = if (contentLength > 0) {
                                (bytesRead.toFloat() / contentLength)
                            } else 0f
                            modelDao.setDownloading(modelId, true, progress)
                            emit(progress)
                        }
                    }
                }
            }

            modelDao.setInstalled(modelId, true, file.absolutePath)
            modelDao.setDownloading(modelId, false, 1f)
        } catch (e: Exception) {
            modelDao.setDownloading(modelId, false, 0f)
            file.delete()
        }
    }

    suspend fun deleteModel(modelId: String) {
        val model = modelDao.getModelById(modelId)
        model?.localPath?.let { path ->
            File(path).delete()
        }
        modelDao.setInstalled(modelId, false)
    }

    fun getModelPath(modelId: String): String? {
        return File(modelsDir, "$modelId.gguf").takeIf { it.exists() }?.absolutePath
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
