package com.neuralmind.ui.screens.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.neuralmind.domain.model.Conversation
import com.neuralmind.ui.theme.*
import com.neuralmind.ui.viewmodel.ChatViewModel

@Composable
fun ChatListScreen(
    viewModel: ChatViewModel = hiltViewModel(),
    onConversationClick: (Long) -> Unit,
    onNewConversation: (Long) -> Unit
) {
    val conversations by viewModel.conversations.collectAsState()
    val installedModels by viewModel.installedModels.collectAsState()
    var showNewConversationDialog by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier.fillMaxSize()
            .background(brush = Brush.verticalGradient(colors = listOf(BackgroundPrimary, Color(0xFF0A1628))))
    {
        if (conversations.isEmpty()) {
            EmptyChatState(onNewConversation = { showNewConversationDialog = true })
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item { Text(text = "开始新对话", style = MaterialTheme.typography.titleMedium, color = TextSecondary, modifier = Modifier.padding(bottom = 8.dp)) }
                item { NewConversationCard(onClick = { showNewConversationDialog = true }) }
                item { Spacer(modifier = Modifier.height(8.dp)); Text(text = "历史对话", style = MaterialTheme.typography.titleMedium, color = TextSecondary) }
                items(conversations, key = { it.id }) { conversation ->
                    ConversationItem(conversation = conversation, onClick = { onConversationClick(conversation.id) })
                }
            }
        }

        FloatingActionButton(
            onClick = { showNewConversationDialog = true },
            containerColor = GradientStart, contentColor = Color.White,
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
        ) { Icon(Icons.Default.Add, contentDescription = "新建对话") }

        if (showNewConversationDialog) {
            NewConversationDialog(
                installedModels = installedModels,
                onDismiss = { showNewConversationDialog = false },
                onConfirm = { title, model ->
                    viewModel.createConversation(title, model) { conversationId ->
                        showNewConversationDialog = false
                        onNewConversation(conversationId)
                    }
                }
            )
        }
    }
}

@Composable
private fun EmptyChatState(onNewConversation: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier.size(120.dp).clip(CircleShape)
                .background(brush = Brush.linearGradient(colors = listOf(GradientStart, GradientEnd, GradientAccent))),
            contentAlignment = Alignment.Center
        ) { Text(text = "N", style = MaterialTheme.typography.displayLarge, fontWeight = FontWeight.Bold, color = Color.White) }
        Spacer(modifier = Modifier.height(32.dp))
        Text(text = "NeuralMind AI", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = TextPrimary)
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = "您的智能AI助手，基于本地大语言模型", style = MaterialTheme.typography.bodyMedium, color = TextSecondary, textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = onNewConversation,
            colors = ButtonDefaults.buttonColors(containerColor = GradientStart),
            shape = RoundedCornerShape(24.dp),
            contentPadding = PaddingValues(horizontal = 32.dp, vertical = 12.dp)
        ) { Icon(Icons.Default.Add, contentDescription = null); Spacer(modifier = Modifier.width(8.dp)); Text("开始新对话", fontWeight = FontWeight.SemiBold) }
    }
}

@Composable
private fun NewConversationCard(onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp))
                    .background(brush = Brush.linearGradient(colors = listOf(GradientStart, GradientEnd))),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Default.Add, contentDescription = null, tint = Color.White) }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "新建对话", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                Text(text = "开始一段新的AI对话之旅", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextTertiary)
        }
    }
}

@Composable
fun ConversationItem(conversation: Conversation, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Chat, contentDescription = null, tint = GradientStart, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = conversation.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = "${conversation.messageCount} 条消息", style = MaterialTheme.typography.bodySmall, color = TextTertiary)
            }
            if (conversation.isPinned) { Icon(Icons.Default.PushPin, contentDescription = "已置顶", tint = GradientStart, modifier = Modifier.size(20.dp)) }
        }
    }
}

@Composable
fun NewConversationDialog(
    installedModels: List<com.neuralmind.domain.model.AIModel>,
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var selectedModel by remember { mutableStateOf(installedModels.firstOrNull()?.id ?: "llama3.2-1b") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BackgroundSecondary,
        title = { Text("新建对话", color = TextPrimary, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                OutlinedTextField(
                    value = title, onValueChange = { title = it },
                    label = { Text("对话标题") }, singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = GradientStart, unfocusedBorderColor = CardBorder,
                        focusedLabelColor = GradientStart, unfocusedLabelColor = TextSecondary,
                        cursorColor = GradientStart, focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text("选择模型", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                Spacer(modifier = Modifier.height(8.dp))
                if (installedModels.isEmpty()) {
                    Text(text = "暂无可用模型，请先下载模型", style = MaterialTheme.typography.bodySmall, color = StatusOffline)
                } else {
                    Column {
                        installedModels.forEach { model ->
                            Row(
                                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                                    .background(if (selectedModel == model.id) CardHighlight else Color.Transparent)
                                    .clickable { selectedModel = model.id }.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(selected = selectedModel == model.id, onClick = { selectedModel = model.id }, colors = RadioButtonDefaults.colors(selectedColor = GradientStart))
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(model.name, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                                    Text(text = model.description, style = MaterialTheme.typography.bodySmall, color = TextTertiary)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { if (title.isNotBlank()) { onConfirm(title, selectedModel) } }, enabled = title.isNotBlank(), colors = ButtonDefaults.buttonColors(containerColor = GradientStart)) { Text("创建") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消", color = TextSecondary) } }
    )
}
