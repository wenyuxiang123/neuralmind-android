package com.neuralmind.tools

import android.content.Context
import android.content.pm.PackageManager
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
        
        // 常见应用名 → 包名映射
        private val APP_ALIASES = mapOf(
            "微信" to "com.tencent.mm",
            "qq" to "com.tencent.mobileqq",
            "手机qq" to "com.tencent.mobileqq",
            "支付宝" to "com.eg.android.AlipayGphone",
            "淘宝" to "com.taobao.taobao",
            "抖音" to "com.ss.android.ugc.aweme",
            "扣子" to "com.ss.android.ugc.aweme",
            "抖音火山版" to "com.ss.android.ugc.aweme.lite",
            "快手" to "com.smile.gifmaker",
            "微博" to "com.sina.weibo",
            "小红书" to "com.xingin.xhs",
            "番茄小说" to "com.dragon.read",
            "哔哩哔哩" to "tv.danmaku.bili",
            "b站" to "tv.danmaku.bili",
            "百度" to "com.baidu.searchbox",
            "百度地图" to "com.baidu.BaiduMap",
            "高德地图" to "com.autonavi.minimap",
            "美团" to "com.sankuai.meituan",
            "饿了么" to "me.ele",
            "大众点评" to "com.dianping.v1",
            "京东" to "com.jingdong.app.mall",
            "拼多多" to "com.xunmeng.pinduoduo",
            "网易云音乐" to "com.netease.cloudmusic",
            "qq音乐" to "com.tencent.qqmusic",
            "酷狗音乐" to "com.kugou.android",
            "今日头条" to "com.ss.android.article.news",
            "知乎" to "com.zhihu.android",
            "wps" to "cn.wps.moffice_eng",
            "飞书" to "com.ss.android.lark",
            "钉钉" to "com.alibaba.android.rimet",
            "企业微信" to "com.tencent.wework",
            "设置" to "com.android.settings",
            "相机" to "com.android.camera",
            "计算器" to "com.android.calculator2",
            "时钟" to "com.android.deskclock",
            "日历" to "com.android.calendar",
            "电话" to "com.android.dialer",
            "联系人" to "com.android.contacts",
            "短信" to "com.android.messaging",
            "文件管理" to "com.android.filemanager",
            "浏览器" to "com.android.browser",
            "应用商店" to "com.android.vending",
            "喜马拉雅" to "com.ximalaya.ting.android",
            "得到" to "com.luojilab.player",
        )
    }

    /**
     * 从 AI 输出文本中解析工具调用
     * @return Pair(清理后的文本, 工具调用列表)
     */
    fun parseToolCalls(text: String): Pair<String, List<DeviceToolCall>> {
        val calls = mutableListOf<DeviceToolCall>()
        var cleanText = text
        
        ACTION_REGEX.findAll(text).forEach { match ->
            val toolName = match.groupValues[1]
            val params = match.groupValues[2].trim()
            calls.add(DeviceToolCall(toolName, params))
            Logger.d(TAG, "Parsed tool call: $toolName → $params")
        }
        
        // 从显示文本中移除工具调用标记
        cleanText = ACTION_REGEX.replace(text, "").trim()
        // 清理多余空行
        cleanText = cleanText.replace(Regex("\n{3,}"), "\n\n").trim()
        
        return Pair(cleanText, calls)
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
                        val focusNode = rootNode.findFocus(android.view.accessibility.AccessibilityNodeInfo.FOCUS_INPUT)
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
                val result = service.goBack()
                DeviceToolResult(result, if (result) "已返回" else "返回失败")
            }
            "go_home" -> {
                val result = service.goHome()
                DeviceToolResult(result, if (result) "已返回主页" else "返回主页失败")
            }
            "open_notifications" -> {
                val result = service.openNotifications()
                DeviceToolResult(result, if (result) "已打开通知栏" else "打开通知栏失败")
            }
            "open_quick_settings" -> {
                val result = service.openQuickSettings()
                DeviceToolResult(result, if (result) "已打开快速设置" else "打开快速设置失败")
            }
            "open_recents" -> {
                val result = service.openRecents()
                DeviceToolResult(result, if (result) "已打开最近任务" else "打开最近任务失败")
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
                val summary = service.getScreenSummary()
                DeviceToolResult(true, "获取屏幕摘要成功", data = summary)
            }
            "search_app" -> {
                val packageName = resolveAppPackageName(call.params)
                if (packageName != null) {
                    DeviceToolResult(true, "找到应用: $packageName", data = packageName)
                } else {
                    DeviceToolResult(false, "未找到应用: ${call.params}")
                }
            }
            else -> {
                Logger.w(TAG, "Unknown tool: ${call.name}")
                DeviceToolResult(false, "未知工具: ${call.name}")
            }
        }
    }

    /**
     * 启动应用，支持应用名或包名
     */
    private fun executeLaunchApp(service: NeuralMindAccessibilityService, nameOrPackage: String): DeviceToolResult {
        var packageName = resolveAppPackageName(nameOrPackage)
        
        if (packageName == null) {
            return DeviceToolResult(
                success = false,
                message = "未找到应用: $nameOrPackage。请确认应用名称或使用包名。"
            )
        }
        
        var result = service.launchApp(packageName)
        
        // If the resolved package is not installed (e.g. wrong alias for this device),
        // try searching installed apps by name as fallback
        if (!result.success && result.message.contains("未安装")) {
            val altPackage = searchInstalledApp(nameOrPackage)
            if (altPackage != null && altPackage != packageName) {
                Logger.d(TAG, "Alias package $packageName not found, trying installed app: $altPackage")
                packageName = altPackage
                result = service.launchApp(packageName)
            }
        }
        
        return DeviceToolResult(
            success = result.success,
            message = if (result.success) "已打开应用: $nameOrPackage" else "打开应用失败: $nameOrPackage - ${result.message}"
        )
    }

    /**
     * 解析应用名到包名
     * 1. 先查常见应用别名表
     * 2. 如果包含点号，认为是包名直接返回
     * 3. 搜索已安装应用匹配名称
     */
    private fun resolveAppPackageName(name: String): String? {
        // 1. 查别名表
        APP_ALIASES[name.lowercase()]?.let { return it }
        
        // 2. 含点号，可能就是包名
        if (name.contains(".")) return name
        
        // 3. 搜索已安装应用
        return searchInstalledApp(name)
    }

    /**
     * 在已安装应用中搜索匹配的应用
     */
    private fun searchInstalledApp(query: String): String? {
        try {
            val pm = context.packageManager
            val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            
            // 精确匹配应用名
            for (app in apps) {
                val label = app.loadLabel(pm)?.toString() ?: continue
                if (label.equals(query, ignoreCase = true)) {
                    Logger.d(TAG, "Exact match: $label → ${app.packageName}")
                    return app.packageName
                }
            }
            
            // 模糊匹配
            for (app in apps) {
                val label = app.loadLabel(pm)?.toString() ?: continue
                if (label.contains(query, ignoreCase = true) || query.contains(label, ignoreCase = true)) {
                    Logger.d(TAG, "Fuzzy match: $label → ${app.packageName}")
                    return app.packageName
                }
            }
        } catch (e: Exception) {
            Logger.e(TAG, "searchInstalledApp failed", e)
        }
        
        return null
    }
}

