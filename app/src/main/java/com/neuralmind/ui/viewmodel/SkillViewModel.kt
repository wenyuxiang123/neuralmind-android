package com.neuralmind.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neuralmind.core.Logger
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
    
    val activeSkills = skillRepository.getActiveSkills()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    
    private val _selectedCategory = MutableStateFlow(SkillCategory.PRODUCTIVITY)
    val selectedCategory: StateFlow<SkillCategory> = _selectedCategory.asStateFlow()
    
    private val _showInstalledOnly = MutableStateFlow(false)
    val showInstalledOnly: StateFlow<Boolean> = _showInstalledOnly.asStateFlow()
    
    init {
        viewModelScope.launch {
            skillRepository.insertDefaultSkills()
        }
    }
    
    /**
     * Select category by tab index (0=全部, 1=效率, 2=创意, 3=学习, 4=工具, 5=生活)
     */
    fun selectCategoryByIndex(index: Int) {
        Logger.d(Logger.Tags.VM, "selectCategoryByIndex(index=$index)")
        _selectedCategory.value = when (index) {
            0 -> SkillCategory.PRODUCTIVITY // 全部 - use PRODUCTIVITY as placeholder for "all"
            1 -> SkillCategory.PRODUCTIVITY
            2 -> SkillCategory.CREATIVE
            3 -> SkillCategory.LEARNING
            4 -> SkillCategory.UTILITY
            5 -> SkillCategory.LIFESTYLE
            else -> SkillCategory.PRODUCTIVITY
        }
    }
    
    fun selectCategory(category: SkillCategory) {
        Logger.d(Logger.Tags.VM, "selectCategory(category=${category.name})")
        _selectedCategory.value = category
    }
    
    fun toggleInstalledFilter() {
        Logger.d(Logger.Tags.VM, "toggleInstalledFilter(current=${_showInstalledOnly.value})")
        _showInstalledOnly.value = !_showInstalledOnly.value
    }
    
    fun installSkill(skillId: String) {
        Logger.d(Logger.Tags.VM, "installSkill(skillId=$skillId)")
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                skillRepository.installSkill(skillId)
                Logger.i(Logger.Tags.VM, "installSkill success: $skillId")
            } catch (e: Exception) {
                Logger.e(Logger.Tags.VM, "installSkill failed: $skillId", e)
                _uiState.update { it.copy(error = e.message) }
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }
    
    fun uninstallSkill(skillId: String) {
        Logger.d(Logger.Tags.VM, "uninstallSkill(skillId=$skillId)")
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                skillRepository.uninstallSkill(skillId)
                Logger.i(Logger.Tags.VM, "uninstallSkill success: $skillId")
            } catch (e: Exception) {
                Logger.e(Logger.Tags.VM, "uninstallSkill failed: $skillId", e)
                _uiState.update { it.copy(error = e.message) }
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }
    
    fun activateSkill(skillId: String) {
        Logger.d(Logger.Tags.VM, "activateSkill(skillId=$skillId)")
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                skillRepository.activateSkill(skillId)
                Logger.i(Logger.Tags.VM, "activateSkill success: $skillId")
            } catch (e: Exception) {
                Logger.e(Logger.Tags.VM, "activateSkill failed: $skillId", e)
                _uiState.update { it.copy(error = e.message) }
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }
    
    fun deactivateSkill(skillId: String) {
        Logger.d(Logger.Tags.VM, "deactivateSkill(skillId=$skillId)")
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                skillRepository.deactivateSkill(skillId)
                Logger.i(Logger.Tags.VM, "deactivateSkill success: $skillId")
            } catch (e: Exception) {
                Logger.e(Logger.Tags.VM, "deactivateSkill failed: $skillId", e)
                _uiState.update { it.copy(error = e.message) }
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }
    
    fun refreshSkills() {
        Logger.d(Logger.Tags.VM, "refreshSkills()")
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                skillRepository.insertDefaultSkills()
                Logger.i(Logger.Tags.VM, "refreshSkills completed")
            } catch (e: Exception) {
                Logger.e(Logger.Tags.VM, "refreshSkills failed", e)
                _uiState.update { it.copy(error = e.message) }
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }
    
    fun clearError() {
        Logger.d(Logger.Tags.VM, "clearError")
        _uiState.update { it.copy(error = null) }
    }
}

data class SkillUiState(
    val isLoading: Boolean = false,
    val error: String? = null
)
