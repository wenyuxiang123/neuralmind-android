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
    
    fun selectCategory(category: SkillCategory) {
        _selectedCategory.value = category
    }
    
    fun toggleInstalledFilter() {
        _showInstalledOnly.value = !_showInstalledOnly.value
    }
    
    fun installSkill(skillId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                skillRepository.installSkill(skillId)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
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
                _uiState.update { it.copy(error = e.message) }
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }
    
    fun activateSkill(skillId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                skillRepository.activateSkill(skillId)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }
    
    fun deactivateSkill(skillId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                skillRepository.deactivateSkill(skillId)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }
    
    fun refreshSkills() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                skillRepository.insertDefaultSkills()
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }
    
    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}

data class SkillUiState(
    val isLoading: Boolean = false,
    val error: String? = null
)
