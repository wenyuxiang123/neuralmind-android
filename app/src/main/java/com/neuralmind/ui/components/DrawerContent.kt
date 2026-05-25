package com.neuralmind.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.neuralmind.ui.theme.*

data class DrawerMenuItem(
    val title: String,
    val icon: ImageVector,
    val route: String,
    val isSelected: Boolean = false
)

@Composable
fun DrawerContent(
    currentRoute: String?,
    onNavigate: (String) -> Unit,
    currentModelName: String = "LLaMA 3.2",
    isModelOnline: Boolean = true,
    modifier: Modifier = Modifier
) {
    val menuItems = listOf(
        DrawerMenuItem(title = "聊天", icon = Icons.Default.Chat, route = "chat_list", isSelected = currentRoute == "chat_list"),
        DrawerMenuItem(title = "模型库", icon = Icons.Default.Inventory2, route = "model_library", isSelected = currentRoute == "model_library"),
        DrawerMenuItem(title = "技能库", icon = Icons.Default.Bolt, route = "skills", isSelected = currentRoute == "skills"),
        DrawerMenuItem(title = "记忆测试", icon = Icons.Default.Psychology, route = "memory", isSelected = currentRoute == "memory"),
        DrawerMenuItem(title = "推理引擎", icon = Icons.Default.Settings, route = "device_control", isSelected = currentRoute == "device_control"),
        DrawerMenuItem(title = "硬件加速", icon = Icons.Default.Memory, route = "acceleration_settings", isSelected = currentRoute == "acceleration_settings")
    )

    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(280.dp)
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(DrawerBackground, BackgroundPrimary.copy(alpha = 0.95f))
                )
            )
            .padding(16.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier.size(72.dp).clip(RoundedCornerShape(16.dp))
                        .background(brush = Brush.linearGradient(colors = listOf(GradientStart, GradientEnd))),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "N", style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold, color = Color.White)
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(text = "NeuralMind AI", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = TextPrimary)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        menuItems.forEach { item ->
            DrawerMenuItemRow(item = item, onClick = { onNavigate(item.route) })
        }

        Spacer(modifier = Modifier.weight(1f))

        Box(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(CardBackground).padding(16.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text(text = "当前模型", style = MaterialTheme.typography.labelMedium, color = TextTertiary)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = currentModelName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, color = TextPrimary)
                }
                Box(modifier = Modifier.size(12.dp).clip(RoundedCornerShape(6.dp)).background(if (isModelOnline) StatusOnline else StatusOffline))
            }
        }
    }
}

@Composable
private fun DrawerMenuItemRow(item: DrawerMenuItem, onClick: () -> Unit) {
    val isSelected = item.route == "chat_list" || item.isSelected
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) DrawerItemSelected else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = item.icon, contentDescription = item.title, tint = if (isSelected) DrawerItemTextSelected else DrawerItemText, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Text(text = item.title, style = MaterialTheme.typography.bodyLarge, fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal, color = if (isSelected) DrawerItemTextSelected else DrawerItemText)
    }
}
