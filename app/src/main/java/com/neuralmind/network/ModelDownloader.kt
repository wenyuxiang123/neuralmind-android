package com.neuralmind.network

import android.content.Context
import com.neuralmind.core.Logger
import com.neuralmind.data.repository.ModelRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap
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
        val isComplete: Boolean = false,
        val currentSource: String = ""
    )
    
    private val _downloadStates = MutableStateFlow<Map<String, DownloadState>>(emptyMap())
    val downloadStates: StateFlow<Map<String, DownloadState>> = _downloadStates
    
    private val modelDir: File by lazy {
        File(context.filesDir, "models").apply { mkdirs() }
    }
    
    private val downloadSpeeds = ConcurrentHashMap<String, Long>()
    
    // 多镜像源配置 - 按优先级排序
    private fun getMirrorUrls(originalUrl: String): List<String> {
        val mirrors = mutableListOf<String>()
        
        if (originalUrl.contains("huggingface.co")) {
            mirrors.add(originalUrl.replace("huggingface.co", "hf-mirror.com"))
            mirrors.add(originalUrl.replace("huggingface.co", "hf-mirror.com"))
        } else if (originalUrl.contains("hf-mirror.com")) {
            mirrors.add(originalUrl)
            mirrors.add(originalUrl.replace("hf-mirror.com/openbmb", "modelscope.cn/models/OpenBMB"))
        } else if (originalUrl.contains("modelscope.cn")) {
            mirrors.add(originalUrl)
            mirrors.add(originalUrl.replace("modelscope.cn/models/OpenBMB", "hf-mirror.com/openbmb"))
        } else {
            mirrors.add(originalUrl)
        }
        
        return mirrors.distinct()
    }
    
    fun getModelPath(modelId: String): File {
        return File(modelDir, "$modelId.gguf")
    }
    
    suspend fun downloadModel(
        modelId: String,
        downloadUrl: String
    ): Result<File> = withContext(Dispatchers.IO) {
        Logger.d(Logger.Tags.NET, "downloadModel(modelId=$modelId, url=$downloadUrl)")
        
        val mirrors = getMirrorUrls(downloadUrl)
        Logger.d(Logger.Tags.NET, "downloadModel: available mirrors: $mirrors")
        
        // 更新下载状态
        updateState(modelId, DownloadState(isDownloading = true))
        
        val targetFile = getModelPath(modelId)
        
        for ((index, mirrorUrl) in mirrors.withIndex()) {
            Logger.d(Logger.Tags.NET, "downloadModel: trying mirror ${index + 1}/${mirrors.size}: $mirrorUrl")
            
            // 更新当前使用的源
            updateState(modelId, DownloadState(isDownloading = true, currentSource = mirrorUrl))
            
            try {
                val result = tryDownloadWithMirror(modelId, mirrorUrl, targetFile)
                
                if (result.isSuccess) {
                    updateState(modelId, DownloadState(
                        isDownloading = false,
                        progress = 1f,
                        downloadedBytes = targetFile.length(),
                        totalBytes = targetFile.length(),
                        isComplete = true,
                        currentSource = mirrorUrl
                    ))
                    
                    modelRepository.get().updateModelDownloaded(modelId, targetFile.absolutePath)
                    Logger.i(Logger.Tags.NET, "downloadModel completed: $modelId from $mirrorUrl, size=${targetFile.length()}")
                    return@withContext result
                } else {
                    Logger.w(Logger.Tags.NET, "downloadModel: mirror $mirrorUrl failed: ${result.exceptionOrNull()?.message}")
                    if (index < mirrors.size - 1) {
                        Logger.d(Logger.Tags.NET, "downloadModel: trying next mirror...")
                        delay(1000)
                    }
                }
            } catch (e: Exception) {
                Logger.e(Logger.Tags.NET, "downloadModel: mirror $mirrorUrl exception", e)
                if (index < mirrors.size - 1) {
                    Logger.d(Logger.Tags.NET, "downloadModel: trying next mirror...")
                    delay(1000)
                } else {
                    throw e
                }
            }
        }
        
        updateState(modelId, DownloadState(
            isDownloading = false,
            error = "所有镜像源都无法下载，请检查网络连接后重试"
        ))
        
        Result.failure(Exception("所有镜像源都无法下载"))
    }
    
    private suspend fun tryDownloadWithMirror(
        modelId: String,
        mirrorUrl: String,
        targetFile: File
    ): Result<File> = withContext(Dispatchers.IO) {
        Logger.d(Logger.Tags.NET, "tryDownloadWithMirror: $mirrorUrl")
        
        var downloadedBytes = 0L
        var lastProgressTime = System.currentTimeMillis()
        var lastProgressBytes = 0L
        
        if (targetFile.exists() && targetFile.length() > 0) {
            downloadedBytes = targetFile.length()
            Logger.d(Logger.Tags.NET, "tryDownloadWithMirror: resuming from $downloadedBytes bytes")
        }
        
        val result = networkManager.downloadFile(
            url = mirrorUrl,
            targetFile = targetFile,
            downloadId = "model_$modelId"
        ) { downloaded, total ->
            val currentTime = System.currentTimeMillis()
            val elapsedMs = currentTime - lastProgressTime
            
            if (elapsedMs > 0) {
                val bytesDelta = downloaded - lastProgressBytes
                val speedBps = (bytesDelta * 1000) / elapsedMs
                downloadSpeeds[modelId] = speedBps
                
                if (elapsedMs > 5000 && bytesDelta < 1024 * 100) {
                    Logger.w(Logger.Tags.NET, "tryDownloadWithMirror: slow download detected ($speedBps bytes/s), will retry with next mirror")
                    networkManager.cancelDownload("model_$modelId")
                }
            }
            
            lastProgressTime = currentTime
            lastProgressBytes = downloaded
            
            val progress = if (total > 0) {
                downloaded.toFloat() / total.toFloat()
            } else {
                0f
            }
            
            updateState(modelId, DownloadState(
                isDownloading = true,
                progress = progress,
                downloadedBytes = downloaded,
                totalBytes = total,
                currentSource = mirrorUrl
            ))
        }
        
        result
    }
    
    fun cancelDownload(modelId: String) {
        Logger.d(Logger.Tags.NET, "cancelDownload(modelId=$modelId)")
        networkManager.cancelDownload("model_$modelId")
        updateState(modelId, DownloadState(isDownloading = false))
    }
    
    fun deleteModel(modelId: String): Boolean {
        Logger.d(Logger.Tags.NET, "deleteModel(modelId=$modelId)")
        val file = getModelPath(modelId)
        return if (file.exists()) {
            val deleted = file.delete()
            if (deleted) {
                scope.launch {
                    modelRepository.get().updateModelDeleted(modelId)
                }
                _downloadStates.value = _downloadStates.value - modelId
                Logger.i(Logger.Tags.NET, "deleteModel success: $modelId")
            } else {
                Logger.w(Logger.Tags.NET, "deleteModel failed to delete file: $modelId")
            }
            deleted
        } else {
            Logger.w(Logger.Tags.NET, "deleteModel: file not found, $modelId")
            false
        }
    }
    
    fun isModelDownloaded(modelId: String): Boolean {
        val exists = getModelPath(modelId).exists()
        Logger.d(Logger.Tags.NET, "isModelDownloaded($modelId): $exists")
        return exists
    }
    
    private fun updateState(modelId: String, state: DownloadState) {
        _downloadStates.value = _downloadStates.value.toMutableMap().apply {
            this[modelId] = state
        }
    }
}