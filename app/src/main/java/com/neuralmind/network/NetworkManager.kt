package com.neuralmind.network

import com.neuralmind.core.Logger
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NetworkManager @Inject constructor() {
    
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(600, TimeUnit.SECONDS)   // 10分钟读超时，适合大文件在慢网下的下载
        .writeTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)                  // 跟随 HTTP 重定向
        .followSslRedirects(true)              // 跟随 HTTPS 重定向（镜像站可能跳转）
        .connectionPool(okhttp3.ConnectionPool(10, 5, TimeUnit.MINUTES))  // 增加连接池大小
        .cache(null)                            // 大文件不需要缓存
        .build()
    
    // 当前活跃的下载调用，用于取消
    private val activeCalls = mutableMapOf<String, okhttp3.Call>()
    
    fun cancelDownload(downloadId: String) {
        Logger.d(Logger.Tags.NET, "cancelDownload(downloadId=$downloadId)")
        activeCalls[downloadId]?.cancel()
        activeCalls.remove(downloadId)
    }
    
    fun createRequest(url: String): Request {
        Logger.d(Logger.Tags.NET, "createRequest(url=$url)")
        return Request.Builder()
            .url(url)
            .build()
    }
    
    fun executeRequest(request: Request): Response {
        Logger.d(Logger.Tags.NET, "executeRequest(url=${request.url})")
        return client.newCall(request).execute()
    }
    
    suspend fun downloadFile(
        url: String,
        targetFile: File,
        downloadId: String = url,
        progressCallback: (Long, Long) -> Unit = { _, _ -> }
    ): Result<File> = runCatching {
        Logger.d(Logger.Tags.NET, "downloadFile: url=$url, target=${targetFile.name}")
        
        var downloadedBytes = 0L
        
        // 断点续传：检查已下载的部分
        if (targetFile.exists() && targetFile.length() > 0) {
            downloadedBytes = targetFile.length()
            Logger.d(Logger.Tags.NET, "downloadFile: resuming from $downloadedBytes bytes")
        }
        
        val requestBuilder = Request.Builder().url(url)
        if (downloadedBytes > 0) {
            requestBuilder.header("Range", "bytes=$downloadedBytes-")
        }
        
        val request = requestBuilder.build()
        val call = client.newCall(request)
        activeCalls[downloadId] = call
        
        val response = call.execute()
        activeCalls.remove(downloadId)
        
        Logger.d(Logger.Tags.NET, "downloadFile: response code=${response.code}")
        
        if (!response.isSuccessful && response.code != 206) {
            throw IOException("Download failed: ${response.code}")
        }
        
        val body = response.body ?: throw IOException("Empty response body")
        
        // 计算总大小
        val totalBytes = if (response.code == 206) {
            // 部分内容响应，从 Content-Range 获取总大小
            val contentRange = response.header("Content-Range")
            val totalFromRange = contentRange?.let {
                val match = Regex("""bytes \d+-\d+/(\d+)""").find(it)
                match?.groupValues?.get(1)?.toLongOrNull()
            }
            totalFromRange ?: (downloadedBytes + body.contentLength())
        } else {
            body.contentLength()
        }
        
        Logger.d(Logger.Tags.NET, "downloadFile: totalBytes=$totalBytes")
        
        // 追加写入（断点续传时追加，否则覆盖）
        val append = downloadedBytes > 0 && response.code == 206
        
        targetFile.parentFile?.mkdirs()
        // 使用更大的缓冲区（1MB）提升下载性能
        java.io.BufferedOutputStream(java.io.FileOutputStream(targetFile, append), 1048576).use { output ->
            body.byteStream().use { input ->
                val buffer = ByteArray(262144)  // 256KB buffer for better I/O performance
                var bytesRead: Int
                var lastMilestone = 0L
                var lastCallbackBytes = 0L
                val callbackInterval = 1048576L  // 1MB interval for progress callback
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    downloadedBytes += bytesRead
                    
                    // 每10%输出一次进度日志
                    if (totalBytes > 0) {
                        val progress = (downloadedBytes * 100 / totalBytes).toInt()
                        if (progress >= lastMilestone + 10) {
                            Logger.d(Logger.Tags.NET, "downloadFile: progress $progress% (${downloadedBytes}/${totalBytes})")
                            lastMilestone = progress.toLong()
                        }
                    }
                    
                    // 降频回调：每 1MB 或进度变化>=1% 时回调
                    if (downloadedBytes - lastCallbackBytes >= callbackInterval || 
                        (totalBytes > 0 && (downloadedBytes * 100 / totalBytes) > (lastCallbackBytes * 100 / totalBytes))) {
                        progressCallback(downloadedBytes, totalBytes)
                        lastCallbackBytes = downloadedBytes
                    }
                }
                // 下载完成，最终回调
                progressCallback(downloadedBytes, totalBytes)
            }
        }
        
        Logger.i(Logger.Tags.NET, "downloadFile: completed ${targetFile.length()} bytes")
        targetFile
    }
    
    suspend fun getString(url: String): Result<String> = runCatching {
        Logger.d(Logger.Tags.NET, "getString(url=$url)")
        val request = createRequest(url)
        val response = client.newCall(request).execute()
        
        if (!response.isSuccessful) {
            Logger.e(Logger.Tags.NET, "getString failed: ${response.code}")
            throw IOException("Request failed: ${response.code}")
        }
        
        val result = response.body?.string() ?: ""
        Logger.d(Logger.Tags.NET, "getString: ${result.length} chars")
        result
    }
    
    suspend fun getByteArray(url: String): Result<ByteArray> = runCatching {
        Logger.d(Logger.Tags.NET, "getByteArray(url=$url)")
        val request = createRequest(url)
        val response = client.newCall(request).execute()
        
        if (!response.isSuccessful) {
            Logger.e(Logger.Tags.NET, "getByteArray failed: ${response.code}")
            throw IOException("Request failed: ${response.code}")
        }
        
        val result = response.body?.bytes() ?: ByteArray(0)
        Logger.d(Logger.Tags.NET, "getByteArray: ${result.size} bytes")
        result
    }
}
