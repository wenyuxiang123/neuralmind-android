package com.neuralmind.ui.screens.chat

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.neuralmind.domain.model.Message
import com.neuralmind.domain.model.MessageRole
import com.neuralmind.ui.viewmodel.ChatViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    conversationId: Long,
    onNavigateBack: () -> Unit,
    onNavigateToModels: () -> Unit,
    onNavigateToMemory: () -> Unit,
    viewModel: ChatViewModel = hiltViewModel()
) {
    val messages by viewModel.messages.collectAsState()
    val currentConversation by viewModel.currentConversation.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val streamingMessage by viewModel.streamingMessage.collectAsState()
    
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    
    // Collect error events
    LaunchedEffect(Unit) {
        viewModel.errorEvent.collect { errorMessage ->
            scope.launch {
                snackbarHostState.showSnackbar(
                    message = errorMessage,
                    duration = SnackbarDuration.Short
                )
            }
        }
    }

    LaunchedEffect(conversationId) {
        viewModel.loadConversation(conversationId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Android, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(currentConversation?.title ?: "对话")
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToModels) {
                        Icon(Icons.Default.Memory, contentDescription = "模型")
                    }
                    IconButton(onClick = onNavigateToMemory) {
                        Icon(Icons.Default.Psychology, contentDescription = "记忆")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                reverseLayout = true
            ) {
                items(
                    messages.asReversed(),
                    key = { it.id }
                ) { message ->
                    MessageBubble(message = message)
                }
                
                // Display streaming message at the end
                streamingMessage?.let { streaming ->
                    item(key = "streaming") {
                        StreamingMessageBubble(message = streaming)
                    }
                }
            }

            ChatInput(
                inputText = uiState.inputText,
                onInputChanged = { viewModel.updateInputText(it) },
                onSend = {
                    viewModel.sendMessage(uiState.inputText)
                },
                isLoading = uiState.isLoading,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun StreamingMessageBubble(
    message: Message,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "cursor")
    val cursorAlpha by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "cursorAlpha"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(8.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Column(
            horizontalAlignment = Alignment.Start
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Android,
                    contentDescription = null,
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    tint = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Card(
                    shape = RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.8f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = message.content,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            textAlign = TextAlign.Start
                        )
                        // Blinking cursor effect
                        Text(
                            text = "▊",
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = cursorAlpha),
                            modifier = Modifier.padding(start = 2.dp)
                        )
                    }
                }
            }
            Text(
                text = "正在生成...",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(4.dp, 2.dp)
            )
        }
    }
}

@Composable
fun MessageBubble(
    message: Message,
    modifier: Modifier = Modifier
) {
    val isUser = message.role == MessageRole.USER
    val alignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart
    val bubbleColor = if (isUser) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.secondaryContainer
    }
    val textColor = if (isUser) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSecondaryContainer
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(8.dp),
        contentAlignment = alignment
    ) {
        Column(
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!isUser) {
                    Icon(
                        Icons.Default.Android,
                        contentDescription = null,
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Card(
                    shape = if (isUser) {
                        RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp)
                    } else {
                        RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp)
                    },
                    colors = CardDefaults.cardColors(
                        containerColor = bubbleColor
                    )
                ) {
                    Text(
                        text = message.content,
                        color = textColor,
                        modifier = Modifier.padding(12.dp),
                        textAlign = if (isUser) TextAlign.End else TextAlign.Start
                    )
                }
                if (isUser) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        Icons.Default.Person,
                        contentDescription = null,
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.secondary),
                        tint = MaterialTheme.colorScheme.onSecondary
                    )
                }
            }
            Text(
                text = formatTime(message.timestamp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(4.dp, 2.dp)
            )
        }
    }
}

@Composable
fun ChatInput(
    inputText: String,
    onInputChanged: (String) -> Unit,
    onSend: () -> Unit,
    isLoading: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .padding(8.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = inputText,
                onValueChange = onInputChanged,
                modifier = Modifier.weight(1f),
                placeholder = { Text("输入消息...") },
                maxLines = 4,
                trailingIcon = {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp
                        )
                    }
                }
            )
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
                onClick = onSend,
                enabled = inputText.isNotBlank() && !isLoading,
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Icon(Icons.Default.Send, contentDescription = "发送")
            }
        }
    }
}

private fun formatTime(timestamp: Long): String {
    val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
