package com.neuralmind.data.repository

import com.neuralmind.data.local.db.dao.SkillDao
import com.neuralmind.data.local.db.entity.SkillEntity
import com.neuralmind.domain.model.Skill
import com.neuralmind.domain.model.SkillCategory
import com.neuralmind.skills.SkillExecutor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SkillRepository @Inject constructor(
    private val skillDao: SkillDao,
    private val skillExecutor: SkillExecutor
) {
    fun getAllSkills(): Flow<List<Skill>> {
        return skillDao.getAllSkills().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    fun getSkillsByCategory(category: SkillCategory): Flow<List<Skill>> {
        return getAllSkills().map { skills ->
            skills.filter { it.category == category }
        }
    }

    fun getInstalledSkills(): Flow<List<Skill>> {
        return skillDao.getInstalledSkills().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    suspend fun getSkillById(id: String): Skill? {
        return skillDao.getSkillById(id)?.toDomain()
    }

    suspend fun installSkill(id: String) {
        val skill = skillDao.getSkillById(id) ?: return
        val updated = skill.copy(isInstalled = true)
        skillDao.update(updated)
    }

    suspend fun uninstallSkill(id: String) {
        val skill = skillDao.getSkillById(id) ?: return
        val updated = skill.copy(isInstalled = false)
        skillDao.update(updated)
    }

    suspend fun executeSkill(id: String, params: Map<String, String>): String {
        val skill = getSkillById(id) ?: return "技能不存在"
        if (!skill.isInstalled) return "技能未安装，请先安装"
        return skillExecutor.executeSkill(skill, params)
    }

    suspend fun insertDefaultSkills() {
        if (skillDao.getSkillById("calculator") != null) return

        val defaultSkills = listOf(
            SkillEntity(
                id = "calculator",
                name = "计算器",
                description = "进行数学计算，支持加减乘除等运算",
                icon = "calculate",
                category = SkillCategory.UTILITY.name,
                version = "1.0",
                author = "NeuralMind",
                permissions = "[]",
                isInstalled = true,
                isBuiltIn = true,
                downloadUrl = null,
                installedSize = 0L
            ),
            SkillEntity(
                id = "weather",
                name = "天气查询",
                description = "查询当前天气和预报信息",
                icon = "weather",
                category = SkillCategory.UTILITY.name,
                version = "1.0",
                author = "NeuralMind",
                permissions = "[]",
                isInstalled = true,
                isBuiltIn = true,
                downloadUrl = null,
                installedSize = 0L
            ),
            SkillEntity(
                id = "translator",
                name = "翻译",
                description = "多语言翻译，支持中英互译",
                icon = "translate",
                category = SkillCategory.UTILITY.name,
                version = "1.0",
                author = "NeuralMind",
                permissions = "[]",
                isInstalled = false,
                isBuiltIn = true,
                downloadUrl = null,
                installedSize = 0L
            ),
            SkillEntity(
                id = "timer",
                name = "计时器",
                description = "倒计时和定时器功能",
                icon = "timer",
                category = SkillCategory.UTILITY.name,
                version = "1.0",
                author = "NeuralMind",
                permissions = "[]",
                isInstalled = true,
                isBuiltIn = true,
                downloadUrl = null,
                installedSize = 0L
            ),
            SkillEntity(
                id = "notes",
                name = "备忘录",
                description = "记录笔记和待办事项",
                icon = "note",
                category = SkillCategory.PRODUCTIVITY.name,
                version = "1.0",
                author = "NeuralMind",
                permissions = "[]",
                isInstalled = true,
                isBuiltIn = true,
                downloadUrl = null,
                installedSize = 0L
            ),
            SkillEntity(
                id = "file-manager",
                name = "文件管理",
                description = "浏览和管理本地文件",
                icon = "folder",
                category = SkillCategory.PRODUCTIVITY.name,
                version = "1.0",
                author = "NeuralMind",
                permissions = "[\"storage\"]",
                isInstalled = false,
                isBuiltIn = true,
                downloadUrl = null,
                installedSize = 0L
            ),
            SkillEntity(
                id = "web-search",
                name = "网络搜索",
                description = "快速搜索网络信息",
                icon = "search",
                category = SkillCategory.UTILITY.name,
                version = "1.0",
                author = "NeuralMind",
                permissions = "[\"internet\"]",
                isInstalled = false,
                isBuiltIn = true,
                downloadUrl = null,
                installedSize = 0L
            ),
            SkillEntity(
                id = "alarm",
                name = "闹钟",
                description = "设置和管理闹钟",
                icon = "alarm",
                category = SkillCategory.PRODUCTIVITY.name,
                version = "1.0",
                author = "NeuralMind",
                permissions = "[\"alarm\"]",
                isInstalled = false,
                isBuiltIn = true,
                downloadUrl = null,
                installedSize = 0L
            ),
            SkillEntity(
                id = "system",
                name = "系统工具",
                description = "查看和管理系统信息",
                icon = "settings",
                category = SkillCategory.SYSTEM.name,
                version = "1.0",
                author = "NeuralMind",
                permissions = "[]",
                isInstalled = true,
                isBuiltIn = true,
                downloadUrl = null,
                installedSize = 0L
            ),
            SkillEntity(
                id = "lifestyle",
                name = "生活助手",
                description = "提供日常生活建议",
                icon = "heart",
                category = SkillCategory.LIFESTYLE.name,
                version = "1.0",
                author = "NeuralMind",
                permissions = "[]",
                isInstalled = false,
                isBuiltIn = true,
                downloadUrl = null,
                installedSize = 0L
            )
        )
        defaultSkills.forEach { skillDao.insert(it) }
    }

    private fun SkillEntity.toDomain(): Skill {
        return Skill(
            id = id,
            name = name,
            description = description,
            icon = icon,
            category = SkillCategory.valueOf(category),
            version = version,
            author = author,
            permissions = listOf(),
            isInstalled = isInstalled,
            isBuiltIn = isBuiltIn,
            downloadUrl = downloadUrl,
            installedSize = installedSize
        )
    }
}
