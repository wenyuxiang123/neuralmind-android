package com.neuralmind.ui.screens.memory

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.neuralmind.domain.model.MemoryLayer
import com.neuralmind.ui.viewmodel.MemoryViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoryScreen(
    viewModel: MemoryViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    val memories by viewModel.memories.collectAsState()
    val activeLayers by viewModel.activeMemoryLayers.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("九层记忆系统") },
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
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                MemoryLayer.values().forEach { layer ->
                    item {
                        MemoryLayerCard(
                            layer = layer,
                            isActive = activeLayers.contains(layer),
                            memoryCount = memories.filter { it.layer == layer && it.isActive }.size,
                            onToggle = { viewModel.toggleLayer(layer) }
                        )
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "最近记忆",
                        style = MaterialTheme.typography.titleMedium
                    )
                }

                items(memories.take(10), key = { it.id }) { memory ->
                    MemoryItem(memory = memory)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoryLayerCard(
    layer: MemoryLayer,
    isActive: Boolean,
    memoryCount: Int,
    onToggle: () -> Unit
) {
    val layerColors = listOf(
        Color(0xFFE3F2FD),
        Color(0xFFBBDEFB),
        Color(0xFF90CAF9),
        Color(0xFF64B5F6),
        Color(0xFF42A5F5),
        Color(0xFF2196F3),
        Color(0xFF1E88E5),
        Color(0xFF1976D2),
        Color(0xFF1565C0)
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onToggle,
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) {
                layerColors[layer.level - 1]
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        border = if (isActive) null else null
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "层级 ${layer.level}",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        layer.description,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "$memoryCount 条",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        "容量 ${layer.capacity}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (isActive) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = "已激活",
                            tint = Color(0xFF2E7D32),
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            "已激活",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF2E7D32)
                        )
                    }
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            Icons.Default.Pause,
                            contentDescription = "未激活",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            "未激活",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MemoryItem(memory: com.neuralmind.domain.model.Memory) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors()
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "层级 ${memory.layer.level}",
                    style = MaterialTheme.typography.labelSmall
                )
                Text(
                    "重要度 ${memory.importance}",
                    style = MaterialTheme.typography.labelSmall
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                memory.content,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
