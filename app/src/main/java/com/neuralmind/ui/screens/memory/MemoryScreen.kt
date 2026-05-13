package com.neuralmind.ui.screens.memory

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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.neuralmind.domain.model.MemoryLayer
import com.neuralmind.ui.theme.*
import com.neuralmind.ui.viewmodel.MemoryViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoryScreen(viewModel: MemoryViewModel = hiltViewModel(), onNavigateBack: () -> Unit) {
    val memories by viewModel.memories.collectAsState()
    val activeLayers by viewModel.activeMemoryLayers.collectAsState()

    Column(modifier = Modifier.fillMaxSize().background(brush = Brush.verticalGradient(colors = listOf(BackgroundPrimary, Color(0xFF0A1628))))) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(text = "九层记忆系统", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = GradientStart)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = "模拟人类认知的层级记忆结构", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        }

        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item { Text(text = "记忆层级", style = MaterialTheme.typography.titleMedium, color = TextSecondary) }
            item { Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) { MemoryLayer.entries.take(3).forEach { layer -> DarkMemoryLayerCard(layer = layer, isActive = activeLayers.contains(layer), memoryCount = memories.filter { it.layer == layer && it.isActive }.size, onToggle = { viewModel.toggleLayer(layer) }, modifier = Modifier.weight(1f)) } } }
            item { Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) { MemoryLayer.entries.drop(3).take(3).forEach { layer -> DarkMemoryLayerCard(layer = layer, isActive = activeLayers.contains(layer), memoryCount = memories.filter { it.layer == layer && it.isActive }.size, onToggle = { viewModel.toggleLayer(layer) }, modifier = Modifier.weight(1f)) } } }
            item { Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) { MemoryLayer.entries.drop(6).forEach { layer -> DarkMemoryLayerCard(layer = layer, isActive = activeLayers.contains(layer), memoryCount = memories.filter { it.layer == layer && it.isActive }.size, onToggle = { viewModel.toggleLayer(layer) }, modifier = Modifier.weight(1f)) } } }
            item { Spacer(modifier = Modifier.height(8.dp)); Text(text = "最近记忆", style = MaterialTheme.typography.titleMedium, color = TextSecondary) }
            items(memories.take(10), key = { it.id }) { memory -> DarkMemoryItem(memory = memory) }
        }
    }
}

@Composable
private fun DarkMemoryLayerCard(layer: MemoryLayer, isActive: Boolean, memoryCount: Int, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val layerColors = listOf(MemoryLayer1, MemoryLayer2, MemoryLayer3, MemoryLayer4, MemoryLayer5, MemoryLayer6, MemoryLayer7, MemoryLayer8, MemoryLayer9)
    val gradientColor = layerColors.getOrElse(layer.level - 1) { MemoryLayer1 }
    Card(modifier = modifier, onClick = onToggle, colors = CardDefaults.cardColors(containerColor = if (isActive) gradientColor else CardBackground), shape = RoundedCornerShape(12.dp)) {
        Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(modifier = Modifier.size(40.dp).background(if (isActive) Brush.radialGradient(colors = listOf(GradientStart, gradientColor)) else Brush.radialGradient(colors = listOf(CardBorder, CardBorder)), shape = RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) { Icon(if (isActive) Icons.Default.CheckCircle else Icons.Default.Circle, contentDescription = null, tint = if (isActive) Color.White else TextTertiary, modifier = Modifier.size(20.dp)) }
            Spacer(modifier = Modifier.height(8.dp))
            Text("L${layer.level}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = if (isActive) Color.White else TextPrimary)
            Text(layer.description, style = MaterialTheme.typography.labelSmall, color = if (isActive) Color.White.copy(alpha = 0.8f) else TextTertiary, maxLines = 1)
            Spacer(modifier = Modifier.height(4.dp))
            Text("$memoryCount 条", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium, color = if (isActive) GradientStart else TextSecondary)
        }
    }
}

@Composable
fun DarkMemoryItem(memory: com.neuralmind.domain.model.Memory) {
    val layerColors = listOf(MemoryLayer1, MemoryLayer2, MemoryLayer3, MemoryLayer4, MemoryLayer5, MemoryLayer6, MemoryLayer7, MemoryLayer8, MemoryLayer9)
    val layerColor = layerColors.getOrElse(memory.layer.level - 1) { MemoryLayer1 }
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = CardBackground), shape = RoundedCornerShape(12.dp)) {
        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.Top) {
                Box(modifier = Modifier.size(8.dp).background(layerColor, RoundedCornerShape(4.dp)))
                Spacer(modifier = Modifier.width(8.dp))
                Column { Text(memory.content, style = MaterialTheme.typography.bodyMedium, color = TextPrimary) }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Surface(shape = RoundedCornerShape(4.dp), color = layerColor.copy(alpha = 0.3f)) { Text("L${memory.layer.level}", modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, color = layerColor) }
                Spacer(modifier = Modifier.height(4.dp))
                Text("重要度 ${memory.importance}", style = MaterialTheme.typography.labelSmall, color = TextTertiary)
            }
        }
    }
}
