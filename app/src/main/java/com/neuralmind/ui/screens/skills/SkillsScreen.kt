package com.neuralmind.ui.screens.skills

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.neuralmind.domain.model.Skill
import com.neuralmind.ui.viewmodel.SkillViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SkillsScreen(
    viewModel: SkillViewModel = hiltViewModel()
) {
    val skills by viewModel.skills.collectAsState()
    val executionResult by viewModel.executionResult.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    
    // 已实现的技能列表（可正常使用）
    val implementedSkills = listOf("calculator", "system")
    
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        TopAppBar(
            title = { Text("技能中心") },
            actions = {
                IconButton(onClick = { viewModel.refreshSkills() }) {
                    Icon(Icons.Default.Refresh, contentDescription = "刷新")
                }
            }
        )
        
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 开发中提示
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                "部分技能正在开发中，敬请期待！",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                    }
                }
                
                items(skills, key = { it.id }) { skill ->
                    val isImplemented = implementedSkills.contains(skill.id)
                    SkillCard(
                        skill = skill,
                        isInstalled = skill.isInstalled,
                        isImplemented = isImplemented,
                        onInstall = { viewModel.installSkill(skill.id) },
                        onUninstall = { viewModel.uninstallSkill(skill.id) },
                        onLaunch = { viewModel.launchSkill(skill) }
                    )
                }
            }
        }
        
        // 执行结果弹窗
        executionResult?.let { result ->
            AlertDialog(
                onDismissRequest = { viewModel.clearExecutionResult() },
                title = { Text("执行结果") },
                text = { Text(result) },
                confirmButton = {
                    TextButton(onClick = { viewModel.clearExecutionResult() }) {
                        Text("确定")
                    }
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SkillCard(
    skill: Skill,
    isInstalled: Boolean,
    isImplemented: Boolean,
    onInstall: () -> Unit,
    onUninstall: () -> Unit,
    onLaunch: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = isInstalled && isImplemented) { onLaunch() },
        colors = CardDefaults.cardColors()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = when (skill.category) {
                            "productivity" -> Icons.Default.Work
                            "lifestyle" -> Icons.Default.Lifestyle
                            "system" -> Icons.Default.Settings
                            else -> Icons.Default.Widgets
                        },
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = skill.name,
                                style = MaterialTheme.typography.titleMedium
                            )
                            if (!isImplemented) {
                                Spacer(modifier = Modifier.width(8.dp))
                                SuggestionChip(
                                    onClick = { },
                                    label = { Text("即将推出", style = MaterialTheme.typography.labelSmall) },
                                    colors = SuggestionChipDefaults.suggestionChipColors(
                                        containerColor = MaterialTheme.colorScheme.tertiaryContainer
                                    )
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = skill.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // 操作按钮区域
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (!isInstalled) {
                    Button(
                        onClick = onInstall,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("安装")
                    }
                } else {
                    OutlinedButton(
                        onClick = onUninstall,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("卸载")
                    }
                    
                    if (isImplemented) {
                        Button(
                            onClick = onLaunch,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("启动")
                        }
                    } else {
                        Button(
                            onClick = { },
                            enabled = false,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.HourglassTop, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("即将推出")
                        }
                    }
                }
            }
        }
    }
}
