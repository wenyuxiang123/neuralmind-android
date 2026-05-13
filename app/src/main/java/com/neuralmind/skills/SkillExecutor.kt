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

    // ========== 已实现的技能 ==========

    private fun executeCalculator(params: Map<String, String>): String {
        val expression = params["expression"] ?: ""
        
        if (expression.isEmpty()) {
            return "📱 计算器\n\n请输入计算表达式，例如：\n- "25+36"\n- "100*45"\n- "1000/25"\n\n我会帮您计算！"
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

    private fun executeSystem(params: Map<String, String>): String {
        return "⚙️ 系统信息\n\n" +
                "📱 设备信息:\n" +
                "- 系统: Android ${android.os.Build.VERSION.RELEASE}\n" +
                "- 设备: ${android.os.Build.MODEL}\n" +
                "- 品牌: ${android.os.Build.BRAND}\n" +
                "- SDK: ${android.os.Build.VERSION.SDK_INT}\n" +
                "- 主板: ${android.os.Build.BOARD}\n" +
                "- 硬件: ${android.os.Build.HARDWARE}"
    }

    // ========== 开发中的技能 ==========

    private fun executeWeather(params: Map<String, String>): String {
        return "🚧 天气功能 正在开发中\n\n" +
                "该功能尚未实现，敬请期待后续更新！\n\n" +
                "💡 提示: 您可以在设置中添加您的位置信息，功能上线后将自动获取当地天气。"
    }

    private fun executeTranslator(params: Map<String, String>): String {
        return "🚧 翻译功能 正在开发中\n\n" +
                "该功能尚未实现，敬请期待后续更新！\n\n" +
                "💡 提示: 未来将支持多语言实时翻译功能。"
    }

    private fun executeTimer(params: Map<String, String>): String {
        return "🚧 计时器功能 正在开发中\n\n" +
                "该功能尚未实现，敬请期待后续更新！\n\n" +
                "💡 提示: 未来将支持倒计时、秒表等多种计时功能。"
    }

    private fun executeNotes(params: Map<String, String>): String {
        return "🚧 备忘录功能 正在开发中\n\n" +
                "该功能尚未实现，敬请期待后续更新！\n\n" +
                "💡 提示: 未来将支持笔记创建、分类管理和搜索功能。"
    }

    private fun executeFileManager(params: Map<String, String>): String {
        return "🚧 文件管理器功能 正在开发中\n\n" +
                "该功能尚未实现，敬请期待后续更新！\n\n" +
                "💡 提示: 未来将支持文件浏览、复制、移动和删除操作。"
    }

    private fun executeWebSearch(params: Map<String, String>): String {
        return "🚧 网络搜索功能 正在开发中\n\n" +
                "该功能尚未实现，敬请期待后续更新！\n\n" +
                "💡 提示: 未来将支持实时网络搜索和信息查询。"
    }

    private fun executeAlarm(params: Map<String, String>): String {
        return "🚧 闹钟功能 正在开发中\n\n" +
                "该功能尚未实现，敬请期待后续更新！\n\n" +
                "💡 提示: 未来将支持多闹钟设置和提醒功能。"
    }

    private fun executeLifestyle(params: Map<String, String>): String {
        return "🚧 生活助手功能 正在开发中\n\n" +
                "该功能尚未实现，敬请期待后续更新！\n\n" +
                "💡 提示: 未来将支持日程管理、健康追踪等生活服务。"
    }

    // ========== 工具方法 ==========

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
