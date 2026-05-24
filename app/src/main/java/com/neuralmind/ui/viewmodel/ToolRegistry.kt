package com.neuralmind.ui.viewmodel

enum class ToolCategory {
    HIGH,    // 高频工具，始终包含
    MEDIUM,  // 中频工具，按需包含
    LOW      // 低频工具，仅必要时包含
}

data class ToolInfo(
    val name: String,
    val description: String,
    val category: ToolCategory,
    val keywords: List<String> = emptyList()
)

object ToolRegistry {
    
    private val allTools = listOf(
        ToolInfo(
            name = "launch_app",
            description = "打开应用，参数：应用名称",
            category = ToolCategory.HIGH,
            keywords = listOf("打开", "启动", "运行", "开启", "app", "应用", "软件")
        ),
        ToolInfo(
            name = "click_text",
            description = "点击文字，参数：屏幕上的文字",
            category = ToolCategory.HIGH,
            keywords = listOf("点击", "按下", "触摸", "选择", "点", "tap")
        ),
        ToolInfo(
            name = "input_text",
            description = "输入文字，参数：输入框名称|文字内容",
            category = ToolCategory.MEDIUM,
            keywords = listOf("输入", "打字", "填写", "输入文字", "搜索", "type")
        ),
        ToolInfo(
            name = "go_back",
            description = "返回上一页（无参数）",
            category = ToolCategory.HIGH,
            keywords = listOf("返回", "后退", "回退", "back")
        ),
        ToolInfo(
            name = "go_home",
            description = "返回主页（无参数）",
            category = ToolCategory.HIGH,
            keywords = listOf("主页", "首页", "桌面", "home")
        ),
        ToolInfo(
            name = "swipe_up",
            description = "向上滑动（无参数）",
            category = ToolCategory.LOW,
            keywords = listOf("上滑", "向上滑", "往上滑", "往上", "向上", "up")
        ),
        ToolInfo(
            name = "swipe_down",
            description = "向下滑动（无参数）",
            category = ToolCategory.LOW,
            keywords = listOf("下滑", "向下滑", "往下滑", "往下", "向下", "down")
        ),
        ToolInfo(
            name = "swipe_left",
            description = "向左滑动（无参数）",
            category = ToolCategory.LOW,
            keywords = listOf("左滑", "向左滑", "往左滑", "往左", "向左", "left")
        ),
        ToolInfo(
            name = "swipe_right",
            description = "向右滑动（无参数）",
            category = ToolCategory.LOW,
            keywords = listOf("右滑", "向右滑", "往右滑", "往右", "向右", "right")
        ),
        ToolInfo(
            name = "get_screen",
            description = "获取屏幕内容（无参数）",
            category = ToolCategory.MEDIUM,
            keywords = listOf("截图", "屏幕", "当前界面", "查看", "screen")
        ),
        ToolInfo(
            name = "analyze_screen",
            description = "AI分析屏幕内容，参数：要分析的任务（如\"查找登录按钮\"、\"描述当前界面\"等）",
            category = ToolCategory.MEDIUM,
            keywords = listOf("分析屏幕", "AI看屏幕", "识别界面", "理解屏幕", "看看屏幕", "屏幕分析", "analyze", "vision")
        ),
        ToolInfo(
            name = "describe_screen",
            description = "AI详细描述当前屏幕内容（无参数）",
            category = ToolCategory.MEDIUM,
            keywords = listOf("描述屏幕", "这是什么", "这是什么界面", "描述界面", "describe")
        ),
        ToolInfo(
            name = "recognize_text",
            description = "AI识别屏幕上的所有文字（无参数）",
            category = ToolCategory.MEDIUM,
            keywords = listOf("识别文字", "OCR", "读取文字", "扫描文字", "recognize", "text")
        ),
        ToolInfo(
            name = "find_on_screen",
            description = "AI在屏幕上查找指定物体或元素，参数：物体名称",
            category = ToolCategory.MEDIUM,
            keywords = listOf("查找", "找找", "寻找", "find", "search")
        ),
        ToolInfo(
            name = "open_url",
            description = "打开网页URL，参数：网址或搜索关键词",
            category = ToolCategory.MEDIUM,
            keywords = listOf("浏览器", "网页", "搜索", "打开网址", "浏览", "google", "百度", "网址", "url", "网站", "搜索内容", "search", "web")
        )
    )
    
    fun selectToolsForUserInput(userInput: String): List<ToolInfo> {
        val input = userInput.lowercase()
        
        // 高频工具始终包含
        val highPriorityTools = allTools.filter { it.category == ToolCategory.HIGH }
        
        // 中频工具：根据关键词匹配
        val mediumPriorityTools = allTools.filter { tool ->
            tool.category == ToolCategory.MEDIUM && 
            tool.keywords.any { keyword -> input.contains(keyword.lowercase()) }
        }
        
        // 低频工具：根据关键词匹配
        val lowPriorityTools = allTools.filter { tool ->
            tool.category == ToolCategory.LOW && 
            tool.keywords.any { keyword -> input.contains(keyword.lowercase()) }
        }
        
        // 组合所有选择的工具
        val selectedTools = highPriorityTools + mediumPriorityTools + lowPriorityTools
        
        // 如果没有任何匹配，至少包含高频工具
        return if (selectedTools.isEmpty()) {
            highPriorityTools
        } else {
            selectedTools
        }
    }
    
    fun buildToolsList(tools: List<ToolInfo>): String {
        return tools.mapIndexed { index, tool ->
            "${index + 1}. ${tool.name} - ${tool.description}"
        }.joinToString("\n")
    }
}
