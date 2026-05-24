package com.neuralmind.device

import android.app.Activity
import android.app.Activity.RESULT_OK
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.view.WindowManager
import com.neuralmind.core.Logger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

@Singleton
class ScreenCaptureManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    
    companion object {
        private const val TAG = "ScreenCapture"
        private const val REQUEST_MEDIA_PROJECTION = 1001
    }
    
    private var mediaProjection: MediaProjection? = null
    private var imageReader: ImageReader? = null
    private var isCapturing = false
    
    private val screenshotsDir: File
        get() = File(context.filesDir, "screenshots").apply { mkdirs() }
    
    data class ScreenshotResult(
        val success: Boolean,
        val filePath: String? = null,
        val error: String? = null
    )
    
    fun setMediaProjection(projection: MediaProjection) {
        this.mediaProjection = projection
        Logger.i(TAG, "MediaProjection set successfully")
    }
    
    fun hasMediaProjection(): Boolean = mediaProjection != null
    
    fun requestMediaProjectionPermission(activity: Activity) {
        val projectionManager = activity.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        activity.startActivityForResult(
            projectionManager.createScreenCaptureIntent(),
            REQUEST_MEDIA_PROJECTION
        )
    }
    
    fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean {
        if (requestCode == REQUEST_MEDIA_PROJECTION && resultCode == RESULT_OK && data != null) {
            val projectionManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            mediaProjection = projectionManager.getMediaProjection(resultCode, data)
            Logger.i(TAG, "MediaProjection permission granted")
            return true
        }
        return false
    }
    
    suspend fun captureScreen(): ScreenshotResult = suspendCancellableCoroutine { continuation ->
        Logger.d(TAG, "Capturing screen")
        
        val projection = mediaProjection
        if (projection == null) {
            Logger.e(TAG, "MediaProjection not available, need permission")
            continuation.resume(ScreenshotResult(false, error = "需要媒体投影权限，请在设置中授权屏幕录制"))
            return@suspendCancellableCoroutine
        }
        
        try {
            val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val displayMetrics = DisplayMetrics()
            windowManager.defaultDisplay.getRealMetrics(displayMetrics)
            
            val width = displayMetrics.widthPixels
            val height = displayMetrics.heightPixels
            
            Logger.d(TAG, "Screen size: ${width}x${height}")
            
            imageReader?.close()
            imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
            
            val virtualDisplay = projection.createVirtualDisplay(
                "NeuralMindScreenshot",
                width,
                height,
                displayMetrics.densityDpi,
                android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                imageReader!!.surface,
                null,
                Handler(Looper.getMainLooper())
            )
            
            imageReader!!.setOnImageAvailableListener({ reader ->
                try {
                    val image = reader.acquireLatestImage()
                    if (image != null) {
                        val planes = image.planes
                        val buffer = planes[0].buffer
                        val pixelStride = planes[0].pixelStride
                        val rowStride = planes[0].rowStride
                        val rowPadding = rowStride - pixelStride * width
                        
                        val bitmap = Bitmap.createBitmap(
                            width + rowPadding / pixelStride,
                            height,
                            Bitmap.Config.ARGB_8888
                        )
                        
                        bitmap.copyPixelsFromBuffer(buffer)
                        
                        val file = saveBitmap(bitmap)
                        bitmap.recycle()
                        image.close()
                        virtualDisplay.release()
                        
                        if (file != null) {
                            Logger.i(TAG, "Screenshot saved: ${file.absolutePath}")
                            continuation.resume(ScreenshotResult(true, file.absolutePath))
                        } else {
                            continuation.resume(ScreenshotResult(false, error = "保存截图失败"))
                        }
                    }
                } catch (e: Exception) {
                    Logger.e(TAG, "Error processing image", e)
                    continuation.resume(ScreenshotResult(false, error = e.message))
                }
            }, Handler(Looper.getMainLooper()))
            
            Thread.sleep(200)
            
        } catch (e: Exception) {
            Logger.e(TAG, "Error capturing screen", e)
            continuation.resume(ScreenshotResult(false, error = e.message))
        }
    }
    
    private fun saveBitmap(bitmap: Bitmap): File? {
        return try {
            val timestamp = System.currentTimeMillis()
            val file = File(screenshotsDir, "screenshot_$timestamp.jpg")
            
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }
            
            file
        } catch (e: Exception) {
            Logger.e(TAG, "Error saving bitmap", e)
            null
        }
    }
    
    fun getLatestScreenshot(): File? {
        return screenshotsDir.listFiles()
            ?.filter { it.extension == "jpg" }
            ?.maxByOrNull { it.lastModified() }
    }
    
    fun cleanupOldScreenshots(maxAgeMs: Long = 24 * 60 * 60 * 1000) {
        val cutoffTime = System.currentTimeMillis() - maxAgeMs
        screenshotsDir.listFiles()
            ?.filter { it.lastModified() < cutoffTime }
            ?.forEach { file ->
                file.delete()
                Logger.d(TAG, "Deleted old screenshot: ${file.name}")
            }
    }
    
    fun release() {
        imageReader?.close()
        imageReader = null
        mediaProjection?.stop()
        mediaProjection = null
        isCapturing = false
        Logger.i(TAG, "ScreenCaptureManager released")
    }
}