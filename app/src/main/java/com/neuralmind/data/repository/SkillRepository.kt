package com.neuralmind.data.repository

import com.neuralmind.data.local.db.dao.SkillDao
import com.neuralmind.data.local.db.entity.SkillEntity
import com.neuralmind.domain.model.Skill
import com.neuralmind.domain.model.SkillCategory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class SkillRepository @Inject constructor(
    private val skillDao: SkillDao
) {
    fun getAllSkills(): Flow<List<Skill>> {
        return skillDao.getAllSkills().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    fun getInstalledSkills(): Flow<List<Skill>> {
        return skillDao.getInstalledSkills().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    suspend fun insertDefaultSkills() {
        val defaultSkills = listOf(
            SkillEntity(
                id = "calculator",
                name = "计算器",
                description = "进行数学计算",
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
                description = "查询当前天气和预报",
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
                description = "多语言翻译",
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
                description = "倒计时和定时器",
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
