package com.neuralmind.skills

import com.neuralmind.data.repository.SkillRepository
import dagger.hilt.android.scopes.ViewModelScoped
import javax.inject.Inject

@ViewModelScoped
class SkillCallManager @Inject constructor(
    private val skillRepository: SkillRepository,
    private val skillExecutor: SkillExecutor
) {
    
    private val skillKeywords = mapOf(
        "calculator" to listOf("计算", "计算器", "算一下", "多少", "+", "-", "*", "/", "等于"),
        "weather" to listOf("天气", "气温", "下雨", "晴天", "多云", "温度"),
        "translator" to listOf("翻译", "英文", "中文", "英语", "译成"),
        "timer" to listOf("计时", "计时器", "倒计时", "闹钟"),
        "notes" to listOf("笔记", "备忘", "记录", "记住", "写下"),
        "file_manager" to listOf("文件", "文件夹", "打开", "查看"),
        "web_search" to listOf("搜索", "查一下", "网络搜索", "百度"),
        "alarm" to listOf("闹钟", "提醒", "定时"),
        "system" to listOf("系统", "设置", "配置"),
        "lifestyle" to listOf("生活", "建议", "今天", "安排")
    )
    
    fun detectSkillsFromInput(input: String): List<String> {
        val detectedSkills = mutableListOf<String>()
        
        for ((skillId, keywords) in skillKeywords) {
            if (keywords.any { keyword -> input.contains(keyword, ignoreCase = true) }) {
                detectedSkills.add(skillId)
            }
        }
        
        return detectedSkills
    }
    
    suspend fun callSkill(skillId: String, params: Map<String, String>): SkillCallResult {
        val skill = skillRepository.getSkillById(skillId)
            ?: return SkillCallResult(
                success = false,
                errorMessage = "技能不存在: $skillId"
            )
        
        if (!skill.isInstalled) {
            return SkillCallResult(
                success = false,
                errorMessage = "技能未安装: ${skill.name}"
            )
        }
        
        return try {
            val result = skillExecutor.executeSkill(skill, params)
            SkillCallResult(
                success = true,
                skillId = skillId,
                skillName = skill.name,
                result = result
            )
        } catch (e: Exception) {
            SkillCallResult(
                success = false,
                errorMessage = "技能执行失败: ${e.message}"
            )
        }
    }
    
    suspend fun callDetectedSkills(input: String): List<SkillCallResult> {
        val detectedSkills = detectSkillsFromInput(input)
        return detectedSkills.map { skillId ->
            val params = extractParams(input, skillId)
            callSkill(skillId, params)
        }
    }
    
    private fun extractParams(input: String, skillId: String): Map<String, String> {
        return when (skillId) {
            "calculator" -> {
                val expression = input.filter { it.isDigit() || "+-*/()".contains(it) }
                mapOf("expression" to expression)
            }
            "weather" -> {
                val cityMatch = input.findCityName()
                mapOf("city" to (cityMatch ?: "当前城市"))
            }
            "timer" -> {
                val seconds = input.extractNumber() ?: 60
                mapOf("seconds" to seconds.toString())
            }
            "notes" -> {
                mapOf("action" to "add", "note" to input)
            }
            else -> emptyMap()
        }
    }
    
    fun getAvailableSkills() = skillKeywords.keys.toList()
}

data class SkillCallResult(
    val success: Boolean,
    val skillId: String = "",
    val skillName: String = "",
    val result: String = "",
    val errorMessage: String? = null
)

private fun String.findCityName(): String? {
    val cities = listOf("北京", "上海", "广州", "深圳", "杭州", "成都", "重庆", "武汉", "南京", "西安")
    return cities.find { this.contains(it) }
}

private fun String.extractNumber(): Int? {
    return Regex("\\d+").find(this)?.value?.toIntOrNull()
}
