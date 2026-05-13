package com.neuralmind.network

import android.content.Context
import com.neuralmind.data.repository.ModelRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import dagger.Lazy

@Singleton
class ModelDownloader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val networkManager: NetworkManager,
    private val modelRepository: Lazy<ModelRepository>
) {
    private val scope = CoroutineScope(Dispatchers.IO)

    data class DownloadState(
        val isDownloading: Boolean = false,
        val progress: Float = 0f,
        val downloadedBytes: Long = 0L,
        val totalBytes: Long = 0L,
        val error: String? = null,
        val isComplete: Boolean = false
    )

    private val _downloadStates = MutableStateFlow<Map<String, DownloadState>>(emptyMap())
    val downloadStates: StateFlow<Map<String, DownloadState>> = _downloadStates

    private val modelDir: File by lazy {
        File(context.filesDir, "models").apply { mkdirs() }
    }

    fun getModelPath(modelId: String): File {
        return File(modelDir, "$modelId.gguf")
    }

    suspend fun downloadModel(
        modelId: String,
        downloadUrl: String
    ): Result<File> = withContext(Dispatchers.IO) {
        // 更新下载状态
        updateState(modelId, DownloadState(isDownloading = true))
        
        val targetFile = getModelPath(modelId)
        
        try {
            val result = networkManager.downloadFile(
                url = downloadUrl,
                targetFile = targetFile,
                downloadId = "model_$modelId"
            ) { downloaded, total ->
                val progress = if (total > 0) {
                    downloaded.toFloat() / total.toFloat()
                } else {
                    0f
                }
                updateState(modelId, DownloadState(
                    isDownloading = true,
                    progress = progress,
                    downloadedBytes = downloaded,
                    totalBytes = total
                ))
            }

            if (result.isSuccess) {
                // 下载完成，更新状态
                updateState(modelId, DownloadState(
                    isDownloading = false,
                    progress = 1f,
                    downloadedBytes = targetFile.length(),
                    totalBytes = targetFile.length(),
                    isComplete = true
                ))
                
                // 更新模型状态
                modelRepository.get().updateModelDownloaded(modelId, targetFile.absolutePath)
            }

            result
        } catch (e: Exception) {
            // 如果是取消导致的异常，不要删除部分文件（方便续传）
            val isCancelled = e is java.io.IOException && e.message?.contains("cancelled", ignoreCase = true) == true
            
            if (!isCancelled && targetFile.exists()) {
                targetFile.delete()
            }
            
            // 更新状态
            updateState(modelId, DownloadState(
                isDownloading = false,
                error = if (isCancelled) null else (e.message ?: "Unknown error"),
                // 如果是取消，保留当前进度
                progress = if (isCancelled) _downloadStates.value[modelId]?.progress ?: 0f else 0f,
                downloadedBytes = if (isCancelled) _downloadStates.value[modelId]?.downloadedBytes ?: 0L else 0L,
                totalBytes = if (isCancelled) _downloadStates.value[modelId]?.totalBytes ?: 0L else 0L
            ))

            Result.failure(e)
        }
    }

    fun cancelDownload(modelId: String) {
        networkManager.cancelDownload("model_$modelId")
        updateState(modelId, DownloadState(isDownloading = false))
    }

    fun deleteModel(modelId: String): Boolean {
        val file = getModelPath(modelId)
        return if (file.exists()) {
            file.delete()
            scope.launch {
                modelRepository.get().updateModelDeleted(modelId)
            }
            _downloadStates.value = _downloadStates.value - modelId
            true
        } else {
            false
        }
    }

    fun isModelDownloaded(modelId: String): Boolean {
        return getModelPath(modelId).exists()
    }

    private fun updateState(modelId: String, state: DownloadState) {
        _downloadStates.value = _downloadStates.value.toMutableMap().apply {
            this[modelId] = state
        }
    }
}
