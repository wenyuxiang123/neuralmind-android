package com.neuralmind.ui.screens.skills

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.neuralmind.domain.model.Skill
import com.neuralmind.domain.model.SkillCategory
import com.neuralmind.ui.theme.*
import com.neuralmind.ui.viewmodel.SkillViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SkillsScreen(viewModel: SkillViewModel = hiltViewModel(), onNavigateBack: () -> Unit = {}) {
    val skills by viewModel.skills.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val isLoading = uiState.isLoading
    val showInstalledOnly by viewModel.showInstalledOnly.collectAsState()
    var selectedTabIndex by remember { mutableIntStateOf(0) }

    Column(modifier = Modifier.fillMaxSize().background(brush = Brush.verticalGradient(colors = listOf(BackgroundPrimary, Color(0xFF0A1628))))) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(text = "技能库", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = GradientStart)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = "发现并管理AI技能扩展", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        }

        val tabs = listOf("全部", "效率", "创意", "学习", "工具", "生活")
        ScrollableTabRow(selectedTabIndex = selectedTabIndex, containerColor = Color.Transparent, contentColor = TextPrimary, edgePadding = 16.dp, divider = {}) {
            tabs.forEachIndexed { index, title ->
                val isSelected = selectedTabIndex == index
                Tab(selected = isSelected, onClick = { selectedTabIndex = index; viewModel.selectCategoryByIndex(index) },
                    modifier = Modifier.padding(horizontal = 4.dp).clip(RoundedCornerShape(20.dp))
                        .then(if (isSelected) Modifier.background(brush = Brush.horizontalGradient(listOf(GradientStart, GradientEnd))) else Modifier.background(CardBackground))) {
                    Text(title, color = if (isSelected) Color.White else TextSecondary, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal)
                }
            }
        }

        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.End) {
            FilterChip(selected = showInstalledOnly, onClick = { viewModel.toggleInstalledFilter() }, label = { Text("仅显示已安装") },
                leadingIcon = { Icon(if (showInstalledOnly) Icons.Default.FilterAlt else Icons.Default.FilterAltOff, contentDescription = null, modifier = Modifier.size(18.dp)) },
                colors = FilterChipDefaults.filterChipColors(containerColor = CardBackground, labelColor = TextSecondary, selectedContainerColor = GradientStart, selectedLabelColor = Color.White, selectedLeadingIconColor = Color.White))
        }

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = GradientStart) }
        } else {
            val filteredSkills = skills.filter { skill ->
                val categoryMatch = when (selectedTabIndex) { 0 -> true; 1 -> skill.category == SkillCategory.PRODUCTIVITY; 2 -> skill.category == SkillCategory.CREATIVE; 3 -> skill.category == SkillCategory.LEARNING; 4 -> skill.category == SkillCategory.UTILITY; 5 -> skill.category == SkillCategory.LIFESTYLE; else -> true }
                categoryMatch && (!showInstalledOnly || skill.isInstalled)
            }

            if (filteredSkills.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.SearchOff, contentDescription = null, modifier = Modifier.size(64.dp), tint = TextTertiary)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(if (showInstalledOnly) "暂无已安装的技能" else "暂无相关技能", style = MaterialTheme.typography.bodyLarge, color = TextSecondary)
                    }
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(filteredSkills, key = { it.id }) { skill ->
                        DarkSkillCard(skill = skill, onInstall = { viewModel.installSkill(skill.id) }, onUninstall = { viewModel.uninstallSkill(skill.id) }, onActivate = { viewModel.activateSkill(skill.id) }, onDeactivate = { viewModel.deactivateSkill(skill.id) })
                    }
                }
            }
        }
    }
}

@Composable
fun DarkSkillCard(skill: Skill, onInstall: () -> Unit, onUninstall: () -> Unit, onActivate: () -> Unit, onDeactivate: () -> Unit) {
    val categoryColor = getCategoryColor(skill.category)
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = if (skill.isActive) categoryColor.copy(alpha = 0.15f) else CardBackground), shape = RoundedCornerShape(16.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = RoundedCornerShape(12.dp), color = categoryColor.copy(alpha = 0.15f), modifier = Modifier.size(48.dp)) {
                        Box(contentAlignment = Alignment.Center) { Icon(imageVector = getSkillIcon(skill.icon), contentDescription = null, tint = categoryColor, modifier = Modifier.size(24.dp)) }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = skill.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                            if (skill.isActive) { Spacer(modifier = Modifier.width(8.dp)); Surface(shape = RoundedCornerShape(4.dp), color = GradientStart) { Text("已启用", style = MaterialTheme.typography.labelSmall, color = Color.White, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)) } }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = skill.description, style = MaterialTheme.typography.bodyMedium, color = TextSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            if (skill.scenarios.isNotBlank()) { Spacer(modifier = Modifier.height(8.dp)); Text(text = "适用: ${skill.scenarios}", style = MaterialTheme.typography.bodySmall, color = TextTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = "作者: ${skill.author} · v${skill.version}", style = MaterialTheme.typography.labelSmall, color = TextTertiary.copy(alpha = 0.7f))
            Spacer(modifier = Modifier.height(12.dp))
            DarkSkillActionButtons(skill = skill, onInstall = onInstall, onUninstall = onUninstall, onActivate = onActivate, onDeactivate = onDeactivate)
        }
    }
}

@Composable
fun DarkSkillActionButtons(skill: Skill, onInstall: () -> Unit, onUninstall: () -> Unit, onActivate: () -> Unit, onDeactivate: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        when {
            !skill.isAvailable -> OutlinedButton(onClick = {}, enabled = false, modifier = Modifier.weight(1f), colors = ButtonDefaults.outlinedButtonColors(disabledContentColor = TextTertiary)) { Icon(Icons.Default.HourglassTop, contentDescription = null); Spacer(modifier = Modifier.width(4.dp)); Text("即将推出") }
            !skill.isInstalled -> Button(onClick = onInstall, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = GradientStart), shape = RoundedCornerShape(8.dp)) { Icon(Icons.Default.Download, contentDescription = null); Spacer(modifier = Modifier.width(4.dp)); Text("安装") }
            skill.isInstalled && !skill.isActive -> { Button(onClick = onActivate, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = GradientStart), shape = RoundedCornerShape(8.dp)) { Icon(Icons.Default.CheckCircle, contentDescription = null); Spacer(modifier = Modifier.width(4.dp)); Text("启用") }; OutlinedButton(onClick = onUninstall, modifier = Modifier.weight(1f), colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusOffline)), shape = RoundedCornerShape(8.dp)) { Icon(Icons.Default.Delete, contentDescription = null); Spacer(modifier = Modifier.width(4.dp)); Text("卸载") } }
            skill.isActive -> { Button(onClick = {}, enabled = false, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(disabledContainerColor = GradientStart, disabledContentColor = Color.White), shape = RoundedCornerShape(8.dp)) { Icon(Icons.Default.CheckCircle, contentDescription = null); Spacer(modifier = Modifier.width(4.dp)); Text("已启用") }; OutlinedButton(onClick = onDeactivate, modifier = Modifier.weight(1f), colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary),  shape = RoundedCornerShape(8.dp)) { Icon(Icons.Default.PauseCircle, contentDescription = null); Spacer(modifier = Modifier.width(4.dp)); Text("停用") } }
        }
    }
}

fun getCategoryColor(category: SkillCategory): Color = when (category) { SkillCategory.PRODUCTIVITY -> SkillCategoryProductivity; SkillCategory.UTILITY -> SkillCategoryUtility; SkillCategory.CREATIVE -> SkillCategoryCreative; SkillCategory.LEARNING -> SkillCategoryLearning; SkillCategory.LIFESTYLE -> SkillCategoryLifestyle }
fun getSkillIcon(iconName: String): ImageVector = when (iconName) { "edit_note" -> Icons.Default.Edit; "compress" -> Icons.Default.Compress; "translate" -> Icons.Default.Translate; "email" -> Icons.Default.Email; "event_note" -> Icons.Default.EventNote; "auto_stories" -> Icons.Default.AutoStories; "create" -> Icons.Default.Create; "lightbulb" -> Icons.Default.Lightbulb; "campaign" -> Icons.Default.Campaign; "school" -> Icons.Default.School; "style" -> Icons.Default.Style; "quiz" -> Icons.Default.Quiz; "question_answer" -> Icons.Default.QuestionAnswer; "calculate" -> Icons.Default.Calculate; "code" -> Icons.Default.Code; "data_object" -> Icons.Default.DataObject; "restaurant" -> Icons.Default.Restaurant; "flight" -> Icons.Default.Flight; "fitness_center" -> Icons.Default.FitnessCenter; "schedule" -> Icons.Default.Schedule; else -> Icons.Default.Widgets }
