package com.neuralmind.ui.screens.models

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
import com.neuralmind.domain.model.AIModel
import com.neuralmind.domain.model.ModelCategory
import com.neuralmind.ui.theme.*
import com.neuralmind.ui.viewmodel.ModelViewModel

@Composable
fun ModelLibraryScreen(
    viewModel: ModelViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    val allModels by viewModel.allModels.collectAsState()
    val installedModels by viewModel.installedModels.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    var selectedCategory by remember { mutableStateOf(ModelCategory.MOBILE) }
    var searchQuery by remember { mutableStateOf("") }

    val categories = listOf(
        ModelCategory.MOBILE to "手机优化",
        ModelCategory.TEXT to "文本",
        ModelCategory.CODE to "代码",
        ModelCategory.VISION to "视觉",
        ModelCategory.AUDIO to "音频"
    )

    Column(
        modifier = Modifier.fillMaxSize().background(brush = Brush.verticalGradient(colors = listOf(BackgroundPrimary, Color(0xFF0A1628))))
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(text = "模型库", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = GradientStart)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = "浏览和管理本地可用的AI模型", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        }

        OutlinedTextField(
            value = searchQuery, onValueChange = { searchQuery = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            placeholder = { Text("搜索模型...", color = TextTertiary) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextSecondary) },
            trailingIcon = { if (searchQuery.isNotEmpty()) IconButton(onClick = { searchQuery = "" }) { Icon(Icons.Default.Clear, contentDescription = "清除", tint = TextSecondary) } },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = GradientStart, unfocusedBorderColor = CardBorder, cursorColor = GradientStart,
                focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary,
                focusedContainerColor = BackgroundTertiary, unfocusedContainerColor = BackgroundTertiary
            ),
            shape = RoundedCornerShape(12.dp), singleLine = true
        )

        Spacer(modifier = Modifier.height(16.dp))

        ScrollableTabRow(
            selectedTabIndex = categories.indexOfFirst { it.first == selectedCategory },
            containerColor = Color.Transparent, contentColor = TextPrimary, edgePadding = 16.dp, divider = {}
        ) {
            categories.forEach { (category, label) ->
                val isSelected = selectedCategory == category
                Tab(
                    selected = isSelected, onClick = { selectedCategory = category },
                    modifier = Modifier.padding(horizontal = 4.dp).clip(RoundedCornerShape(20.dp))
                        .then(if (isSelected) Modifier.background(brush = Brush.horizontalGradient(listOf(GradientStart, GradientEnd))) else Modifier.background(CardBackground))
                ) {
                    Text(text = label, color = if (isSelected) Color.White else TextSecondary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal)
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        val filteredModels = allModels.filter { it.category == selectedCategory }
            .filter { searchQuery.isEmpty() || it.name.contains(searchQuery, ignoreCase = true) || it.description.contains(searchQuery, ignoreCase = true) }

        if (filteredModels.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Storage, contentDescription = null, modifier = Modifier.size(64.dp), tint = TextTertiary)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("暂无模型", style = MaterialTheme.typography.bodyLarge, color = TextSecondary)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(filteredModels, key = { it.id }) { model ->
                    DarkModelCard(
                        model = model, isInstalled = model.isInstalled,
                        onDownload = { viewModel.downloadModel(model.id) },
                        onDelete = { viewModel.deleteModel(model.id) },
                        onSelect = { viewModel.selectModel(model) },
                        downloadProgress = model.downloadProgress, isDownloading = model.isDownloading
                    )
                }
            }
        }
    }
}

@Composable
fun DarkModelCard(
    model: AIModel, isInstalled: Boolean,
    onDownload: () -> Unit, onDelete: () -> Unit, onSelect: () -> Unit,
    downloadProgress: Float, isDownloading: Boolean
) {
    val categoryColor = when (model.category) {
        ModelCategory.MOBILE -> ModelCategoryMobile
        ModelCategory.TEXT -> GradientEnd
        ModelCategory.CODE -> ModelCategoryCode
        ModelCategory.VISION -> ModelCategoryVision
        ModelCategory.AUDIO -> ModelCategoryAudio
    }

    Card(
        modifier = Modifier.fillMaxWidth().clickable(enabled = isInstalled) { onSelect() },
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column {
            Box(modifier = Modifier.fillMaxWidth().height(4.dp).background(brush = Brush.horizontalGradient(colors = listOf(categoryColor, categoryColor.copy(alpha = 0.6f)))))
            Column(modifier = Modifier.padding(16.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(model.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = TextPrimary)
                            if (model.category == ModelCategory.MOBILE) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(shape = RoundedCornerShape(4.dp), color = StatusOnline) {
                                    Text("手机端", modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, color = Color.White)
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(model.description, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                    }
                    if (isInstalled) { Icon(Icons.Default.CheckCircle, contentDescription = "已安装", tint = StatusOnline) }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Memory, contentDescription = null, tint = TextTertiary, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("${model.parameters}B", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Storage, contentDescription = null, tint = TextTertiary, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("${(model.size / (1024 * 1024))} MB", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        repeat(5) { index -> Icon(if (index < 4) Icons.Default.Star else Icons.Default.StarBorder, contentDescription = null, tint = StatusWarning, modifier = Modifier.size(16.dp)) }
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("4.2", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Download, contentDescription = null, tint = TextTertiary, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("1.2k", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    if (isInstalled) {
                        OutlinedButton(onClick = onDelete, colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusOffline), borderColor = StatusOffline.copy(alpha = 0.5f)) { Icon(Icons.Default.Delete, contentDescription = null); Spacer(modifier = Modifier.width(4.dp)); Text("删除") }
                    } else if (isDownloading) {
                        OutlinedButton(onClick = {}, enabled = false, colors = ButtonDefaults.outlinedButtonColors(disabledContentColor = TextSecondary)) { CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = GradientStart); Spacer(modifier = Modifier.width(8.dp)); Text("${(downloadProgress * 100).toInt()}%") }
                    } else {
                        Button(onClick = onDownload, colors = ButtonDefaults.buttonColors(containerColor = GradientStart), shape = RoundedCornerShape(8.dp)) { Icon(Icons.Default.Download, contentDescription = null); Spacer(modifier = Modifier.width(4.dp)); Text("下载") }
                    }
                }
            }
        }
    }
}
