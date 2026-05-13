package com.neuralmind.ui.screens.device

import android.Manifest
import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.neuralmind.ui.viewmodel.DeviceViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceControlScreen(
    viewModel: DeviceViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 标题
        Text(
            text = "设备控制",
            style = MaterialTheme.typography.headlineMedium
        )
        
        // 注意提示
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer
            )
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onTertiaryContainer
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "部分功能需要打开系统设置进行操作",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
        }
        
        // 快捷控制卡片
        QuickControlsCard(
            isWifiEnabled = uiState.isWifiEnabled,
            isBluetoothEnabled = uiState.isBluetoothEnabled,
            onOpenWifiSettings = { viewModel.openWifiSettings() },
            onOpenBluetoothSettings = { viewModel.openBluetoothSettings() }
        )
        
        // 音量和亮度卡片
        VolumeBrightnessCard(
            mediaVolume = uiState.mediaVolume,
            brightness = uiState.brightness,
            maxMediaVolume = uiState.maxMediaVolume,
            maxBrightness = 255,
            onMediaVolumeChanged = { viewModel.setMediaVolume(it) },
            onOpenSoundSettings = { viewModel.openSoundSettings() },
            onOpenDisplaySettings = { viewModel.openDisplaySettings() }
        )
        
        // 电池信息卡片
        BatteryInfoCard(
            batteryLevel = uiState.batteryLevel,
            isCharging = uiState.isCharging
        )
        
        // 预设场景卡片
        AutomationRulesCard()
    }
}

@Composable
fun QuickControlsCard(
    isWifiEnabled: Boolean,
    isBluetoothEnabled: Boolean,
    onOpenWifiSettings: () -> Unit,
    onOpenBluetoothSettings: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                "快捷控制",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SettingsButton(
                    icon = Icons.Default.Wifi,
                    label = "WiFi",
                    description = if (isWifiEnabled) "已开启" else "已关闭",
                    onClick = onOpenWifiSettings,
                    modifier = Modifier.weight(1f)
                )
                SettingsButton(
                    icon = Icons.Default.Bluetooth,
                    label = "蓝牙",
                    description = if (isBluetoothEnabled) "已开启" else "已关闭",
                    onClick = onOpenBluetoothSettings,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                icon,
                contentDescription = label,
                tint = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                label,
                style = MaterialTheme.typography.titleSmall
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "打开设置",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
fun VolumeBrightnessCard(
    mediaVolume: Int,
    brightness: Int,
    maxMediaVolume: Int,
    maxBrightness: Int,
    onMediaVolumeChanged: (Int) -> Unit,
    onOpenSoundSettings: () -> Unit,
    onOpenDisplaySettings: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                "音量和亮度",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(16.dp))

            // 音量行
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.VolumeUp, contentDescription = null)
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    "音量",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.width(50.dp)
                )
                Slider(
                    value = mediaVolume.toFloat(),
                    valueRange = 0f..maxMediaVolume.toFloat(),
                    onValueChange = { onMediaVolumeChanged(it.toInt()) },
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onOpenSoundSettings) {
                    Icon(
                        Icons.Default.Settings,
                        contentDescription = "更多音量设置",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 亮度行
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.BrightnessHigh, contentDescription = null)
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    "亮度",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.width(50.dp)
                )
                Slider(
                    value = brightness.toFloat(),
                    valueRange = 0f..maxBrightness.toFloat(),
                    onValueChange = { },
                    onValueChangeFinished = onOpenDisplaySettings,
                    modifier = Modifier.weight(1f),
                    enabled = false
                )
                IconButton(onClick = onOpenDisplaySettings) {
                    Icon(
                        Icons.Default.Settings,
                        contentDescription = "更多显示设置",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            
            // 提示
            Text(
                "提示：拖动亮度滑块后将打开系统显示设置",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun BatteryInfoCard(
    batteryLevel: Int,
    isCharging: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        if (isCharging) Icons.Default.BatteryChargingFull else Icons.Default.BatteryStd,
                        contentDescription = "电池",
                        tint = if (batteryLevel > 20) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        "$batteryLevel%",
                        style = MaterialTheme.typography.titleLarge
                    )
                }
                Text(
                    if (isCharging) "充电中" else "未充电",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
fun AutomationRulesCard() {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                "预设场景",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SceneButton(
                    label = "起床",
                    icon = Icons.Default.WbSunny,
                    modifier = Modifier.weight(1f)
                )
                SceneButton(
                    label = "睡眠",
                    icon = Icons.Default.Bedtime,
                    modifier = Modifier.weight(1f)
                )
                SceneButton(
                    label = "工作",
                    icon = Icons.Default.Work,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
fun SceneButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                icon,
                contentDescription = label,
                tint = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                label,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutomationRuleItem(
    rule: com.neuralmind.domain.model.AutomationRule,
    onToggle: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    rule.name,
                    style = MaterialTheme.typography.titleSmall
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    rule.description,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Switch(
                    checked = rule.isEnabled,
                    onCheckedChange = { onToggle() }
                )
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "删除")
                }
            }
        }
    }
}
