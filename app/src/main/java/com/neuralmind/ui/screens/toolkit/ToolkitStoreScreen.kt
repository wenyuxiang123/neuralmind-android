package com.neuralmind.ui.screens.toolkit

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.neuralmind.domain.model.ToolCategory
import com.neuralmind.domain.model.ToolModule
import com.neuralmind.ui.theme.*
import com.neuralmind.ui.viewmodel.ToolkitViewModel

@Composable
fun ToolkitStoreScreen(viewModel: ToolkitViewModel = hiltViewModel(), onNavigateBack: () -> Unit) {
    val tools by viewModel.tools.collectAsState()
    val installedTools by viewModel.installedTools.collectAsState()
    var selectedCategory by remember { mutableStateOf<ToolCategory?>(null) }
    val categories = listOf(ToolCategory.EDITOR to "编辑器", ToolCategory.TERMINAL to "终端", ToolCategory.GIT to "Git", ToolCategory.DATABASE to "数据库", ToolCategory.API_TESTER to "API测试", ToolCategory.FILE_MANAGER to "文件管理", ToolCategory.NETWORK to "网络工具", ToolCategory.PERFORMANCE to "性能监控", ToolCategory.LOG_VIEWER to "日志查看")
    val filteredTools = if (selectedCategory == null) tools else tools.filter { it.category == selectedCategory }

    Column(modifier = Modifier.fillMaxSize().background(brush = Brush.verticalGradient(colors = listOf(BackgroundPrimary, Color(0xFF0A1628))))) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) { Text(text = "工具包", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = GradientStart); Spacer(modifier = Modifier.height(4.dp)); Text(text = "开发者工具集", style = MaterialTheme.typography.bodyMedium, color = TextSecondary) }
        ScrollableTabRow(selectedTabIndex = if (selectedCategory == null) 0 else categories.indexOfFirst { it.first == selectedCategory } + 1, containerColor = Color.Transparent, contentColor = TextPrimary, edgePadding = 16.dp, divider = {}) {
            Tab(selected = selectedCategory == null, onClick = { selectedCategory = null }, modifier = Modifier.padding(horizontal = 4.dp).clip(RoundedCornerShape(20.dp)).then(if (selectedCategory == null) Modifier.background(brush = Brush.horizontalGradient(listOf(GradientStart, GradientEnd))) else Modifier.background(CardBackground))) { Text("全部", color = if (selectedCategory == null) Color.White else TextSecondary, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), fontWeight = if (selectedCategory == null) FontWeight.SemiBold else FontWeight.Normal) }
            categories.forEach { (category, label) ->
                val isSelected = selectedCategory == category
                Tab(selected = isSelected, onClick = { selectedCategory = category }, modifier = Modifier.padding(horizontal = 4.dp).clip(RoundedCornerShape(20.dp)).then(if (isSelected) Modifier.background(brush = Brush.horizontalGradient(listOf(GradientStart, GradientEnd))) else Modifier.background(CardBackground))) { Text(label, color = if (isSelected) Color.White else TextSecondary, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1) }
            }
        }
        if (filteredTools.isEmpty()) { Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Default.Build, contentDescription = null, modifier = Modifier.size(64.dp), tint = TextTertiary); Spacer(modifier = Modifier.height(16.dp)); Text("暂无工具", style = MaterialTheme.typography.bodyLarge, color = TextSecondary) } } }
        else { LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { items(filteredTools, key = { it.id }) { tool -> DarkToolCard(tool = tool, isInstalled = tool.isInstalled, onDownload = { viewModel.downloadTool(tool.id) }, onDelete = { viewModel.deleteTool(tool.id) }, onLaunch = { viewModel.launchTool(tool) }, downloadProgress = tool.downloadProgress, isDownloading = tool.isDownloading) } } }
    }
}

@Composable
fun DarkToolCard(tool: ToolModule, isInstalled: Boolean, onDownload: () -> Unit, onDelete: () -> Unit, onLaunch: () -> Unit, downloadProgress: Float, isDownloading: Boolean) {
    Card(modifier = Modifier.fillMaxWidth().clickable(enabled = isInstalled) { onLaunch() }, colors = CardDefaults.cardColors(containerColor = CardBackground), shape = RoundedCornerShape(16.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) { Text(tool.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = TextPrimary); if (isInstalled) { Spacer(modifier = Modifier.width(8.dp)); Icon(Icons.Default.CheckCircle, contentDescription = "已安装", tint = StatusOnline, modifier = Modifier.size(18.dp)) } }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(tool.description, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(8.dp), color = GradientEnd.copy(alpha = 0.2f)) { Text(tool.category.name, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall, color = GradientEnd) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (isInstalled) {
                        OutlinedButton(onClick = onDelete, colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusOffline)), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)) { Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp)); Spacer(modifier = Modifier.width(4.dp)); Text("卸载", style = MaterialTheme.typography.labelMedium) }
                        Button(onClick = onLaunch, colors = ButtonDefaults.buttonColors(containerColor = GradientStart), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)) { Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp)); Spacer(modifier = Modifier.width(4.dp)); Text("启动", style = MaterialTheme.typography.labelMedium) }
                    } else if (isDownloading) {
                        OutlinedButton(onClick = {}, enabled = false, colors = ButtonDefaults.outlinedButtonColors(disabledContentColor = TextSecondary), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)) { CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = GradientStart); Spacer(modifier = Modifier.width(8.dp)); Text("${(downloadProgress * 100).toInt()}%", style = MaterialTheme.typography.labelMedium) }
                    } else {
                        Button(onClick = onDownload, colors = ButtonDefaults.buttonColors(containerColor = GradientStart), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) { Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp)); Spacer(modifier = Modifier.width(4.dp)); Text("下载", style = MaterialTheme.typography.labelMedium) }
                    }
                }
            }
        }
    }
}
