package com.neuralmind.domain.usecase.skill

import com.neuralmind.data.repository.SkillRepository
import com.neuralmind.domain.model.Skill
import com.neuralmind.domain.model.SkillCategory
import javax.inject.Inject

class GetAllSkillsUseCase @Inject constructor(
    private val repository: SkillRepository
) {
    operator fun invoke() = repository.getAllSkills()
}

class GetSkillsByCategoryUseCase @Inject constructor(
    private val repository: SkillRepository
) {
    operator fun invoke(category: SkillCategory) = repository.getSkillsByCategory(category)
}

class InstallSkillUseCase @Inject constructor(
    private val repository: SkillRepository
) {
    suspend operator fun invoke(skillId: String) = repository.installSkill(skillId)
}

class ExecuteSkillUseCase @Inject constructor(
    private val repository: SkillRepository
) {
    suspend operator fun invoke(skillId: String, params: Map<String, String>): String = 
        repository.executeSkill(skillId, params)
}
