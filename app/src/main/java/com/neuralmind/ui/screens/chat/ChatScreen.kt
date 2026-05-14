package com.neuralmind.ui.screens.chat

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.neuralmind.domain.model.Message
import com.neuralmind.domain.model.MessageRole
import com.neuralmind.ui.theme.*
import com.neuralmind.ui.viewmodel.ChatViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

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
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(messages.size, streamingMessage) {
        if (messages.isNotEmpty()) { listState.animateScrollToItem(0) }
    }

    LaunchedEffect(Unit) {
        viewModel.errorEvent.collect { errorMessage ->
            scope.launch { snackbarHostState.showSnackbar(message = errorMessage, duration = SnackbarDuration.Short) }
        }
    }

    LaunchedEffect(conversationId) { viewModel.loadConversation(conversationId) }

    Column(
        modifier = Modifier.fillMaxSize()
            .background(brush = Brush.verticalGradient(colors = listOf(BackgroundPrimary, Color(0xFF0A1628))))
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            reverseLayout = true,
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(messages.asReversed(), key = { it.id }) { message -> DarkMessageBubble(message = message) }
            streamingMessage?.let { streaming -> item(key = "streaming") { StreamingMessageBubbleDark(message = streaming) } }
            if (messages.isEmpty() && streamingMessage == null) { item { EmptyMessageState() } }
        }

        DarkChatInput(
            inputText = uiState.inputText,
            onInputChanged = { viewModel.updateInputText(it) },
            onSend = { viewModel.sendMessage(uiState.inputText) },
            isLoading = uiState.isLoading,
            modifier = Modifier.fillMaxWidth()
        )
    }

    SnackbarHost(hostState = snackbarHostState)
}

@Composable
private fun EmptyMessageState() {
    Column(modifier = Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier.size(80.dp).clip(CircleShape).background(brush = Brush.linearGradient(colors = listOf(GradientStart, GradientEnd))),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Default.Psychology, contentDescription = null, tint = Color.White, modifier = Modifier.size(40.dp)) }
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = "开始对话吧！", style = MaterialTheme.typography.titleMedium, color = TextSecondary)
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = "发送消息与AI助手交流", style = MaterialTheme.typography.bodyMedium, color = TextTertiary, textAlign = TextAlign.Center)
    }
}

@Composable
fun StreamingMessageBubbleDark(message: Message, modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "cursor")
    val cursorAlpha by infiniteTransition.animateFloat(0f, 1f, infiniteRepeatable(animation = tween(500, easing = LinearEasing), repeatMode = RepeatMode.Reverse), label = "cursorAlpha")

    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        Icon(Icons.Default.Android, contentDescription = null, modifier = Modifier.size(32.dp).clip(CircleShape).background(GradientStart), tint = Color.White)
        Spacer(modifier = Modifier.width(8.dp))
        Card(shape = RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp), colors = CardDefaults.cardColors(containerColor = AIBubbleBackground)) {
            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(text = message.content, color = TextPrimary, textAlign = TextAlign.Start)
                Text(text = "▊", color = GradientStart.copy(alpha = cursorAlpha), modifier = Modifier.padding(start = 2.dp))
            }
        }
    }
    Spacer(modifier = Modifier.height(4.dp))
    Text(text = "正在生成...", style = MaterialTheme.typography.labelSmall, color = TextTertiary, modifier = Modifier.padding(start = 40.dp))
}

@Composable
fun DarkMessageBubble(message: Message, modifier: Modifier = Modifier) {
    val isUser = message.role == MessageRole.USER
    val alignment = if (isUser) Alignment.End else Alignment.Start

    Column(modifier = modifier.fillMaxWidth(), horizontalAlignment = alignment) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start) {
            if (!isUser) {
                Icon(Icons.Default.Android, contentDescription = null, modifier = Modifier.size(32.dp).clip(CircleShape).background(GradientStart), tint = Color.White)
                Spacer(modifier = Modifier.width(8.dp))
            }
            if (isUser) {
                Box(modifier = Modifier.clip(RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp)).background(brush = Brush.horizontalGradient(colors = listOf(UserBubbleStart, UserBubbleEnd))).padding(12.dp)) {
                    Text(text = message.content, color = Color.White, textAlign = TextAlign.End)
                }
            } else {
                Card(shape = RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp), colors = CardDefaults.cardColors(containerColor = AIBubbleBackground)) {
                    Text(text = message.content, color = TextPrimary, modifier = Modifier.padding(12.dp), textAlign = TextAlign.Start)
                }
            }
            if (isUser) {
                Spacer(modifier = Modifier.width(8.dp))
                Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(32.dp).clip(CircleShape).background(GradientAccent), tint = Color.White)
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = formatTime(message.timestamp), style = MaterialTheme.typography.labelSmall, color = TextTertiary, modifier = Modifier.padding(horizontal = 4.dp))
    }
}

@Composable
fun DarkChatInput(inputText: String, onInputChanged: (String) -> Unit, onSend: () -> Unit, isLoading: Boolean, modifier: Modifier = Modifier) {
    var showAttachMenu by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current

    Surface(modifier = modifier, color = BackgroundSecondary, shadowElevation = 8.dp) {
        Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            // 附件+图片合并按钮
            Box {
                IconButton(onClick = { showAttachMenu = !showAttachMenu }, colors = IconButtonDefaults.iconButtonColors(contentColor = TextSecondary)) {
                    Icon(Icons.Default.AddCircleOutline, contentDescription = "添加")
                }
                DropdownMenu(expanded = showAttachMenu, onDismissRequest = { showAttachMenu = false }) {
                    DropdownMenuItem(text = { Text("文件", color = TextPrimary) }, leadingIcon = { Icon(Icons.Default.AttachFile, contentDescription = null, tint = TextSecondary) }, onClick = { showAttachMenu = false; android.widget.Toast.makeText(context, "文件上传功能开发中", android.widget.Toast.LENGTH_SHORT).show() })
                    DropdownMenuItem(text = { Text("图片", color = TextPrimary) }, leadingIcon = { Icon(Icons.Default.Image, contentDescription = null, tint = TextSecondary) }, onClick = { showAttachMenu = false; android.widget.Toast.makeText(context, "图片上传功能开发中", android.widget.Toast.LENGTH_SHORT).show() })
                }
            }
            // 输入框
            TextField(
                value = inputText, onValueChange = onInputChanged, modifier = Modifier.weight(1f),
                placeholder = { Text("输入消息...", color = TextTertiary) },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = BackgroundTertiary, unfocusedContainerColor = BackgroundTertiary, disabledContainerColor = BackgroundTertiary,
                    focusedIndicatorColor = GradientStart, unfocusedIndicatorColor = CardBorder, cursorColor = GradientStart,
                    focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary, disabledTextColor = TextPrimary
                ),
                shape = RoundedCornerShape(24.dp), minLines = 1, maxLines = 5
            )
            // 语音按钮
            IconButton(onClick = { android.widget.Toast.makeText(context, "语音功能开发中", android.widget.Toast.LENGTH_SHORT).show() }, colors = IconButtonDefaults.iconButtonColors(contentColor = TextSecondary)) {
                Icon(Icons.Default.Mic, contentDescription = "语音")
            }
            // 发送按钮
            Box(
                modifier = Modifier.size(44.dp).clip(CircleShape).background(brush = if (inputText.isNotBlank() && !isLoading) Brush.linearGradient(colors = listOf(GradientStart, GradientEnd)) else Brush.linearGradient(colors = listOf(CardBorder, CardBorder))),
                contentAlignment = Alignment.Center
            ) {
                IconButton(onClick = onSend, enabled = inputText.isNotBlank() && !isLoading) {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
                    } else {
                        Icon(Icons.Default.Send, contentDescription = "发送", tint = if (inputText.isNotBlank()) Color.White else TextTertiary, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}

private fun formatTime(timestamp: Long): String = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))
