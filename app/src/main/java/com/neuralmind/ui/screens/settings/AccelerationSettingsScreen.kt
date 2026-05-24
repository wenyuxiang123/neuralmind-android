package com.neuralmind.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.neuralmind.llama.HardwareAccelerationManager
import com.neuralmind.llama.HardwareAccelerationManager.AccelerationType
import com.neuralmind.llama.HardwareAccelerationManager.AccelerationInfo
import dagger.hilt.android.EntryPointAccessors

@Composable
fun AccelerationSettingsScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val hiltEntryPoint = EntryPointAccessors.fromApplication(
        context.applicationContext,
        HardwareAccelerationManagerEntryPoint::class.java
    )
    val accelerationManager = hiltEntryPoint.hardwareAccelerationManager
    
    val accelerators by remember { mutableStateOf(accelerationManager.getAvailableAccelerators()) }
    val selectedAccelerator by remember { mutableStateOf(accelerationManager.getSelectedAccelerator()) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("硬件加速设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp)
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                Icons.Default.Cpu,
                                contentDescription = "当前加速",
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(
                                    text = "当前使用",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = accelerationManager.getSelectedAcceleratorInfo().name,
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
            
            item {
                Text(
                    text = "可用的加速选项",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
            
            items(accelerators.size) { index ->
                val accelerator = accelerators[index]
                AccelerationCard(
                    accelerator = accelerator,
                    isSelected = accelerator.type == selectedAccelerator,
                    onSelect = {
                        accelerationManager.selectAccelerator(accelerator.type)
                    }
                )
                if (index < accelerators.size - 1) {
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
            
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Info,
                                contentDescription = "提示",
                                modifier = Modifier.size(24.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "提示",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "• 选择 AUTO 模式会自动检测并使用最佳加速方案",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = "• 更改设置后需要重新加载模型才能生效",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = "• NPU 加速需要设备支持并安装相应驱动",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AccelerationCard(
    accelerator: AccelerationInfo,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isSelected) 8.dp else 2.dp
        ),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        onClick = onSelect
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = when (accelerator.type) {
                    AccelerationType.CPU -> Icons.Default.Memory
                    AccelerationType.GPU_OPENCL -> Icons.Default.GraphicEq
                    AccelerationType.GPU_VULKAN -> Icons.Default.Layers
                    AccelerationType.NPU_HEXAGON -> Icons.Default.Brain
                    AccelerationType.NPU_NNAPI -> Icons.Default.Cell
                    AccelerationType.AUTO -> Icons.Default.AutoAwesome
                },
                contentDescription = accelerator.name,
                modifier = Modifier.size(40.dp),
                tint = if (isSelected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.primary
                }
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = accelerator.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (accelerator.recommended) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Chip(
                            onClick = {},
                            modifier = Modifier.height(24.dp)
                        ) {
                            Text(
                                text = "推荐",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = accelerator.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
            if (isSelected) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = "已选择",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
@Preview
fun AccelerationSettingsScreenPreview() {
    MaterialTheme {
        AccelerationSettingsScreen(onBack = {})
    }
}

@dagger.hilt.EntryPoint
@dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
interface HardwareAccelerationManagerEntryPoint {
    fun hardwareAccelerationManager(): HardwareAccelerationManager
}