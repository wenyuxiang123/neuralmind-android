package com.neuralmind.tools

import android.content.Context
import android.content.Intent
import android.net.Uri
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
    @ApplicationContext private val context: Context,
    private val visionToolExecutor: VisionToolExecutor
) {
    companion object {
        private val ACTION_REGEX = Regex("""\[ACTION:(\w+)\](.*?)\[/ACTION\]""")
        private const val TAG = "DeviceToolExecutor"
        private const val MAX_CLEAN_TEXT_LENGTH = 200
        private const val MAX_TOOL_CALLS = 3
        
        private val VALID_TOOLS = setOf(
            "launch_app", "click_text", "input_text", "go_back", 
            "go_home", "open_notifications", "open_quick_settings", 
            "open_recents", "swipe_up", "swipe_down", "swipe_left", 
            "swipe_right", "get_screen", "search_app", "click_at", 
            "long_click", "open_url", "analyze_screen", "describe_screen",
            "recognize_text", "find_on_screen"
        )
        
        private val FORBIDDEN_PATTERNS = Regex(
            """(?i)(assistant|Human:|user:|human:|ai:|<\|.*?\|>|</?.*?>)"""
        )
        
        private const val CENTER_X = 540
        private const val SCREEN_TOP_Y = 400
        private const val SCREEN_BOTTOM_Y = 1600
        private const val SWIPE_LEFT_X_START = 900
        private const val SWIPE_RIGHT_X_START = 200
        private const val SWIPE_Y = 1000
        private const val SWIPE_DURATION = 500L
        private const val HOME_WAIT_MS = 500L
        private const val APP_CLICK_WAIT_MS = 300L
    }

    fun parseToolCalls(text: String): Pair<String, List<DeviceToolCall>> {
        // 先清理掉所有 assistant 前缀
        var cleanedText = text
            .replace(Regex("""assistant\s*""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""^\s*assistant\s*""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""assistant$""", RegexOption.IGNORE_CASE), "")
        
        val rawCalls = mutableListOf<DeviceToolCall>()
        
        // 1. 先尝试用标准正则匹配
        ACTION_REGEX.findAll(cleanedText).forEach { match ->
            val toolName = match.groupValues[1]
            val params = match.groupValues[2].trim()
            rawCalls.add(DeviceToolCall(toolName, params))
        }
        
        // 2. 如果没有匹配到，尝试修复格式错误的工具调用
        if (rawCalls.isEmpty()) {
            rawCalls.addAll(parseFuzzyToolCalls(cleanedText))
        }
        
        // 3. 还是没有的话，尝试解析冒号格式
        if (rawCalls.isEmpty()) {
            rawCalls.addAll(parseColonFormat(cleanedText))
        }
        
        var cleanText = cleanedText
        
        // 移除所有工具调用
        ACTION_REGEX.findAll(cleanedText).forEach { match ->
            cleanText = cleanText.replace(match.value, "")
        }
        
        cleanText = cleanText.replace(FORBIDDEN_PATTERNS, "")
            .replace(Regex("""\s+"""), " ")
            .replace(Regex("""\n{2,}"""), "\n")
            .trim()
        
        if (cleanText.length > MAX_CLEAN_TEXT_LENGTH) {
            cleanText = cleanText.take(MAX_CLEAN_TEXT_LENGTH) + "..."
        }
        
        return Pair(cleanText, filterAndLimitToolCalls(rawCalls))
    }
    
    private fun parseFuzzyToolCalls(text: String): List<DeviceToolCall> {
        val calls = mutableListOf<DeviceToolCall>()
        val validTools = VALID_TOOLS.joinToString("|")
        
        // 匹配各种格式错误的工具调用
        // 例如：[ACTION:go_home[/ACTION], [ACTION:go_home[/ACTION, ACTION:go_home]params[/ACTION]
        val fuzzyPattern = Regex("""\[?ACTION:($validTools)\]?(.*?)(?:\[/?ACTION\]|$)""", RegexOption.IGNORE_CASE)
        
        fuzzyPattern.findAll(text).forEach { match ->
            val toolName = match.groupValues[1].lowercase()
            var params = match.groupValues[2].trim()
            
            // 清理参数中的无效内容
            params = params.replace(Regex("""\[/?ACTION.*?\]""", RegexOption.IGNORE_CASE), "")
                .replace(Regex("""assistant|user|human|ai""", RegexOption.IGNORE_CASE), "")
                .trim()
            
            if (toolName.isNotEmpty()) {
                calls.add(DeviceToolCall(toolName, params))
            }
        }
        
        return calls
    }
    
    private fun parseColonFormat(text: String): List<DeviceToolCall> {
        val calls = mutableListOf<DeviceToolCall>()
        val validTools = VALID_TOOLS.joinToString("|")
        
        // 匹配冒号格式：launch_app:抖音 或 launch_app: 抖音
        val colonPattern = Regex("""(?:^|\n)($validTools):\s*([^\n]+)""", RegexOption.IGNORE_CASE)
        
        colonPattern.findAll(text).forEach { match ->
            val toolName = match.groupValues[1].lowercase()
            var params = match.groupValues[2].trim()
            
            // 清理参数中的无效内容
            params = params.replace(Regex("""assistant|user|human|ai""", RegexOption.IGNORE_CASE), "")
                .trim()
            
            if (toolName.isNotEmpty()) {
                calls.add(DeviceToolCall(toolName, params))
            }
        }
        
        return calls
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
            if (validCalls.size >= MAX_TOOL_CALLS) break
        }
        
        return validCalls
    }

    fun executeTool(call: DeviceToolCall): DeviceToolResult {
        Logger.i(TAG, "=== executeTool START ===")
        Logger.d(TAG, "executeTool: name=${call.name}, params='${call.params}'")
        
        val service = NeuralMindAccessibilityService.getInstance()
        
        if (service == null) {
            Logger.e(TAG, "executeTool: Accessibility service not running")
            return DeviceToolResult(false, "无障碍服务未开启，请在设置中开启 NeuralMind 无障碍服务")
        }
        
        Logger.d(TAG, "executeTool: Accessibility service is running")
        
        val result = when (call.name) {
            "launch_app" -> executeLaunchApp(service, call.params)
            "click_text" -> executeClickText(service, call.params)
            "click_at" -> executeClickAt(service, call.params)
            "long_click" -> executeLongClick(service, call.params)
            "input_text" -> executeInputText(service, call.params)
            "go_back" -> DeviceToolResult(true, "已返回").also { 
                Logger.d(TAG, "executeTool: goBack()"); service.goBack() 
            }
            "go_home" -> DeviceToolResult(true, "已回到主页").also { 
                Logger.d(TAG, "executeTool: goHome()"); service.goHome() 
            }
            "open_notifications" -> DeviceToolResult(true, "已打开通知栏").also { 
                Logger.d(TAG, "executeTool: openNotifications()"); service.openNotifications() 
            }
            "open_quick_settings" -> DeviceToolResult(true, "已打开快捷设置").also { 
                Logger.d(TAG, "executeTool: openQuickSettings()"); service.openQuickSettings() 
            }
            "open_recents" -> DeviceToolResult(true, "已打开最近任务").also { 
                Logger.d(TAG, "executeTool: openRecents()"); service.openRecents() 
            }
            "swipe_up" -> DeviceToolResult(true, "已向上滑动").also { 
                Logger.d(TAG, "executeTool: swipeUp()"); service.swipe(CENTER_X, SCREEN_BOTTOM_Y, CENTER_X, SCREEN_TOP_Y, SWIPE_DURATION) 
            }
            "swipe_down" -> DeviceToolResult(true, "已向下滑动").also { 
                Logger.d(TAG, "executeTool: swipeDown()"); service.swipe(CENTER_X, SCREEN_TOP_Y, CENTER_X, SCREEN_BOTTOM_Y, SWIPE_DURATION) 
            }
            "swipe_left" -> DeviceToolResult(true, "已向左滑动").also { 
                Logger.d(TAG, "executeTool: swipeLeft()"); service.swipe(SWIPE_LEFT_X_START, SWIPE_Y, SWIPE_RIGHT_X_START, SWIPE_Y, SWIPE_DURATION) 
            }
            "swipe_right" -> DeviceToolResult(true, "已向右滑动").also { 
                Logger.d(TAG, "executeTool: swipeRight()"); service.swipe(SWIPE_RIGHT_X_START, SWIPE_Y, SWIPE_LEFT_X_START, SWIPE_Y, SWIPE_DURATION) 
            }
            "get_screen" -> executeGetScreen(service)
            "search_app" -> executeSearchApp(service, call.params)
            "open_url" -> executeOpenUrl(call.params)
            "analyze_screen" -> executeAnalyzeScreen(call.params)
            "describe_screen" -> executeDescribeScreen()
            "recognize_text" -> executeRecognizeText()
            "find_on_screen" -> executeFindOnScreen(call.params)
            else -> {
                Logger.w(TAG, "executeTool: Unknown tool name: ${call.name}")
                DeviceToolResult(false, "未知工具: ${call.name}")
            }
        }
        
        Logger.i(TAG, "executeTool result: success=${result.success}, msg=${result.message}")
        Logger.i(TAG, "=== executeTool END ===")
        return result
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
        Logger.i(TAG, "=== executeLaunchApp START ===")
        Logger.d(TAG, "executeLaunchApp: appName='$appName'")
        
        Logger.d(TAG, "executeLaunchApp: going home")
        service.goHome()
        Thread.sleep(HOME_WAIT_MS)
        
        Logger.d(TAG, "executeLaunchApp: searching for app on home screen")
        if (service.clickByText(appName, exactMatch = false)) {
            Logger.i(TAG, "executeLaunchApp: found and clicked app on home screen")
            Thread.sleep(APP_CLICK_WAIT_MS)
            return DeviceToolResult(true, "已打开应用: $appName")
        }
        
        Logger.d(TAG, "executeLaunchApp: app not found on home screen, searching pages")
        for (i in 1..2) {
            Logger.d(TAG, "executeLaunchApp: swiping to page $i")
            service.swipe(CENTER_X, SCREEN_BOTTOM_Y, CENTER_X, SCREEN_TOP_Y, SWIPE_DURATION)
            Thread.sleep(HOME_WAIT_MS)
            if (service.clickByText(appName, exactMatch = false)) {
                Logger.i(TAG, "executeLaunchApp: found and clicked app on page $i")
                Thread.sleep(APP_CLICK_WAIT_MS)
                return DeviceToolResult(true, "已打开应用: $appName")
            }
        }
        
        Logger.w(TAG, "executeLaunchApp: app not found after searching all pages")
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
    
    private fun executeOpenUrl(url: String): DeviceToolResult {
        Logger.d(TAG, "executeOpenUrl: url='$url'")
        
        // 验证 URL
        if (url.isBlank()) {
            Logger.w(TAG, "executeOpenUrl: empty URL")
            return DeviceToolResult(false, "URL 不能为空")
        }
        
        // 检查是否是搜索词
        val finalUrl = if (!url.startsWith("http://") && !url.startsWith("https://")) {
            if (url.contains(".") && !url.contains(" ")) {
                "https://$url"
            } else {
                // 使用百度移动端搜索（广告较少）
                "https://m.baidu.com/s?word=${Uri.encode(url)}"
            }
        } else {
            url
        }
        
        Logger.i(TAG, "executeOpenUrl: opening $finalUrl")
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(finalUrl)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        
        return DeviceToolResult(true, "已打开: $finalUrl")
    }
    
    private fun executeAnalyzeScreen(params: String): DeviceToolResult {
        Logger.i(TAG, "executeAnalyzeScreen: params='$params'")
        
        val task = if (params.isNotBlank()) params else "分析当前屏幕内容"
        
        val result = visionToolExecutor.analyzeScreen(task)
        return DeviceToolResult(result.success, result.message, result.data)
    }
    
    private fun executeDescribeScreen(): DeviceToolResult {
        Logger.i(TAG, "executeDescribeScreen")
        
        val result = visionToolExecutor.analyzeCurrentScreen()
        return DeviceToolResult(result.success, result.message, result.data)
    }
    
    private fun executeRecognizeText(): DeviceToolResult {
        Logger.i(TAG, "executeRecognizeText")
        
        val result = visionToolExecutor.recognizeScreenText()
        return DeviceToolResult(result.success, result.message, result.data)
    }
    
    private fun executeFindOnScreen(params: String): DeviceToolResult {
        Logger.i(TAG, "executeFindOnScreen: params='$params'")
        
        if (params.isBlank()) {
            return DeviceToolResult(false, "需要指定要查找的物体或元素名称")
        }
        
        val result = visionToolExecutor.findOnScreen(params)
        return DeviceToolResult(result.success, result.message, result.data)
    }
}
