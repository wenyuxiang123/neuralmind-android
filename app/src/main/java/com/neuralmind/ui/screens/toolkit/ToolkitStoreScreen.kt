package com.neuralmind.ui.screens.toolkit

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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.neuralmind.domain.model.ToolCategory
import com.neuralmind.domain.model.ToolModule
import com.neuralmind.ui.viewmodel.ToolkitViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolkitStoreScreen(
    viewModel: ToolkitViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    val tools by viewModel.tools.collectAsState()
    val installedTools by viewModel.installedTools.collectAsState()
    var selectedCategory by remember { mutableStateOf<ToolCategory?>(null) }

    val categories = listOf(
        ToolCategory.EDITOR to "编辑器",
        ToolCategory.TERMINAL to "终端",
        ToolCategory.GIT to "Git",
        ToolCategory.DATABASE to "数据库",
        ToolCategory.API_TESTER to "API 测试",
        ToolCategory.FILE_MANAGER to "文件管理",
        ToolCategory.NETWORK to "网络工具",
        ToolCategory.PERFORMANCE to "性能监控",
        ToolCategory.LOG_VIEWER to "日志查看"
    )

    val filteredTools = if (selectedCategory == null) {
        tools
    } else {
        tools.filter { it.category == selectedCategory }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("工具包") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            ScrollableTabRow(
                selectedTabIndex = if (selectedCategory == null) 0 else
                    categories.indexOfFirst { it.first == selectedCategory } + 1
            ) {
                Tab(
                    selected = selectedCategory == null,
                    onClick = { selectedCategory = null },
                    text = { Text("全部") }
                )
                categories.forEach { (category, label) ->
                    Tab(
                        selected = selectedCategory == category,
                        onClick = { selectedCategory = category },
                        text = { Text(label) }
                    )
                }
            }

            if (filteredTools.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Build,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "暂无工具",
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filteredTools, key = { it.id }) { tool ->
                        ToolCard(
                            tool = tool,
                            isInstalled = tool.isInstalled,
                            onDownload = { viewModel.downloadTool(tool.id) },
                            onDelete = { viewModel.deleteTool(tool.id) },
                            onLaunch = { viewModel.launchTool(tool) },
                            downloadProgress = tool.downloadProgress,
                            isDownloading = tool.isDownloading
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolCard(
    tool: ToolModule,
    isInstalled: Boolean,
    onDownload: () -> Unit,
    onDelete: () -> Unit,
    onLaunch: () -> Unit,
    downloadProgress: Float,
    isDownloading: Boolean
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = isInstalled) { onLaunch() },
        colors = CardDefaults.cardColors(
            containerColor = if (isInstalled) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        tool.name,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        tool.description,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                if (isInstalled) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = "已安装",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Text(
                        tool.category.name,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (isInstalled) {
                        OutlinedButton(onClick = onDelete) {
                            Icon(Icons.Default.Delete, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("卸载")
                        }
                        Button(onClick = onLaunch) {
                            Icon(Icons.Default.OpenInNew, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("启动")
                        }
                    } else if (isDownloading) {
                        OutlinedButton(onClick = {}, enabled = false) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("${(downloadProgress * 100).toInt()}%")
                        }
                    } else {
                        Button(onClick = onDownload) {
                            Icon(Icons.Default.Download, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("下载")
                        }
                    }
                }
            }
        }
    }
}
