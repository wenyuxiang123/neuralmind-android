package com.neuralmind.ui.screens.chat

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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.neuralmind.domain.model.Conversation
import com.neuralmind.ui.viewmodel.ChatViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatListScreen(
    viewModel: ChatViewModel = hiltViewModel(),
    onConversationClick: (Long) -> Unit,
    onNewConversation: (Long) -> Unit
) {
    val conversations by viewModel.conversations.collectAsState()
    var showNewConversationDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("NeuralMind AI") },
                actions = {
                    IconButton(onClick = { showNewConversationDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "新建对话")
                    }
                }
            )
        }
    ) { padding ->
        if (conversations.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Chat,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "开始新对话",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = { showNewConversationDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("新建对话")
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                items(conversations, key = { it.id }) { conversation ->
                    ConversationItem(
                        conversation = conversation,
                        onClick = { onConversationClick(conversation.id) }
                    )
                }
            }
        }

        if (showNewConversationDialog) {
            NewConversationDialog(
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
fun ConversationItem(
    conversation: Conversation,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = {
            Text(
                conversation.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        supportingContent = {
            Text(
                "${conversation.messageCount} 条消息",
                style = MaterialTheme.typography.bodySmall
            )
        },
        leadingContent = {
            Icon(Icons.Default.Chat, contentDescription = null)
        },
        trailingContent = {
            if (conversation.isPinned) {
                Icon(Icons.Default.PushPin, contentDescription = "已置顶")
            }
        },
        modifier = Modifier.clickable(onClick = onClick)
    )
}

@Composable
fun NewConversationDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var selectedModel by remember { mutableStateOf("llama3.2-1b") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新建对话") },
        text = {
            Column {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("对话标题") },
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text("选择模型", style = MaterialTheme.typography.labelMedium)
                Spacer(modifier = Modifier.height(8.dp))
                ModelSelector(
                    selectedModel = selectedModel,
                    onModelSelected = { selectedModel = it }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank()) {
                        onConfirm(title, selectedModel)
                    }
                },
                enabled = title.isNotBlank()
            ) {
                Text("创建")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

@Composable
fun ModelSelector(
    selectedModel: String,
    onModelSelected: (String) -> Unit
) {
    val models = listOf(
        "llama3.2-1b" to "LLaMA 3.2 1B",
        "gemma-2b" to "Gemma 2B",
        "phi-2.5" to "Phi-2.5 3B",
        "qwen2.5-0.5b" to "Qwen2.5 0.5B"
    )

    Column {
        models.forEach { (id, name) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onModelSelected(id) }
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = selectedModel == id,
                    onClick = { onModelSelected(id) }
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(name)
            }
        }
    }
}
