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
    val data: String = ""  // 用于返回屏幕摘要等数据
)

@Singleton
class DeviceToolExecutor @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private val ACTION_REGEX = Regex("""\[ACTION:(\w+)\](.*?)\[/ACTION\]""")
        private const val TAG = "DeviceToolExecutor"
    }

    /**
     * 从 AI 输出文本中解析工具调用
     * @return Pair(清理后的文本, 工具调用列表)
     */
    fun parseToolCalls(text: String): Pair<String, List<DeviceToolCall>> {
        val rawCalls = mutableListOf<DeviceToolCall>()
        var cleanText = text
        
        ACTION_REGEX.findAll(text).forEach { match ->
            val toolName = match.groupValues[1]
            val params = match.groupValues[2].trim()
            rawCalls.add(DeviceToolCall(toolName, params))
            Logger.d(TAG, "Parsed tool call: $toolName → $params")
        }
        
        // 从显示文本中移除工具调用标记
        cleanText = ACTION_REGEX.replace(text, "").trim()
        // 清理多余空行
        cleanText = cleanText.replace(Regex("\n{3,}"), "\n\n").trim()
        
        // 过滤和限制工具调用
        val filteredCalls = filterAndLimitToolCalls(rawCalls)
        
        return Pair(cleanText, filteredCalls)
    }
    
    /**
     * 过滤和限制工具调用
     * 1. 限制每次最多1个工具调用
     * 2. 过滤参数无效的调用（如包含模板变量{}）
     * 3. 过滤掉不必要的导航调用
     */
    private fun filterAndLimitToolCalls(calls: List<DeviceToolCall>): List<DeviceToolCall> {
        val validCalls = mutableListOf<DeviceToolCall>()
        
        for (call in calls) {
            // 检查工具名是否有效
            val validTools = setOf(
                "launch_app", "click_text", "input_text", "go_back", 
                "go_home", "open_notifications", "open_quick_settings", 
                "open_recents", "swipe_up", "swipe_down", "swipe_left", 
                "swipe_right", "get_screen", "search_app", "click_at", 
                "long_click"
            )
            
            if (!validTools.contains(call.name)) {
                Logger.w(TAG, "Invalid tool name: ${call.name}, skipping")
                continue
            }
            
            // 检查参数是否有效（不包含模板变量如 {app_name}）
            if (call.params.contains("{") || call.params.contains("}")) {
                Logger.w(TAG, "Invalid params with template variable: ${call.params}, skipping")
                continue
            }
            
            // 对于 launch_app，参数不能为空
            if (call.name == "launch_app" && call.params.isBlank()) {
                Logger.w(TAG, "launch_app requires app name, skipping")
                continue
            }
            
            validCalls.add(call)
            
            // 限制每次只执行1个工具调用
            if (validCalls.size >= 1) {
                break
            }
        }
        
        if (validCalls.size != calls.size) {
            Logger.w(TAG, "Filtered tool calls: ${calls.size} → ${validCalls.size}")
        }
        
        return validCalls
    }

    /**
     * 执行工具调用
     */
    fun executeTool(call: DeviceToolCall): DeviceToolResult {
        val service = NeuralMindAccessibilityService.getInstance()
        
        if (service == null) {
            Logger.w(TAG, "AccessibilityService not running, cannot execute: ${call.name}")
            return DeviceToolResult(
                success = false,
                message = "无障碍服务未开启，请在设置中开启 NeuralMind 无障碍服务"
            )
        }
        
        return when (call.name) {
            "launch_app" -> executeLaunchApp(service, call.params)
            "click_text" -> {
                val result = service.clickByText(call.params, exactMatch = false)
                DeviceToolResult(result, if (result) "已点击: ${call.params}" else "未找到可点击的文字: ${call.params}")
            }
            "click_at" -> {
                val parts = call.params.split(",")
                if (parts.size == 2) {
                    val x = parts[0].trim().toIntOrNull()
                    val y = parts[1].trim().toIntOrNull()
                    if (x != null && y != null) {
                        service.clickAt(x, y)
                        DeviceToolResult(true, "已点击坐标: ($x, $y)")
                    } else {
                        DeviceToolResult(false, "坐标格式错误: ${call.params}")
                    }
                } else {
                    DeviceToolResult(false, "坐标格式: x,y")
                }
            }
            "long_click" -> {
                val parts = call.params.split(",")
                if (parts.size == 2) {
                    val x = parts[0].trim().toIntOrNull()
                    val y = parts[1].trim().toIntOrNull()
                    if (x != null && y != null) {
                        service.longClickAt(x, y)
                        DeviceToolResult(true, "已长按: ($x, $y)")
                    } else {
                        DeviceToolResult(false, "坐标格式错误")
                    }
                } else {
                    DeviceToolResult(false, "坐标格式: x,y")
                }
            }
            "input_text" -> {
                // 格式: "提示文字|要输入的内容" 或 直接输入内容
                val parts = call.params.split("|", limit = 2)
                val result = if (parts.size == 2) {
                    service.findAndInputText(parts[0].trim(), parts[1].trim())
                } else {
                    // 尝试找到当前焦点的编辑框并输入
                    val rootNode = service.rootInActiveWindow
                    if (rootNode != null) {
                        val focusNode = rootNode.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
                        if (focusNode != null && focusNode.isEditable) {
                            val r = service.inputText(focusNode, call.params)
                            focusNode.recycle()
                            rootNode.recycle()
                            r
                        } else {
                            focusNode?.recycle()
                            rootNode.recycle()
                            service.findAndInputText("", call.params)
                        }
                    } else {
                        false
                    }
                }
                DeviceToolResult(result, if (result) "已输入: ${call.params}" else "未找到输入框")
            }
            "go_back" -> {
                service.goBack()
                DeviceToolResult(true, "已返回")
            }
            "go_home" -> {
                service.goHome()
                DeviceToolResult(true, "已回到主页")
            }
            "open_notifications" -> {
                service.openNotifications()
                DeviceToolResult(true, "已打开通知栏")
            }
            "open_quick_settings" -> {
                service.openQuickSettings()
                DeviceToolResult(true, "已打开快捷设置")
            }
            "open_recents" -> {
                service.openRecents()
                DeviceToolResult(true, "已打开最近任务")
            }
            "swipe_up" -> {
                service.swipe(540, 1600, 540, 400, 500)
                DeviceToolResult(true, "已向上滑动")
            }
            "swipe_down" -> {
                service.swipe(540, 400, 540, 1600, 500)
                DeviceToolResult(true, "已向下滑动")
            }
            "swipe_left" -> {
                service.swipe(900, 1000, 200, 1000, 500)
                DeviceToolResult(true, "已向左滑动")
            }
            "swipe_right" -> {
                service.swipe(200, 1000, 900, 1000, 500)
                DeviceToolResult(true, "已向右滑动")
            }
            "get_screen" -> {
                val text = service.getScreenText()
                val app = service.getCurrentApp()
                val summary = buildString {
                    if (app.isNotBlank()) append("当前应用: $app\n")
                    append("屏幕内容: ${text.take(500)}")
                }
                DeviceToolResult(true, summary, summary)
            }
            "search_app" -> {
                // 搜索应用就是回到桌面查找
                service.goHome()
                Thread.sleep(500)
                val found = service.clickByText(call.params, exactMatch = false)
                DeviceToolResult(found, if (found) "找到并打开了: ${call.params}" else "未找到应用: ${call.params}")
            }
            else -> {
                Logger.w(TAG, "Unknown tool: ${call.name}")
                DeviceToolResult(false, "未知工具: ${call.name}")
            }
        }
    }

    /**
     * 像人一样操作：先回桌面，然后找应用图标点击
     */
    private fun executeLaunchApp(service: NeuralMindAccessibilityService, appName: String): DeviceToolResult {
        // 先回桌面
        service.goHome()
        Thread.sleep(500) // 等桌面加载
        
        // 尝试直接点击应用名
        if (service.clickByText(appName, exactMatch = false)) {
            Thread.sleep(300)
            return DeviceToolResult(true, "已打开应用: $appName")
        }
        
        // 桌面没找到，上滑到应用抽屉再找
        service.swipe(540, 1600, 540, 400, 500)
        Thread.sleep(500)
        
        if (service.clickByText(appName, exactMatch = false)) {
            Thread.sleep(300)
            return DeviceToolResult(true, "已打开应用: $appName")
        }
        
        // 再上滑一次尝试
        service.swipe(540, 1600, 540, 400, 500)
        Thread.sleep(500)
        
        if (service.clickByText(appName, exactMatch = false)) {
            Thread.sleep(300)
            return DeviceToolResult(true, "已打开应用: $appName")
        }
        
        return DeviceToolResult(false, "未找到应用: $appName，请在桌面上确认应用名称")
    }
}
