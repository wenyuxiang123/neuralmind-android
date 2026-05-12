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
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

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
        progressCallback: (Long, Long) -> Unit = { _, _ -> }
    ): Result<File> = runCatching {
        val request = createRequest(url)
        val response = client.newCall(request).execute()
        
        if (!response.isSuccessful) {
            throw IOException("Download failed: ${response.code}")
        }

        val body = response.body ?: throw IOException("Empty response body")
        val totalBytes = body.contentLength()
        var downloadedBytes = 0L

        // 确保父目录存在
        targetFile.parentFile?.mkdirs()

        // 保存到文件
        targetFile.outputStream().use { output ->
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
