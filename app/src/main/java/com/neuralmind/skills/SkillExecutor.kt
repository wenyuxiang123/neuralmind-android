package com.neuralmind.skills

import android.content.Context
import com.neuralmind.domain.model.Skill
import com.neuralmind.domain.model.SkillCategory
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SkillExecutor @Inject constructor(
    @ApplicationContext private val context: Context
) {
    
    suspend fun executeSkill(skill: Skill, params: Map<String, String>): String {
        return when (skill.id) {
            "calculator" -> executeCalculator(params)
            "weather" -> executeWeather(params)
            "translator" -> executeTranslator(params)
            "timer" -> executeTimer(params)
            "notes" -> executeNotes(params)
            else -> "技能 ${skill.name} 尚未实现"
        }
    }
    
    private fun executeCalculator(params: Map<String, String>): String {
        val expression = params["expression"] ?: return "请输入计算表达式"
        return try {
            val result = evaluateExpression(expression)
            "计算结果: $expression = $result"
        } catch (e: Exception) {
            "计算错误: ${e.message}"
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
        val city = params["city"] ?: "当前城市"
        val temp = (15..28).random()
        val conditions = listOf("晴朗", "多云", "小雨", "阴天")
        val condition = conditions.random()
        return "🏙️ $city 天气预报:\n" +
               "📅 日期: ${java.text.SimpleDateFormat("yyyy年MM月dd日", java.util.Locale.getDefault()).format(java.util.Date())}\n" +
               "🌡️ 温度: ${temp}°C\n" +
               "🌤️ 天气: $condition\n" +
               "💨 风速: ${(5..20).random()} km/h\n" +
               "💧 湿度: ${(40..80).random()}%"
    }
    
    private fun executeTranslator(params: Map<String, String>): String {
        val text = params["text"] ?: return "请输入要翻译的文本"
        val targetLang = params["target"] ?: "中文"
        return "🔄 翻译结果:\n" +
               "📝 原文: $text\n" +
               "🌍 目标语言: $targetLang\n" +
               "📄 译文: [模拟翻译结果] $text"
    }
    
    private fun executeTimer(params: Map<String, String>): String {
        val action = params["action"] ?: "start"
        val seconds = params["seconds"]?.toIntOrNull() ?: 60
        return when (action) {
            "start" -> "⏱️ 计时器已启动: $seconds 秒\n" +
                      "预计结束时间: ${java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(System.currentTimeMillis() + seconds * 1000))}"
            "stop" -> "⏹️ 计时器已停止"
            else -> "计时器操作: $action"
        }
    }
    
    private fun executeNotes(params: Map<String, String>): String {
        val action = params["action"] ?: "list"
        val note = params["note"]
        return when (action) {
            "add" -> "📝 备忘录已添加: \"$note\""
            "list" -> "📋 备忘录列表:\n" +
                     "1. [示例] 完成项目开发\n" +
                     "2. [示例] 学习新知识\n" +
                     "3. [示例] 锻炼身体"
            else -> "备忘录操作: $action"
        }
    }
}
