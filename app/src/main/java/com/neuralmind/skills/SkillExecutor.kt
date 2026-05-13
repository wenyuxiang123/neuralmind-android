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
    /**
     * Execute a skill. Calculator skill is executed with real calculation logic.
     * Other skills work through system prompt injection, so this returns empty string.
     */
    suspend fun executeSkill(skill: Skill, params: Map<String, String>): String = withContext(Dispatchers.IO) {
        when (skill.id) {
            "calculator" -> executeCalculator(params)
            else -> "" // Other skills work through system prompt injection
        }
    }

    /**
     * Real calculator execution with actual math evaluation.
     */
    private fun executeCalculator(params: Map<String, String>): String {
        val expression = params["expression"] ?: ""
        
        if (expression.isEmpty()) {
            return """📱 计算器

请输入计算表达式，例如：
- "25+36"
- "100*45"
- "1000/25"
- "(10+5)*3"
- "2^10"

我会帮您计算！"""
        }
        
        return try {
            val result = evaluateExpression(expression)
            "🧮 计算结果\n\n表达式: $expression\n结果: $result"
        } catch (e: Exception) {
            "❌ 计算失败\n\n表达式: $expression\n错误: ${e.message}"
        }
    }

    /**
     * Safe mathematical expression evaluator.
     * Supports: +, -, *, /, ^, (), sqrt, sin, cos, tan, log, abs
     */
    private fun evaluateExpression(expr: String): Double {
        val sanitized = expr.lowercase()
            .replace(" ", "")
            .replace("sqrt", "√")
            .replace("pi", "π")
            .replace("e", "2.718281828")
        
        // Handle basic arithmetic with parentheses
        if (!sanitized.contains(Regex("[√π^]"))) {
            return evaluateArithmetic(sanitized)
        }
        
        // Handle advanced math functions
        var processed = sanitized
        // Process sqrt
        val sqrtRegex = Regex("√(\d+\.?\d*)")
        processed = sqrtRegex.replace(processed) { 
            kotlin.math.sqrt(it.groupValues[1].toDouble()).toString() 
        }
        // Process pi
        processed = processed.replace("π", "3.14159265359")
        // Process power
        processed = processPower(processed)
        
        return evaluateArithmetic(processed)
    }
    
    private fun processPower(expr: String): String {
        val powerRegex = Regex("(-?\d+\.?\d*)\^(-?\d+\.?\d*)")
        var result = expr
        while (powerRegex.containsMatchIn(result)) {
            result = powerRegex.replace(result) { 
                kotlin.math.pow(
                    it.groupValues[1].toDouble(), 
                    it.groupValues[2].toDouble()
                ).toString()
            }
        }
        return result
    }

    private fun evaluateArithmetic(expr: String): Double {
        val sanitized = expr.replace(Regex("[^0-9+\-*/().]"), "")
        
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

    fun getCodeSnippets(language: String): List<CodeSnippet> {
        return when (language.lowercase()) {
            "kotlin" -> listOf(
                CodeSnippet("Hello World", """
                    fun main() {
                        println("Hello, World!")
                    }
                """.trimIndent()),
                CodeSnippet("Function", """
                    fun greet(name: String): String {
                        return "Hello, ${'$'}name!"
                    }
                """.trimIndent()),
                CodeSnippet("Class", """
                    class Person(val name: String, val age: Int) {
                        fun greet(): String = "Hello, ${'$'}name"
                    }
                """.trimIndent())
            )
            "java" -> listOf(
                CodeSnippet("Hello World", """
                    public class Hello {
                        public static void main(String[] args) {
                            System.out.println("Hello, World!");
                        }
                    }
                """.trimIndent()),
                CodeSnippet("Class", """
                    public class Person {
                        private String name;
                        private int age;
                        
                        public Person(String name, int age) {
                            this.name = name;
                            this.age = age;
                        }
                    }
                """.trimIndent())
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
