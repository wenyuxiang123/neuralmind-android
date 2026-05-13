package com.neuralmind.ui.screens.device

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.neuralmind.ui.theme.*
import com.neuralmind.ui.viewmodel.DeviceViewModel

@Composable
fun DeviceControlScreen(viewModel: DeviceViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    Column(modifier = Modifier.fillMaxSize().background(brush = Brush.verticalGradient(colors = listOf(BackgroundPrimary, Color(0xFF0A1628)))).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(text = "推理引擎", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = GradientStart)
        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = GradientStart.copy(alpha = 0.1f)), shape = RoundedCornerShape(12.dp)) {
            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Info, contentDescription = null, tint = GradientStart); Spacer(modifier = Modifier.width(8.dp)); Text("管理设备设置和快捷控制", style = MaterialTheme.typography.bodySmall, color = TextSecondary) }
        }
        DarkQuickControlsCard(isWifiEnabled = uiState.isWifiEnabled, isBluetoothEnabled = uiState.isBluetoothEnabled, onOpenWifiSettings = { viewModel.openWifiSettings() }, onOpenBluetoothSettings = { viewModel.openBluetoothSettings() })
        DarkVolumeBrightnessCard(mediaVolume = uiState.mediaVolume, brightness = uiState.brightness, maxMediaVolume = uiState.maxMediaVolume, maxBrightness = 255, onMediaVolumeChanged = { viewModel.setMediaVolume(it) }, onOpenSoundSettings = { viewModel.openSoundSettings() }, onOpenDisplaySettings = { viewModel.openDisplaySettings() })
        DarkBatteryInfoCard(batteryLevel = uiState.batteryLevel, isCharging = uiState.isCharging)
        DarkAutomationRulesCard()
    }
}

@Composable
fun DarkQuickControlsCard(isWifiEnabled: Boolean, isBluetoothEnabled: Boolean, onOpenWifiSettings: () -> Unit, onOpenBluetoothSettings: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = CardBackground), shape = RoundedCornerShape(16.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("快捷控制", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                DarkSettingsButton(icon = Icons.Default.Wifi, label = "WiFi", description = if (isWifiEnabled) "已开启" else "已关闭", isActive = isWifiEnabled, onClick = onOpenWifiSettings, modifier = Modifier.weight(1f))
                DarkSettingsButton(icon = Icons.Default.Bluetooth, label = "蓝牙", description = if (isBluetoothEnabled) "已开启" else "已关闭", isActive = isBluetoothEnabled, onClick = onOpenBluetoothSettings, modifier = Modifier.weight(1f))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DarkSettingsButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, description: String, isActive: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(modifier = modifier, onClick = onClick, colors = CardDefaults.cardColors(containerColor = if (isActive) GradientStart.copy(alpha = 0.2f) else BackgroundTertiary), shape = RoundedCornerShape(12.dp)) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = label, tint = if (isActive) GradientStart else TextSecondary)
            Spacer(modifier = Modifier.height(8.dp))
            Text(label, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
            Spacer(modifier = Modifier.height(4.dp))
            Text(description, style = MaterialTheme.typography.bodySmall, color = if (isActive) GradientStart else TextTertiary)
            Spacer(modifier = Modifier.height(4.dp))
            Text("打开设置", style = MaterialTheme.typography.labelSmall, color = GradientStart)
        }
    }
}

@Composable
fun DarkVolumeBrightnessCard(mediaVolume: Int, brightness: Int, maxMediaVolume: Int, maxBrightness: Int, onMediaVolumeChanged: (Int) -> Unit, onOpenSoundSettings: () -> Unit, onOpenDisplaySettings: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = CardBackground), shape = RoundedCornerShape(16.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("音量和亮度", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            Spacer(modifier = Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.VolumeUp, contentDescription = null, tint = TextSecondary)
                Spacer(modifier = Modifier.width(12.dp))
                Text("音量", style = MaterialTheme.typography.bodyMedium, color = TextPrimary, modifier = Modifier.width(50.dp))
                Slider(value = mediaVolume.toFloat(), valueRange = 0f..maxMediaVolume.toFloat(), onValueChange = { onMediaVolumeChanged(it.toInt()) }, modifier = Modifier.weight(1f), colors = SliderDefaults.colors(thumbColor = GradientStart, activeTrackColor = GradientStart, inactiveTrackColor = CardBorder))
                IconButton(onClick = onOpenSoundSettings) { Icon(Icons.Default.Settings, contentDescription = "更多音量设置", tint = GradientStart) }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.BrightnessHigh, contentDescription = null, tint = TextSecondary)
                Spacer(modifier = Modifier.width(12.dp))
                Text("亮度", style = MaterialTheme.typography.bodyMedium, color = TextPrimary, modifier = Modifier.width(50.dp))
                Slider(value = brightness.toFloat(), valueRange = 0f..maxBrightness.toFloat(), onValueChange = {}, onValueChangeFinished = onOpenDisplaySettings, modifier = Modifier.weight(1f), enabled = false, colors = SliderDefaults.colors(disabledThumbColor = TextTertiary, disabledActiveTrackColor = CardBorder, disabledInactiveTrackColor = CardBorder))
                IconButton(onClick = onOpenDisplaySettings) { Icon(Icons.Default.Settings, contentDescription = "更多显示设置", tint = GradientStart) }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text("提示：拖动亮度滑块后将打开系统显示设置", style = MaterialTheme.typography.labelSmall, color = TextTertiary)
        }
    }
}

@Composable
fun DarkBatteryInfoCard(batteryLevel: Int, isCharging: Boolean) {
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = CardBackground), shape = RoundedCornerShape(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) { Icon(if (isCharging) Icons.Default.BatteryChargingFull else Icons.Default.BatteryStd, contentDescription = "电池", tint = if (batteryLevel > 20) GradientStart else StatusOffline); Spacer(modifier = Modifier.width(12.dp)); Text("$batteryLevel%", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = TextPrimary) }
                Spacer(modifier = Modifier.height(4.dp))
                Text(if (isCharging) "充电中" else "未充电", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
            }
            Box(modifier = Modifier.width(60.dp).height(100.dp).background(CardBorder, RoundedCornerShape(8.dp)).padding(4.dp)) { Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Bottom) { Box(modifier = Modifier.fillMaxWidth().fillMaxHeight(batteryLevel / 100f).background(if (batteryLevel > 20) StatusOnline else StatusOffline, RoundedCornerShape(4.dp))) } }
        }
    }
}

@Composable
fun DarkAutomationRulesCard() {
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = CardBackground), shape = RoundedCornerShape(16.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("预设场景", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DarkSceneButton(label = "起床", icon = Icons.Default.WbSunny, modifier = Modifier.weight(1f))
                DarkSceneButton(label = "睡眠", icon = Icons.Default.Bedtime, modifier = Modifier.weight(1f))
                DarkSceneButton(label = "工作", icon = Icons.Default.Work, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun DarkSceneButton(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier = Modifier) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = GradientStart.copy(alpha = 0.15f)), shape = RoundedCornerShape(12.dp)) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) { Icon(icon, contentDescription = label, tint = GradientStart); Spacer(modifier = Modifier.height(4.dp)); Text(label, style = MaterialTheme.typography.bodySmall, color = TextPrimary) }
    }
}
