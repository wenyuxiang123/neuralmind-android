package com.neuralmind.tools

import android.content.Context
import android.view.accessibility.AccessibilityNodeInfo
import com.neuralmind.service.NeuralMindAccessibilityService
import com.neuralmind.core.Logger
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

data class DeviceToolCall(
    val name: String,
    val params: String
)

data class DeviceToolResult(
    val success: Boolean,
    val message: String,
    val data: String = ""
)

@Singleton
class DeviceToolExecutor @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private val ACTION_REGEX = Regex("""\[ACTION:(\w+)\](.*?)\[/ACTION\]""")
        private const val TAG = "DeviceToolExecutor"
        
        private val VALID_TOOLS = setOf(
            "launch_app", "click_text", "input_text", "go_back", 
            "go_home", "open_notifications", "open_quick_settings", 
            "open_recents", "swipe_up", "swipe_down", "swipe_left", 
            "swipe_right", "get_screen", "search_app", "click_at", 
            "long_click"
        )
        
        private const val CENTER_X = 540
        private const val SCREEN_TOP_Y = 400
        private const val SCREEN_BOTTOM_Y = 1600
        private const val SWIPE_LEFT_X_START = 900
        private const val SWIPE_RIGHT_X_START = 200
        private const val SWIPE_Y = 1000
        private const val SWIPE_DURATION = 500
        private const val HOME_WAIT_MS = 500L
        private const val APP_CLICK_WAIT_MS = 300L
    }

    fun parseToolCalls(text: String): Pair<String, List<DeviceToolCall>> {
        val rawCalls = mutableListOf<DeviceToolCall>()
        
        ACTION_REGEX.findAll(text).forEach { match ->
            val toolName = match.groupValues[1]
            val params = match.groupValues[2].trim()
            rawCalls.add(DeviceToolCall(toolName, params))
        }
        
        val cleanText = ACTION_REGEX.replace(text, "").trim()
            .replace(Regex("\n{3,}"), "\n\n").trim()
        
        return Pair(cleanText, filterAndLimitToolCalls(rawCalls))
    }
    
    private fun filterAndLimitToolCalls(calls: List<DeviceToolCall>): List<DeviceToolCall> {
        val validCalls = mutableListOf<DeviceToolCall>()
        
        for (call in calls) {
            if (!VALID_TOOLS.contains(call.name)) {
                Logger.w(TAG, "Invalid tool name: ${call.name}, skipping")
                continue
            }
            
            if (call.params.contains("{") || call.params.contains("}")) {
                Logger.w(TAG, "Invalid params with template variable: ${call.params}, skipping")
                continue
            }
            
            if (call.name == "launch_app" && call.params.isBlank()) {
                Logger.w(TAG, "launch_app requires app name, skipping")
                continue
            }
            
            validCalls.add(call)
            if (validCalls.size >= 1) break
        }
        
        return validCalls
    }

    fun executeTool(call: DeviceToolCall): DeviceToolResult {
        val service = NeuralMindAccessibilityService.getInstance()
        
        if (service == null) {
            return DeviceToolResult(false, "无障碍服务未开启，请在设置中开启 NeuralMind 无障碍服务")
        }
        
        return when (call.name) {
            "launch_app" -> executeLaunchApp(service, call.params)
            "click_text" -> executeClickText(service, call.params)
            "click_at" -> executeClickAt(service, call.params)
            "long_click" -> executeLongClick(service, call.params)
            "input_text" -> executeInputText(service, call.params)
            "go_back" -> DeviceToolResult(true, "已返回").also { service.goBack() }
            "go_home" -> DeviceToolResult(true, "已回到主页").also { service.goHome() }
            "open_notifications" -> DeviceToolResult(true, "已打开通知栏").also { service.openNotifications() }
            "open_quick_settings" -> DeviceToolResult(true, "已打开快捷设置").also { service.openQuickSettings() }
            "open_recents" -> DeviceToolResult(true, "已打开最近任务").also { service.openRecents() }
            "swipe_up" -> DeviceToolResult(true, "已向上滑动").also { service.swipe(CENTER_X, SCREEN_BOTTOM_Y, CENTER_X, SCREEN_TOP_Y, SWIPE_DURATION) }
            "swipe_down" -> DeviceToolResult(true, "已向下滑动").also { service.swipe(CENTER_X, SCREEN_TOP_Y, CENTER_X, SCREEN_BOTTOM_Y, SWIPE_DURATION) }
            "swipe_left" -> DeviceToolResult(true, "已向左滑动").also { service.swipe(SWIPE_LEFT_X_START, SWIPE_Y, SWIPE_RIGHT_X_START, SWIPE_Y, SWIPE_DURATION) }
            "swipe_right" -> DeviceToolResult(true, "已向右滑动").also { service.swipe(SWIPE_RIGHT_X_START, SWIPE_Y, SWIPE_LEFT_X_START, SWIPE_Y, SWIPE_DURATION) }
            "get_screen" -> executeGetScreen(service)
            "search_app" -> executeSearchApp(service, call.params)
            else -> DeviceToolResult(false, "未知工具: ${call.name}")
        }
    }
    
    private fun executeClickText(service: NeuralMindAccessibilityService, params: String) =
        if (service.clickByText(params, exactMatch = false))
            DeviceToolResult(true, "已点击: $params")
        else
            DeviceToolResult(false, "未找到可点击的文字: $params")
    
    private fun executeClickAt(service: NeuralMindAccessibilityService, params: String): DeviceToolResult {
        val coords = parseCoordinates(params) ?: return DeviceToolResult(false, "坐标格式错误: $params")
        service.clickAt(coords.first, coords.second)
        return DeviceToolResult(true, "已点击坐标: ($coords)")
    }
    
    private fun executeLongClick(service: NeuralMindAccessibilityService, params: String): DeviceToolResult {
        val coords = parseCoordinates(params) ?: return DeviceToolResult(false, "坐标格式错误")
        service.longClickAt(coords.first, coords.second)
        return DeviceToolResult(true, "已长按: ($coords)")
    }
    
    private fun executeInputText(service: NeuralMindAccessibilityService, params: String): DeviceToolResult {
        val parts = params.split("|", limit = 2)
        val result = if (parts.size == 2) {
            service.findAndInputText(parts[0].trim(), parts[1].trim())
        } else {
            val rootNode = service.rootInActiveWindow
            if (rootNode != null) {
                val focusNode = rootNode.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
                if (focusNode != null && focusNode.isEditable) {
                    val r = service.inputText(focusNode, params)
                    focusNode.recycle()
                    rootNode.recycle()
                    r
                } else {
                    focusNode?.recycle()
                    rootNode.recycle()
                    service.findAndInputText("", params)
                }
            } else false
        }
        return DeviceToolResult(result, if (result) "已输入: $params" else "未找到输入框")
    }
    
    private fun executeGetScreen(service: NeuralMindAccessibilityService): DeviceToolResult {
        val text = service.getScreenText()
        val app = service.getCurrentApp()
        val summary = buildString {
            if (app.isNotBlank()) append("当前应用: $app\n")
            append("屏幕内容: ${text.take(500)}")
        }
        return DeviceToolResult(true, summary, summary)
    }
    
    private fun executeSearchApp(service: NeuralMindAccessibilityService, params: String): DeviceToolResult {
        service.goHome()
        Thread.sleep(HOME_WAIT_MS)
        val found = service.clickByText(params, exactMatch = false)
        return DeviceToolResult(found, if (found) "找到并打开了: $params" else "未找到应用: $params")
    }

    private fun executeLaunchApp(service: NeuralMindAccessibilityService, appName: String): DeviceToolResult {
        service.goHome()
        Thread.sleep(HOME_WAIT_MS)
        
        if (service.clickByText(appName, exactMatch = false)) {
            Thread.sleep(APP_CLICK_WAIT_MS)
            return DeviceToolResult(true, "已打开应用: $appName")
        }
        
        for (i in 1..2) {
            service.swipe(CENTER_X, SCREEN_BOTTOM_Y, CENTER_X, SCREEN_TOP_Y, SWIPE_DURATION)
            Thread.sleep(HOME_WAIT_MS)
            if (service.clickByText(appName, exactMatch = false)) {
                Thread.sleep(APP_CLICK_WAIT_MS)
                return DeviceToolResult(true, "已打开应用: $appName")
            }
        }
        
        return DeviceToolResult(false, "未找到应用: $appName，请在桌面上确认应用名称")
    }
    
    private fun parseCoordinates(params: String): Pair<Int, Int>? {
        val parts = params.split(",")
        return if (parts.size == 2) {
            val x = parts[0].trim().toIntOrNull()
            val y = parts[1].trim().toIntOrNull()
            if (x != null && y != null) x to y else null
        } else null
    }
}
