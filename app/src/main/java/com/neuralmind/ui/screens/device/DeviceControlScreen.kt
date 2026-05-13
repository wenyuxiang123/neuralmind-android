package com.neuralmind.ui.screens.device

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
import com.neuralmind.ui.viewmodel.DeviceViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceControlScreen(
    viewModel: DeviceViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设备控制") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
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
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                DeviceInfoCard(
                    batteryLevel = uiState.batteryLevel,
                    isCharging = uiState.isCharging
                )
            }

            item {
                QuickControlsCard(
                    isWifiEnabled = uiState.isWifiEnabled,
                    isBluetoothEnabled = uiState.isBluetoothEnabled,
                    onToggleWifi = { viewModel.toggleWifi() },
                    onToggleBluetooth = { viewModel.toggleBluetooth() }
                )
            }

            item {
                VolumeBrightnessCard(
                    mediaVolume = uiState.mediaVolume,
                    brightness = uiState.brightness,
                    maxMediaVolume = uiState.maxMediaVolume,
                    maxBrightness = 255,
                    onMediaVolumeChanged = { viewModel.setMediaVolume(it) },
                    onBrightnessChanged = { viewModel.setBrightness(it) }
                )
            }

            item {
                AutomationRulesCard()
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "自动化规则",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            items(viewModel.automationRules, key = { it.id }) { rule ->
                AutomationRuleItem(
                    rule = rule,
                    onToggle = { viewModel.toggleRule(rule.id) },
                    onDelete = { viewModel.deleteRule(rule.id) }
                )
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { /* TODO: 添加自动化规则 */ },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("添加规则")
                }
            }
        }
    }
}

@Composable
fun DeviceInfoCard(
    batteryLevel: Int,
    isCharging: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
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
fun QuickControlsCard(
    isWifiEnabled: Boolean,
    isBluetoothEnabled: Boolean,
    onToggleWifi: () -> Unit,
    onToggleBluetooth: () -> Unit
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
                ControlButton(
                    icon = Icons.Default.Wifi,
                    label = "WiFi",
                    isEnabled = isWifiEnabled,
                    onClick = onToggleWifi,
                    modifier = Modifier.weight(1f)
                )
                ControlButton(
                    icon = Icons.Default.Bluetooth,
                    label = "蓝牙",
                    isEnabled = isBluetoothEnabled,
                    onClick = onToggleBluetooth,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ControlButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isEnabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = if (isEnabled) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
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
                tint = if (isEnabled) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(label)
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
    onBrightnessChanged: (Int) -> Unit
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

            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.VolumeUp, contentDescription = null)
                Spacer(modifier = Modifier.width(12.dp))
                Slider(
                    value = mediaVolume.toFloat(),
                    valueRange = 0f..maxMediaVolume.toFloat(),
                    onValueChange = { onMediaVolumeChanged(it.toInt()) },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.BrightnessHigh, contentDescription = null)
                Spacer(modifier = Modifier.width(12.dp))
                Slider(
                    value = brightness.toFloat(),
                    valueRange = 0f..maxBrightness.toFloat(),
                    onValueChange = { onBrightnessChanged(it.toInt()) },
                    modifier = Modifier.weight(1f)
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
