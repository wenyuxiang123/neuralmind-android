package com.neuralmind.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neuralmind.data.repository.SkillRepository
import com.neuralmind.domain.model.Skill
import com.neuralmind.domain.model.SkillCategory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SkillViewModel @Inject constructor(
    private val skillRepository: SkillRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SkillUiState())
    val uiState: StateFlow<SkillUiState> = _uiState.asStateFlow()

    val skills = skillRepository.getAllSkills()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val installedSkills = skillRepository.getInstalledSkills()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            skillRepository.insertDefaultSkills()
        }
    }

    fun installSkill(skillId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                skillRepository.installSkill(skillId)
            } catch (e: Exception) {
                // 处理错误
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun uninstallSkill(skillId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                skillRepository.uninstallSkill(skillId)
            } catch (e: Exception) {
                // 处理错误
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun launchSkill(skill: Skill) {
        viewModelScope.launch {
            try {
                skillRepository.executeSkill(skill.id, emptyMap())
            } catch (e: Exception) {
                // 处理错误
            }
        }
    }
}

data class SkillUiState(
    val isLoading: Boolean = false
)
