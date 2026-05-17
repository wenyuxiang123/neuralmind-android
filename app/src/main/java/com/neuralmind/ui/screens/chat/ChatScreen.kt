package com.neuralmind.ui.screens.chat

import android.Manifest
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.neuralmind.domain.model.Message
import com.neuralmind.domain.model.MessageRole
import com.neuralmind.ui.theme.*
import com.neuralmind.ui.viewmodel.ChatViewModel
import com.neuralmind.voice.VoiceState
import com.neuralmind.voice.VoiceViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ChatScreen(
    conversationId: Long,
    onNavigateBack: () -> Unit,
    onNavigateToModels: () -> Unit,
    onNavigateToMemory: () -> Unit,
    viewModel: ChatViewModel = hiltViewModel(),
    voiceViewModel: VoiceViewModel = hiltViewModel()
) {
    val messages by viewModel.messages.collectAsState()
    val currentConversation by viewModel.currentConversation.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val streamingMessage by viewModel.streamingMessage.collectAsState()
    val voiceState by voiceViewModel.voiceState.collectAsState()
    val partialText by voiceViewModel.partialText.collectAsState()
    val ttsEnabled by voiceViewModel.ttsEnabled.collectAsState()
    val isSpeaking by voiceViewModel.isSpeaking.collectAsState()
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // 录音权限请求
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            voiceViewModel.toggleListening()
        } else {
            Toast.makeText(context, "需要麦克风权限才能使用语音功能", Toast.LENGTH_SHORT).show()
        }
    }

    // 监听语音识别结果，自动填入输入框并发送
    LaunchedEffect(Unit) {
        voiceViewModel.recognizedText.collect { recognized ->
            if (recognized.isNotEmpty()) {
                viewModel.updateInputText(recognized)
                // 清空 recognizedText 以便下次识别
                voiceViewModel.recognizedText.collect { /* consume */ }
            }
        }
    }

    // 监听 AI 回复，语音播报
    LaunchedEffect(streamingMessage) {
        if (streamingMessage == null && messages.isNotEmpty()) {
            val lastMessage = messages.first()
            if (lastMessage.role == MessageRole.ASSISTANT && ttsEnabled) {
                // 延迟一小段时间，确保消息已经保存
                kotlinx.coroutines.delay(500)
                voiceViewModel.speakText(lastMessage.content)
            }
        }
    }

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
        // 顶部 TTS 开关按钮
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { voiceViewModel.toggleTts() },
                colors = IconButtonDefaults.iconButtonColors(
                    contentColor = if (ttsEnabled) GradientStart else TextTertiary
                )
            ) {
                Icon(
                    imageVector = if (ttsEnabled) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                    contentDescription = if (ttsEnabled) "关闭语音播报" else "开启语音播报"
                )
            }
            if (isSpeaking) {
                Text(
                    text = "播放中...",
                    style = MaterialTheme.typography.labelSmall,
                    color = GradientStart,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            reverseLayout = true,
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            streamingMessage?.let { streaming -> item(key = "streaming") { StreamingMessageBubbleDark(message = streaming) } }
            items(messages.asReversed(), key = { it.id }) { message -> DarkMessageBubble(message = message) }
            if (messages.isEmpty() && streamingMessage == null) { item { EmptyMessageState() } }
        }

        DarkChatInput(
            inputText = if (partialText.isNotEmpty()) partialText else uiState.inputText,
            onInputChanged = { viewModel.updateInputText(it) },
            onSend = {
                val textToSend = if (partialText.isNotEmpty()) partialText else uiState.inputText
                if (textToSend.isNotBlank()) {
                    viewModel.sendMessage(textToSend)
                }
            },
            isLoading = uiState.isLoading,
            voiceState = voiceState,
            onVoiceClick = {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                    == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    voiceViewModel.toggleListening()
                } else {
                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            },
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
fun DarkChatInput(
    inputText: String,
    onInputChanged: (String) -> Unit,
    onSend: () -> Unit,
    isLoading: Boolean,
    voiceState: VoiceState,
    onVoiceClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showAttachMenu by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current

    // 麦克风按钮动画
    val infiniteTransition = rememberInfiniteTransition(label = "voice")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )
    val micButtonColor by animateColorAsState(
        targetValue = when (voiceState) {
            is VoiceState.Listening -> Color.Red
            is VoiceState.Processing -> GradientStart
            is VoiceState.Error -> Color(0xFFFF6B6B)
            VoiceState.Idle -> TextSecondary
        },
        animationSpec = tween(300),
        label = "micColor"
    )

    Surface(modifier = modifier, color = BackgroundSecondary, shadowElevation = 8.dp) {
        Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            // 附件+图片合并按钮
            Box {
                IconButton(onClick = { showAttachMenu = !showAttachMenu }, colors = IconButtonDefaults.iconButtonColors(contentColor = TextSecondary)) {
                    Icon(Icons.Default.AddCircleOutline, contentDescription = "添加")
                }
                DropdownMenu(expanded = showAttachMenu, onDismissRequest = { showAttachMenu = false }) {
                    DropdownMenuItem(text = { Text("文件", color = TextPrimary) }, leadingIcon = { Icon(Icons.Default.AttachFile, contentDescription = null, tint = TextSecondary) }, onClick = { showAttachMenu = false; Toast.makeText(context, "文件上传功能开发中", Toast.LENGTH_SHORT).show() })
                    DropdownMenuItem(text = { Text("图片", color = TextPrimary) }, leadingIcon = { Icon(Icons.Default.Image, contentDescription = null, tint = TextSecondary) }, onClick = { showAttachMenu = false; Toast.makeText(context, "图片上传功能开发中", Toast.LENGTH_SHORT).show() })
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
            // 语音按钮 - 根据状态显示不同样式
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .then(
                        if (voiceState is VoiceState.Listening) {
                            Modifier
                                .scale(pulseScale)
                                .border(2.dp, Color.Red.copy(alpha = 0.5f), CircleShape)
                        } else Modifier
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (voiceState is VoiceState.Processing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                        color = micButtonColor
                    )
                } else {
                    IconButton(
                        onClick = onVoiceClick,
                        colors = IconButtonDefaults.iconButtonColors(contentColor = micButtonColor)
                    ) {
                        Icon(
                            imageVector = when (voiceState) {
                                is VoiceState.Listening -> Icons.Default.Stop
                                else -> Icons.Default.Mic
                            },
                            contentDescription = "语音"
                        )
                    }
                }
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
