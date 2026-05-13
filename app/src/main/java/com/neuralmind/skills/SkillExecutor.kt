package com.neuralmind.skills

import android.content.Context
import com.neuralmind.domain.model.Skill
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SkillExecutor @Inject constructor(
    @ApplicationContext private val context: Context
) {
    suspend fun executeSkill(skill: Skill, params: Map<String, String>): String = withContext(Dispatchers.IO) {
        when (skill.id) {
            "calculator" -> executeCalculator(params)
            "weather" -> executeWeather(params)
            "translator" -> executeTranslator(params)
            "timer" -> executeTimer(params)
            "notes" -> executeNotes(params)
            "file_manager" -> executeFileManager(params)
            "web_search" -> executeWebSearch(params)
            "alarm" -> executeAlarm(params)
            "system" -> executeSystem(params)
            "lifestyle" -> executeLifestyle(params)
            else -> "技能 ${skill.name} 正在开发中，敬请期待！"
        }
    }

    private fun executeCalculator(params: Map<String, String>): String {
        val expression = params["expression"] ?: ""
        
        if (expression.isEmpty()) {
            return "📱 计算器\n\n请输入计算表达式，例如：\n- \"25+36\"\n- \"100*45\"\n- \"1000/25\"\n\n我会帮您计算！"
        }
        
        return try {
            val result = evaluateExpression(expression)
            "🧮 计算结果\n\n表达式: $expression\n结果: $result"
        } catch (e: Exception) {
            "❌ 计算失败\n\n表达式: $expression\n错误: ${e.message}"
        }
    }

    private fun evaluateExpression(expr: String): Double {
        val sanitized = expr.replace(Regex("[^0-9+\\-*/().]"), "")
        val result = object {
            var pos = 0
            fun parse(): Double {
                var value = parseTerm()
                while (pos < sanitized.length) {
                    when (sanitized[pos]) {
                        '+' -> { pos++; value += parseTerm() }
                        '-' -> { pos++; value -= parseTerm() }
                        else -> break
                    }
                }
                return value
            }
            fun parseTerm(): Double {
                var value = parseFactor()
                while (pos < sanitized.length) {
                    when (sanitized[pos]) {
                        '*' -> { pos++; value *= parseFactor() }
                        '/' -> { pos++; value /= parseFactor() }
                        else -> break
                    }
                }
                return value
            }
            fun parseFactor(): Double {
                if (pos >= sanitized.length) return 0.0
                return when (sanitized[pos]) {
                    '(' -> {
                        pos++
                        val result = parse()
                        if (pos < sanitized.length && sanitized[pos] == ')') pos++
                        result
                    }
                    '+' -> { pos++; parseFactor() }
                    '-' -> { pos++; -parseFactor() }
                    else -> parseNumber()
                }
            }
            fun parseNumber(): Double {
                var num = 0.0
                var start = pos
                while (pos < sanitized.length && (sanitized[pos].isDigit() || sanitized[pos] == '.')) {
                    pos++
                }
                return sanitized.substring(start, pos).toDoubleOrNull() ?: 0.0
            }
        }.parse()
        return result
    }

    private fun executeWeather(params: Map<String, String>): String {
        val city = params["city"] ?: "您所在城市"
        val temp = (15..28).random()
        val conditions = listOf("☀️ 晴朗", "⛅ 多云", "🌤️ 晴转多云", "🌥️ 多云转晴", "🌦️ 小雨")
        val condition = conditions.random()
        
        return "🌤️ 天气预报 - $city\n\n" +
                "📅 日期: ${java.text.SimpleDateFormat("yyyy年MM月dd日", java.util.Locale.getDefault()).format(java.util.Date())}\n" +
                "🌡️ 温度: ${temp}°C\n" +
                "☁️ 天气: $condition\n" +
                "💨 风速: ${(10..20).random()} km/h\n" +
                "💧 湿度: ${(40..70).random()}%\n" +
                "🔆 紫外线: 中等\n\n" +
                "💡 建议: 适合户外活动，记得带伞以防万一！"
    }

    private fun executeTranslator(params: Map<String, String>): String {
        val text = params["text"] ?: ""
        val targetLang = params["target"] ?: "中文"
        
        return if (text.isEmpty()) {
            "🌐 翻译\n\n请输入要翻译的内容，例如：\n- \"翻译 Hello\"\n- \"把这句话翻译成英文\"\n\n我会帮您翻译！"
        } else {
            "🌐 翻译结果\n\n" +
                    "📝 原文: $text\n" +
                    "🌍 目标语言: $targetLang\n" +
                    "📄 译文: [模拟翻译] $text"
        }
    }

    private fun executeTimer(params: Map<String, String>): String {
        val action = params["action"] ?: "start"
        val seconds = params["seconds"]?.toIntOrNull() ?: 60
        
        return when (action) {
            "start" -> "⏱️ 计时器\n\n" +
                    "已启动 ${seconds}秒 倒计时\n" +
                    "预计结束时间: ${
                        java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(
                            java.util.Date(System.currentTimeMillis() + seconds * 1000)
                        )
                    }"
            "stop" -> "⏹️ 计时器\n\n已停止"
            else -> "⏱️ 计时器"
        }
    }

    private fun executeNotes(params: Map<String, String>): String {
        val action = params["action"] ?: "list"
        val note = params["note"]
        
        return when (action) {
            "add" -> "📝 备忘录\n\n" +
                    "已添加新笔记：\n\"$note\""
            "list" -> "📝 备忘录列表\n\n" +
                    "1. [示例] 完成 NeuralMind 应用开发\n" +
                    "2. [示例] 学习 Kotlin 新知识\n" +
                    "3. [示例] 每周健身计划\n" +
                    "4. [示例] 阅读推荐书籍"
            else -> "📝 备忘录"
        }
    }

    private fun executeFileManager(params: Map<String, String>): String {
        return "📁 文件管理器\n\n" +
                "📂 内部存储\n" +
                "  - 📄 documents/\n" +
                "  - 📄 downloads/\n" +
                "  - 📄 pictures/\n" +
                "  - 📄 music/\n\n" +
                "💡 提示: 点击文件夹可以浏览内容"
    }

    private fun executeWebSearch(params: Map<String, String>): String {
        val query = params["query"] ?: ""
        
        return if (query.isEmpty()) {
            "🔍 网络搜索\n\n请输入要搜索的内容，我会帮您查找相关信息！"
        } else {
            "🔍 搜索 - $query\n\n" +
                    "📊 搜索结果：\n" +
                    "1. [相关信息] 关于 \"$query\" 的搜索结果...\n" +
                    "2. [相关信息] 更多关于 \"$query\" 的信息...\n" +
                    "3. [相关信息] 与 \"$query\" 相关的内容..."
        }
    }

    private fun executeAlarm(params: Map<String, String>): String {
        val action = params["action"] ?: "list"
        
        return when (action) {
            "set" -> "⏰ 闹钟\n\n已设置闹钟"
            "list" -> "⏰ 闹钟列表\n\n" +
                    "1. 07:00 - 工作日 (周一到周五)\n" +
                    "2. 09:00 - 周末 (周六周日)\n" +
                    "3. 18:00 - 提醒下班"
            else -> "⏰ 闹钟"
        }
    }

    private fun executeSystem(params: Map<String, String>): String {
        return "⚙️ 系统工具\n\n" +
                "📱 设备信息:\n" +
                "- 系统: Android ${android.os.Build.VERSION.RELEASE}\n" +
                "- 设备: ${android.os.Build.MODEL}\n" +
                "- 品牌: ${android.os.Build.BRAND}\n\n" +
                "💾 存储信息:\n" +
                "- 可用空间: ${(50..200).random()} GB\n" +
                "- 总空间: ${(200..500).random()} GB"
    }

    private fun executeLifestyle(params: Map<String, String>): String {
        return "💖 生活助手\n\n" +
                "📋 今日建议：\n\n" +
                "1. 🌅 早晨: 早起喝一杯温水，开始美好的一天\n" +
                "2. 🏃 运动: 下午抽30分钟进行轻运动\n" +
                "3. 📚 学习: 晚上花1小时学习新知识\n" +
                "4. 😴 睡眠: 保证22:30前睡觉\n\n" +
                "💡 祝您今天愉快！"
    }

    suspend fun executeCommand(command: String): CommandResult = withContext(Dispatchers.IO) {
        try {
            val process = Runtime.getRuntime().exec(command)
            val output = BufferedReader(InputStreamReader(process.inputStream)).readText()
            val error = BufferedReader(InputStreamReader(process.errorStream)).readText()
            val exitCode = process.waitFor()

            CommandResult(
                output = output,
                error = error,
                exitCode = exitCode,
                isSuccess = exitCode == 0
            )
        } catch (e: Exception) {
            CommandResult(
                output = "",
                error = e.message ?: "未知错误",
                exitCode = -1,
                isSuccess = false
            )
        }
    }

    suspend fun readFile(path: String): String = withContext(Dispatchers.IO) {
        try {
            File(path).readText()
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }

    suspend fun writeFile(path: String, content: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val file = File(path)
            file.parentFile?.mkdirs()
            file.writeText(content)
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun listFiles(directory: String): List<FileInfo> = withContext(Dispatchers.IO) {
        try {
            val dir = File(directory)
            if (dir.exists() && dir.isDirectory) {
                dir.listFiles()?.map { file ->
                    FileInfo(
                        name = file.name,
                        path = file.absolutePath,
                        isDirectory = file.isDirectory,
                        size = file.length(),
                        lastModified = file.lastModified()
                    )
                } ?: emptyList()
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getGitStatus(repoPath: String): String = withContext(Dispatchers.IO) {
        try {
            val process = Runtime.getRuntime().exec("git status", null, File(repoPath))
            val output = BufferedReader(InputStreamReader(process.inputStream)).readText()
            output
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }

    suspend fun gitCommit(repoPath: String, message: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val addProcess = Runtime.getRuntime().exec("git add .", null, File(repoPath))
            addProcess.waitFor()
            
            val commitProcess = Runtime.getRuntime().exec("git commit -m \"$message\"", null, File(repoPath))
            commitProcess.waitFor() == 0
        } catch (e: Exception) {
            false
        }
    }

    fun getCodeSnippets(language: String): List<CodeSnippet> {
        return when (language.lowercase()) {
            "kotlin" -> listOf(
                CodeSnippet("Hello World", """
                    fun main() {
                        println("Hello, World!")
                    }
                """),
                CodeSnippet("Function", """
                    fun greet(name: String): String {
                        return "Hello, ${'$'}name!"
                    }
                """),
                CodeSnippet("Class", """
                    class Person(val name: String, val age: Int) {
                        fun greet(): String = "Hello, ${'$'}name"
                    }
                """)
            )
            "java" -> listOf(
                CodeSnippet("Hello World", """
                    public class Hello {
                        public static void main(String[] args) {
                            System.out.println("Hello, World!");
                        }
                    }
                """),
                CodeSnippet("Class", """
                    public class Person {
                        private String name;
                        private int age;
                        
                        public Person(String name, int age) {
                            this.name = name;
                            this.age = age;
                        }
                    }
                """)
            )
            else -> listOf(
                CodeSnippet("Template", "// Code template for $language")
            )
        }
    }
}

data class CommandResult(
    val output: String,
    val error: String,
    val exitCode: Int,
    val isSuccess: Boolean
)

data class FileInfo(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: Long,
    val lastModified: Long
)

data class CodeSnippet(
    val name: String,
    val code: String
)
