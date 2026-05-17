package com.neuralmind.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.neuralmind.R
import com.neuralmind.ui.MainActivity

/**
 * NeuralMind 前台常驻服务
 * 用于保持 AI 助手在后台持续运行，支持语音唤醒等功能
 */
class NeuralMindAssistantService : Service() {

    companion object {
        const val CHANNEL_ID = "neuralmind_assistant"
        const val NOTIFICATION_ID = 1001
        
        const val ACTION_START = "com.neuralmind.action.START_ASSISTANT"
        const val ACTION_STOP = "com.neuralmind.action.STOP_ASSISTANT"
        
        @Volatile
        private var isRunning = false
        
        fun isServiceRunning(): Boolean = isRunning
        
        /**
         * 启动服务
         */
        fun start(context: Context) {
            val intent = Intent(context, NeuralMindAssistantService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
        
        /**
         * 停止服务
         */
        fun stop(context: Context) {
            val intent = Intent(context, NeuralMindAssistantService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    private lateinit var notificationManager: NotificationManager

    // ==================== 服务生命周期 ====================

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        // 创建通知渠道（Android 8.0+）
        createNotificationChannel()
        
        // 显示前台通知
        startForeground(NOTIFICATION_ID, buildNotification())
        
        isRunning = true
        
        // 初始化监听（占位，后续接入语音唤醒）
        startListening()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopListening()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            else -> {
                // ACTION_START 或其他，默认行为已在 onCreate 中处理
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        stopListening()
    }

    // ==================== 通知管理 ====================

    /**
     * 创建通知渠道（Android 8.0+ 需要）
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "NeuralMind 助手",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "NeuralMind AI 助手常驻服务"
                setShowBadge(false)
                enableLights(false)
                enableVibration(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * 构建常驻通知
     */
    private fun buildNotification(): Notification {
        // 点击通知打开主界面
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 停止服务按钮
        val stopIntent = Intent(this, NeuralMindAssistantService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("NeuralMind 助手运行中")
            .setContentText("点击打开 / 语音唤醒待命")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setOngoing(true)
            .setContentIntent(openPendingIntent)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "停止服务",
                stopPendingIntent
            )
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
    }

    /**
     * 更新通知内容
     */
    private fun updateNotification(title: String, content: String) {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setOngoing(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    this,
                    0,
                    Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "停止服务",
                PendingIntent.getService(
                    this,
                    1,
                    Intent(this, NeuralMindAssistantService::class.java).apply {
                        action = ACTION_STOP
                    },
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
        
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    // ==================== 核心功能（占位） ====================

    /**
     * 开始监听（语音唤醒占位）
     * 后续将接入语音识别引擎
     */
    private fun startListening() {
        // TODO: 后续接入语音唤醒引擎
        // 目前仅为占位实现
        updateNotification("NeuralMind 助手运行中", "点击打开 / 语音唤醒待命")
    }

    /**
     * 停止监听
     */
    private fun stopListening() {
        // TODO: 停止语音唤醒引擎
    }

    /**
     * 处理语音唤醒触发
     * 当检测到唤醒词时调用此方法
     */
    fun onWakeWordDetected() {
        // TODO: 唤醒词检测到后的处理
        // 1. 震动/声音提示用户
        // 2. 打开拾音界面
        // 3. 等待用户语音输入
    }

    /**
     * 处理语音输入
     */
    fun onVoiceInput(text: String) {
        // TODO: 将语音输入传递给 AI 处理
    }
}
