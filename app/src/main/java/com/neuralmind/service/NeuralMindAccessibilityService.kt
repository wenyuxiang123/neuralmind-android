package com.neuralmind.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.graphics.Rect
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import android.content.pm.PackageManager

/**
 * NeuralMind 无障碍服务 - 屏幕操控核心
 * 提供"上帝模式"能力，让 AI 可以读取和操控屏幕
 */
class NeuralMindAccessibilityService : AccessibilityService() {

    companion object {
        private var instance: NeuralMindAccessibilityService? = null
        
        fun getInstance(): NeuralMindAccessibilityService? = instance
        
        fun isRunning(): Boolean = instance != null
    }

    // 最近的事件记录
    private var lastEvent: AccessibilityEvent? = null
    private val recentEvents = mutableListOf<AccessibilityEvent>()
    private val maxRecentEvents = 20

    // ==================== 服务生命周期 ====================

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        
        // 配置服务信息
        val info = AccessibilityServiceInfo().apply {
            // 监听所有事件类型
            eventTypes = AccessibilityEvent.TYPES_ALL_MASK
            // 通用反馈类型
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            // 设置标志：报告视图ID、可检索交互节点、包含不重要视图
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                    AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
            // 通知超时 100ms
            notificationTimeout = 100
        }
        serviceInfo = info
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event?.let {
            lastEvent = it
            synchronized(recentEvents) {
                recentEvents.add(it)
                if (recentEvents.size > maxRecentEvents) {
                    recentEvents.removeAt(0)
                }
            }
        }
    }

    override fun onInterrupt() {
        // 服务中断时调用
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        synchronized(recentEvents) {
            recentEvents.clear()
        }
    }

    // ==================== 屏幕内容读取 ====================

    /**
     * 读取当前屏幕所有文字内容
     */
    fun getScreenText(): String {
        val rootNode = rootInActiveWindow ?: return ""
        return rootNode.text?.toString() ?: buildString {
            traverseText(rootNode, this)
        }.also { rootNode.recycle() }
    }

    private fun traverseText(node: AccessibilityNodeInfo, builder: StringBuilder) {
        node.text?.let { text ->
            if (text.isNotEmpty()) {
                builder.append(text)
                builder.append("\n")
            }
        }
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { child ->
                traverseText(child, builder)
                child.recycle()
            }
        }
    }

    /**
     * 获取当前包名
     */
    fun getCurrentApp(): String {
        return rootInActiveWindow?.packageName?.toString() ?: ""
    }

    // ==================== 节点查找与操作 ====================

    /**
     * 查找并点击包含指定文字的节点
     * @param text 要查找的文字
     * @param exactMatch 是否精确匹配
     * @return 是否成功
     */
    fun clickByText(text: String, exactMatch: Boolean = false): Boolean {
        val rootNode = rootInActiveWindow ?: return false
        
        val node = findNodeByText(rootNode, text, exactMatch)
        if (node != null) {
            val result = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            node.recycle()
            rootNode.recycle()
            return result
        }
        
        rootNode.recycle()
        return false
    }

    /**
     * 查找并点击指定 ID 的节点
     * @param id 视图 ID（不包含包名）
     * @return 是否成功
     */
    fun clickById(id: String): Boolean {
        val rootNode = rootInActiveWindow ?: return false
        
        val node = findNodeByViewId(rootNode, id)
        if (node != null) {
            val result = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            node.recycle()
            rootNode.recycle()
            return result
        }
        
        rootNode.recycle()
        return false
    }

    /**
     * 在指定节点输入文字
     * @param nodeInfo 输入框节点
     * @param text 要输入的文字
     * @return 是否成功
     */
    fun inputText(nodeInfo: AccessibilityNodeInfo, text: String): Boolean {
        val arguments = android.os.Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        return nodeInfo.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
    }

    /**
     * 查找包含指定 hint 的输入框并输入文字
     * @param hint 输入框的 hint 文字
     * @param text 要输入的文字
     * @return 是否成功
     */
    fun findAndInputText(hint: String, text: String): Boolean {
        val rootNode = rootInActiveWindow ?: return false
        
        val node = findInputNodeByHint(rootNode, hint)
        if (node != null) {
            val result = inputText(node, text)
            node.recycle()
            rootNode.recycle()
            return result
        }
        
        rootNode.recycle()
        return false
    }

    // ==================== 手势操作 ====================

    /**
     * 点击指定坐标
     * 使用 dispatchGesture API（API 24+）
     */
    fun clickAt(x: Int, y: Int) {
        val clickPath = android.graphics.Path().apply {
            moveTo(x.toFloat(), y.toFloat())
        }
        val gesture = android.accessibilityservice.GestureDescription.Builder()
            .addStroke(android.accessibilityservice.GestureDescription.StrokeDescription(clickPath, 0, 100))
            .build()
        dispatchGesture(gesture, null, null)
    }

    /**
     * 长按指定坐标
     */
    fun longClickAt(x: Int, y: Int) {
        val longClickPath = android.graphics.Path().apply {
            moveTo(x.toFloat(), y.toFloat())
        }
        val gesture = android.accessibilityservice.GestureDescription.Builder()
            .addStroke(android.accessibilityservice.GestureDescription.StrokeDescription(longClickPath, 0, 1000))
            .build()
        dispatchGesture(gesture, null, null)
    }

    /**
     * 滑动手势
     * @param startX 起始 X 坐标
     * @param startY 起始 Y 坐标
     * @param endX 结束 X 坐标
     * @param endY 结束 Y 坐标
     * @param duration 持续时间（毫秒）
     */
    fun swipe(startX: Int, startY: Int, endX: Int, endY: Int, duration: Long = 500) {
        val swipePath = android.graphics.Path().apply {
            moveTo(startX.toFloat(), startY.toFloat())
            lineTo(endX.toFloat(), endY.toFloat())
        }
        val gesture = android.accessibilityservice.GestureDescription.Builder()
            .addStroke(android.accessibilityservice.GestureDescription.StrokeDescription(swipePath, 0, duration))
            .build()
        dispatchGesture(gesture, null, null)
    }

    // ==================== 全局操作 ====================

    /**
     * 返回操作
     */
    fun goBack(): Boolean {
        return performGlobalAction(GLOBAL_ACTION_BACK)
    }

    /**
     * 返回主页
     */
    fun goHome(): Boolean {
        return performGlobalAction(GLOBAL_ACTION_HOME)
    }

    /**
     * 打开最近任务列表
     */
    fun openRecents(): Boolean {
        return performGlobalAction(GLOBAL_ACTION_RECENTS)
    }

    /**
     * 打开通知栏
     */
    fun openNotifications(): Boolean {
        return performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
    }

    /**
     * 打开快速设置
     */
    fun openQuickSettings(): Boolean {
        return performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)
    }

    /**
     * 启动指定应用
     * @param packageName 包名
     * @return LaunchResult 包含成功/失败和详细原因
     */
    fun launchApp(packageName: String): LaunchResult {
        // 先检查应用是否安装
        try {
            packageManager.getPackageInfo(packageName, 0)
        } catch (e: PackageManager.NameNotFoundException) {
            return LaunchResult(false, "应用未安装: $packageName")
        }
        
        // 方式1: getLaunchIntentForPackage
        val intent = packageManager.getLaunchIntentForPackage(packageName)
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            return LaunchResult(true, "已打开应用")
        }
        
        // 方式2: Fallback - 用 ACTION_MAIN + CATEGORY_LAUNCHER
        val mainIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
            setPackage(packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            startActivity(mainIntent)
            return LaunchResult(true, "已打开应用(fallback)")
        } catch (e: Exception) {
            // 方式3: 最后尝试 - 通过 resolveActivity 找到 launcher activity
            val resolveIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
                setPackage(packageName)
            }
            val resolveInfo = packageManager.resolveActivity(resolveIntent, 0)
            if (resolveInfo != null) {
                val launchIntent = Intent(Intent.ACTION_MAIN).apply {
                    setClassName(packageName, resolveInfo.activityInfo.name)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                try {
                    startActivity(launchIntent)
                    return LaunchResult(true, "已打开应用(resolved)")
                } catch (e2: Exception) {
                    return LaunchResult(false, "启动失败: ${e2.message}")
                }
            }
            return LaunchResult(false, "未找到启动入口: $packageName")
        }
    }

    /**
     * 应用启动结果
     */
    data class LaunchResult(
        val success: Boolean,
        val message: String
    )

    // ==================== 屏幕摘要 ====================

    /**
     * 获取屏幕摘要 - 所有可交互节点信息
     * 供 AI 阅读以了解屏幕内容
     */
    fun getScreenSummary(): String {
        val rootNode = rootInActiveWindow ?: return "无法获取屏幕内容"
        
        return buildString {
            appendLine("=== 屏幕摘要 ===")
            appendLine("包名: ${rootNode.packageName}")
            appendLine()
            
            // 获取窗口信息
            val windows = windows
            if (windows.isNotEmpty()) {
                appendLine("--- 窗口列表 ---")
                windows.forEach { window ->
                    val title = window.title?.toString() ?: "无标题"
                    appendLine("窗口: $title")
                }
                appendLine()
            }
            
            // 递归遍历节点树
            appendLine("--- 可交互节点 ---")
            traverseInteractiveNodes(rootNode, this, 0)
        }.also { rootNode.recycle() }
    }

    private fun traverseInteractiveNodes(
        node: AccessibilityNodeInfo,
        builder: StringBuilder,
        depth: Int
    ) {
        val indent = "  ".repeat(depth)
        val isImportant = node.isImportantForAccessibility
        
        // 收集节点信息
        val nodeInfo = mutableListOf<String>()
        
        node.text?.takeIf { it.isNotEmpty() }?.let {
            nodeInfo.add("文字: $it")
        }
        
        node.contentDescription?.takeIf { it.isNotEmpty() }?.let {
            nodeInfo.add("描述: $it")
        }
        
        node.hintText?.takeIf { it.isNotEmpty() }?.let {
            nodeInfo.add("提示: $it")
        }
        
        node.viewIdResourceName?.takeIf { it.isNotEmpty() }?.let {
            nodeInfo.add("ID: $it")
        }
        
        // 获取边界
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        nodeInfo.add("位置: [${bounds.left},${bounds.top}]-[${bounds.right},${bounds.bottom}]")
        
        // 可交互属性
        val actions = mutableListOf<String>()
        if (node.isClickable) actions.add("可点击")
        if (node.isLongClickable) actions.add("可长按")
        if (node.isScrollable) actions.add("可滚动")
        if (node.isFocusable) actions.add("可聚焦")
        if (node.isEditable) actions.add("可输入")
        
        // 如果有信息或有交互能力，输出
        if (nodeInfo.isNotEmpty() || actions.isNotEmpty()) {
            builder.append(indent)
            builder.append("[${node.className?.toString()?.substringAfterLast('.') ?: "View"}]")
            
            if (nodeInfo.isNotEmpty()) {
                builder.append(" ")
                builder.append(nodeInfo.joinToString(", "))
            }
            
            if (actions.isNotEmpty()) {
                builder.append(" (")
                builder.append(actions.joinToString(", "))
                builder.append(")")
            }
            
            builder.appendLine()
        }
        
        // 递归处理子节点
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { child ->
                traverseInteractiveNodes(child, builder, depth + 1)
                child.recycle()
            }
        }
    }

    // ==================== 辅助方法 ====================

    private fun findNodeByText(
        root: AccessibilityNodeInfo,
        text: String,
        exactMatch: Boolean
    ): AccessibilityNodeInfo? {
        // 检查当前节点
        val currentText = root.text?.toString() ?: ""
        val contentDesc = root.contentDescription?.toString() ?: ""
        
        val found = if (exactMatch) {
            currentText == text || contentDesc == text
        } else {
            currentText.contains(text, ignoreCase = true) ||
            contentDesc.contains(text, ignoreCase = true)
        }
        
        if (found && root.isClickable) {
            return AccessibilityNodeInfo.obtain(root)
        }
        
        // 递归搜索子节点
        for (i in 0 until root.childCount) {
            root.getChild(i)?.let { child ->
                val result = findNodeByText(child, text, exactMatch)
                child.recycle()
                if (result != null) {
                    return result
                }
            }
        }
        
        return null
    }

    private fun findNodeByViewId(root: AccessibilityNodeInfo, id: String): AccessibilityNodeInfo? {
        // 检查当前节点
        root.viewIdResourceName?.takeIf { it.endsWith("/$id") }?.let {
            if (root.isClickable) {
                return AccessibilityNodeInfo.obtain(root)
            }
        }
        
        // 递归搜索子节点
        for (i in 0 until root.childCount) {
            root.getChild(i)?.let { child ->
                val result = findNodeByViewId(child, id)
                child.recycle()
                if (result != null) {
                    return result
                }
            }
        }
        
        return null
    }

    private fun findInputNodeByHint(root: AccessibilityNodeInfo, hint: String): AccessibilityNodeInfo? {
        // 检查当前节点
        val hintText = root.hintText?.toString() ?: ""
        if (hintText.contains(hint, ignoreCase = true) && root.isEditable) {
            return AccessibilityNodeInfo.obtain(root)
        }
        
        // 递归搜索子节点
        for (i in 0 until root.childCount) {
            root.getChild(i)?.let { child ->
                val result = findInputNodeByHint(child, hint)
                child.recycle()
                if (result != null) {
                    return result
                }
            }
        }
        
        return null
    }
}

