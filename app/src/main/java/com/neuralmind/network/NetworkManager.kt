package com.neuralmind.network

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
        .readTimeout(300, TimeUnit.SECONDS)   // 5分钟读超时，适合大文件
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    // 当前活跃的下载调用，用于取消
    private val activeCalls = mutableMapOf<String, okhttp3.Call>()

    fun cancelDownload(downloadId: String) {
        activeCalls[downloadId]?.cancel()
        activeCalls.remove(downloadId)
    }

    fun createRequest(url: String): Request {
        return Request.Builder()
            .url(url)
            .build()
    }

    fun executeRequest(request: Request): Response {
        return client.newCall(request).execute()
    }

    suspend fun downloadFile(
        url: String,
        targetFile: File,
        downloadId: String = url,
        progressCallback: (Long, Long) -> Unit = { _, _ -> }
    ): Result<File> = runCatching {
        var downloadedBytes = 0L
        
        // 断点续传：检查已下载的部分
        if (targetFile.exists() && targetFile.length() > 0) {
            downloadedBytes = targetFile.length()
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
        
        // 追加写入（断点续传时追加，否则覆盖）
        val append = downloadedBytes > 0 && response.code == 206
        
        targetFile.parentFile?.mkdirs()
        targetFile.outputStream(append).use { output ->
            body.byteStream().use { input ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    downloadedBytes += bytesRead
                    progressCallback(downloadedBytes, totalBytes)
                }
            }
        }
        
        targetFile
    }

    suspend fun getString(url: String): Result<String> = runCatching {
        val request = createRequest(url)
        val response = client.newCall(request).execute()
        
        if (!response.isSuccessful) {
            throw IOException("Request failed: ${response.code}")
        }

        response.body?.string() ?: ""
    }

    suspend fun getByteArray(url: String): Result<ByteArray> = runCatching {
        val request = createRequest(url)
        val response = client.newCall(request).execute()
        
        if (!response.isSuccessful) {
            throw IOException("Request failed: ${response.code}")
        }

        response.body?.bytes() ?: ByteArray(0)
    }
}
