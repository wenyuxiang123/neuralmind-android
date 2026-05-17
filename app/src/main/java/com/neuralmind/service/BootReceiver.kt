package com.neuralmind.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

/**
 * NeuralMind 开机自启接收器
 * 在设备启动完成后自动启动前台助手服务
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == "android.intent.action.QUICKBOOT_POWERON") {
            
            // 启动前台助手服务
            val serviceIntent = Intent(context, NeuralMindAssistantService::class.java).apply {
                action = NeuralMindAssistantService.ACTION_START
            }
            ContextCompat.startForegroundService(context, serviceIntent)
            
            // 注意：不能自动开启无障碍服务，只能引导用户去设置
            // 可以在应用内检测无障碍服务状态并提示用户
        }
    }
}
