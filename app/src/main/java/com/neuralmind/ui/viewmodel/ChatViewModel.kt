package com.neuralmind.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.content.Context
import com.neuralmind.core.MemoryMonitor
import dagger.hilt.android.qualifiers.ApplicationContext
import com.neuralmind.core.Logger
import com.neuralmind.data.repository.ChatRepository
import com.neuralmind.data.repository.ModelRepository
import com.neuralmind.data.repository.MemoryRepository
import com.neuralmind.data.repository.SkillRepository
import com.neuralmind.tools.DeviceToolExecutor
import com.neuralmind.service.NeuralMindAccessibilityService
import com.neuralmind.domain.model.AIModel
import com.neuralmind.domain.model.Conversation
import com.neuralmind.domain.model.Message
import com.neuralmind.domain.model.MessageRole
import com.neuralmind.llama.LlamaEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val chatRepository: ChatRepository,
    private val modelRepository: ModelRepository,
    private val memoryRepository: MemoryRepository,
    private val skillRepository: SkillRepository,
    private val llamaEngine: LlamaEngine,
    private val deviceToolExecutor: DeviceToolExecutor,
    private val memoryMonitor: MemoryMonitor
) : ViewModel() {
    
    init {
        memoryMonitor.startMonitoring()
        Logger.i(Logger.Tags.VM, "ChatViewModel: memory monitoring started")
    }
    
    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()
    
    val conversations = chatRepository.getActiveConversations()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    
    val installedModels = modelRepository.getInstalledModels()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    
    val allModels = modelRepository.getAllModels()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    
    private val _currentConversation = MutableStateFlow<Conversation?>(null)
    val currentConversation: StateFlow<Conversation?> = _currentConversation
    
    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages
    
    private val _streamingMessage = MutableStateFlow<Message?>(null)
    val streamingMessage: StateFlow<Message?> = _streamingMessage
    
    private val _errorEvent = Channel<String>(Channel.BUFFERED)
    val errorEvent = _errorEvent.receiveAsFlow()
    
    private val reservedOutputTokens = 512
    private val tokenBudget: Int
        get() = llamaEngine.getNctx() - reservedOutputTokens
    
    fun updateInputText(text: String) {
        Logger.d(Logger.Tags.VM, "updateInputText(text=${text.take(20)}...)")
        _uiState.update { it.copy(inputText = text) }
    }
    
    fun createConversation(title: String, model: String, onCreated: (Long) -> Unit) {
        Logger.d(Logger.Tags.VM, "createConversation(title=$title, model=$model)")
        viewModelScope.launch {
            try {
                val id = chatRepository.createConversation(title, model)
                Logger.i(Logger.Tags.VM, "createConversation success: id=$id")
                onCreated(id)
            } catch (e: Exception) {
                Logger.e(Logger.Tags.VM, "createConversation failed: title=$title", e)
            }
        }
    }
    
    fun loadConversation(conversationId: Long) {
        Logger.d(Logger.Tags.VM, "loadConversation(conversationId=$conversationId)")
        viewModelScope.launch {
            try {
                val conversation = chatRepository.getConversationById(conversationId)
                _currentConversation.value = conversation
                Logger.i(Logger.Tags.VM, "loadConversation success: ${conversation?.title}")
                
                conversation?.let { conv ->
                    chatRepository.getMessagesByConversation(conv.id).collect { msgs ->
                        _messages.value = msgs
                        Logger.d(Logger.Tags.VM, "loadConversation: loaded ${msgs.size} messages")
                    }
                }
            } catch (e: Exception) {
                Logger.e(Logger.Tags.VM, "loadConversation failed: conversationId=$conversationId", e)
            }
        }
    }
    
    fun sendMessage(content: String) {
        val conversation = _currentConversation.value
        if (conversation == null) {
            Logger.w(Logger.Tags.VM, "sendMessage: no current conversation")
            return
        }
        
        Logger.d(Logger.Tags.VM, "sendMessage(content=${content.take(50)}...)")
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, isStreaming = false, inputText = "") }
            
            try {
                val userMessageId = chatRepository.sendMessage(
                    conversationId = conversation.id,
                    role = MessageRole.USER,
                    content = content
                )
                Logger.d(Logger.Tags.VM, "sendMessage: user message sent, id=$userMessageId")
                
                memoryRepository.activateMemoryFromUserInput(content)
                
                _uiState.update { it.copy(isLoading = false, isStreaming = true) }
                
                val contextMessages = _messages.value.dropLast(1).takeLast(10)
                generateAIResponse(conversation, content, contextMessages)
            } catch (e: Exception) {
                Logger.e(Logger.Tags.VM, "sendMessage failed", e)
                _uiState.update { it.copy(isLoading = false, isStreaming = false) }
                _errorEvent.send("发送消息失败: ${e.message}")
            }
        }
    }
    
    private suspend fun generateAIResponse(
        conversation: Conversation,
        userInput: String,
        contextMessages: List<Message>
    ) {
        Logger.d(Logger.Tags.VM, "generateAIResponse: building prompt")
        val modelId = conversation.model
        val currentTime = System.currentTimeMillis()
        
        _streamingMessage.value = Message(
            id = -1,
            conversationId = conversation.id,
            role = MessageRole.ASSISTANT,
            content = "",
            timestamp = currentTime,
            model = modelId
        )
        
        val modelLoaded = if (llamaEngine.isModelLoaded.value) {
            Logger.d(Logger.Tags.VM, "generateAIResponse: model already loaded")
            true
        } else {
            val targetModelId = modelRepository.currentModel.value?.id ?: modelId
            if (targetModelId.isNotEmpty()) {
                Logger.d(Logger.Tags.VM, "generateAIResponse: loading model $targetModelId")
                llamaEngine.loadModel(targetModelId)
            } else {
                Logger.w(Logger.Tags.VM, "generateAIResponse: no model available")
                false
            }
        }
        
        if (modelLoaded) {
            Logger.d(Logger.Tags.VM, "generateAIResponse: model loaded, starting inference")
            
            if (memoryMonitor.isMemoryPressure()) {
                Logger.w(Logger.Tags.VM, "Memory pressure detected (${memoryMonitor.getMemoryUsagePercent().toInt()}%), clearing KV cache and L1 memories")
                llamaEngine.clearKvRange()
                memoryRepository.clearLayerMemories(com.neuralmind.domain.model.MemoryLayer.L1_WORKING)
            }
            
            val prompt = buildPrompt(userInput, contextMessages, modelId)
            generateWithToolLoop(conversation, prompt, modelId, userInput)
        } else {
            Logger.w(Logger.Tags.VM, "generateAIResponse: model not loaded, sending fallback response")
            val fallbackResponse = "模型未加载，请先在模型库中下载并加载一个模型！"
            chatRepository.sendMessage(
                conversationId = conversation.id,
                role = MessageRole.ASSISTANT,
                content = fallbackResponse,
                model = modelId
            )
            _streamingMessage.value = null
            _uiState.update { it.copy(isStreaming = false) }
            _errorEvent.send("模型未加载，请先在模型库中下载并选择一个模型")
        }
    }
    
    private suspend fun handleTimeout(
        conversation: Conversation,
        modelId: String,
        userInput: String,
        allDisplayText: String,
        originalAiResponse: String,
        reason: String
    ) {
        Logger.w(Logger.Tags.VM, "handleTimeout: $reason")
        chatRepository.sendMessage(
            conversationId = conversation.id,
            role = MessageRole.ASSISTANT,
            content = allDisplayText.ifBlank { "推理超时，请重试" },
            model = modelId
        )
        memoryRepository.saveConversationSegment(userInput, originalAiResponse, modelId)
        _streamingMessage.value = null
        _uiState.update { it.copy(isStreaming = false) }
        memoryMonitor.stopInferenceTimeout()
    }
    
    private suspend fun finalizeResponse(
        conversation: Conversation,
        modelId: String,
        userInput: String,
        allDisplayText: String,
        originalAiResponse: String
    ) {
        chatRepository.sendMessage(
            conversationId = conversation.id,
            role = MessageRole.ASSISTANT,
            content = allDisplayText,
            model = modelId
        )
        memoryRepository.saveConversationSegment(userInput, originalAiResponse, modelId)
        Logger.i(Logger.Tags.VM, "generateWithToolLoop: completed, ${allDisplayText.length} chars")
        _streamingMessage.value = null
        _uiState.update { it.copy(isStreaming = false) }
        memoryMonitor.stopInferenceTimeout()
    }
    
    private suspend fun generateWithToolLoop(
        conversation: Conversation,
        initialPrompt: String,
        modelId: String,
        userInput: String
    ) {
        var currentPrompt = initialPrompt
        val maxIterations = 3
        var iteration = 0
        var allDisplayText = ""
        var originalAiResponse = ""
        
        memoryMonitor.startInferenceTimeout(60_000L)
        
        while (iteration < maxIterations) {
            iteration++
            var tempResponse = ""
            
            if (memoryMonitor.isInferenceTimeout()) {
                handleTimeout(conversation, modelId, userInput, allDisplayText, originalAiResponse, "stopping generation")
                return
            }
            
            _streamingMessage.value = Message(
                id = -1,
                conversationId = conversation.id,
                role = MessageRole.ASSISTANT,
                content = if (allDisplayText.isNotBlank()) allDisplayText else "",
                timestamp = System.currentTimeMillis(),
                model = modelId
            )
            
            val responseDeferred = CompletableDeferred<String>()
            
            llamaEngine.generate(
                prompt = currentPrompt,
                onToken = { token ->
                    tempResponse += token
                    _streamingMessage.value = _streamingMessage.value?.copy(
                        content = allDisplayText + tempResponse
                    )
                },
                onComplete = { finalResponse ->
                    responseDeferred.complete(finalResponse)
                },
                onError = { error ->
                    responseDeferred.completeExceptionally(Exception(error))
                }
            )
            
            val response = try {
                responseDeferred.await()
            } catch (e: Exception) {
                Logger.e(Logger.Tags.VM, "generateWithToolLoop error: ${e.message}")
                chatRepository.sendMessage(
                    conversationId = conversation.id,
                    role = MessageRole.ASSISTANT,
                    content = allDisplayText.ifBlank { "生成失败: ${e.message}" },
                    model = modelId
                )
                _streamingMessage.value = null
                _uiState.update { it.copy(isStreaming = false) }
                memoryMonitor.stopInferenceTimeout()
                return
            }
            
            if (memoryMonitor.isInferenceTimeout()) {
                handleTimeout(conversation, modelId, userInput, allDisplayText, originalAiResponse, "after tool execution")
                return
            }
            
            val (cleanText, toolCalls) = deviceToolExecutor.parseToolCalls(response)
            
            if (toolCalls.isEmpty()) {
                allDisplayText += cleanText
                originalAiResponse += cleanText
                finalizeResponse(conversation, modelId, userInput, allDisplayText, originalAiResponse)
                return
            }
            
            Logger.i(Logger.Tags.VM, "Found ${toolCalls.size} tool calls, executing...")
            
            originalAiResponse += response
            
            if (cleanText.isNotBlank()) {
                allDisplayText += cleanText + "\n"
            }
            
            val toolResults = StringBuilder()
            toolResults.append("[工具执行结果]\n")
            
            for (call in toolCalls) {
                Logger.d(Logger.Tags.VM, "Executing tool: ${call.name} → ${call.params}")
                val result = deviceToolExecutor.executeTool(call)
                Logger.d(Logger.Tags.VM, "Tool result: success=${result.success}, msg=${result.message}")
                
                toolResults.append("- ${call.name}(${call.params}): ${result.message}\n")
                if (result.data.isNotEmpty()) {
                    toolResults.append("  数据: ${result.data.take(500)}\n")
                }
            }
            
            allDisplayText += toolResults.toString()
            
            _streamingMessage.value = _streamingMessage.value?.copy(content = allDisplayText)
            
            val template = ChatTemplate.fromModelId(modelId)
            currentPrompt = buildToolResultPrompt(template, currentPrompt, response, toolResults.toString())
            
            llamaEngine.clearPromptCache()
        }
        
        Logger.w(Logger.Tags.VM, "generateWithToolLoop: max iterations ($maxIterations) reached")
        finalizeResponse(conversation, modelId, userInput, allDisplayText, originalAiResponse)
    }
    
    private fun buildToolResultPrompt(
        template: ChatTemplate,
        originalPrompt: String,
        aiResponse: String,
        toolResult: String
    ): String {
        val sb = StringBuilder()
        sb.append(originalPrompt)
        sb.append(aiResponse)
        
        return when (template) {
            ChatTemplate.CHATML -> {
                sb.append("<|im_end|>\n<|im_start|>system\n")
                sb.append(toolResult)
                sb.append("<|im_end|>\n<|im_start|>assistant\n")
                sb.toString()
            }
            ChatTemplate.LLAMA3 -> {
                sb.append("<|eot_id|><|start_header_id|>system<|end_header_id|>\n\n")
                sb.append(toolResult)
                sb.append("<|eot_id|><|start_header_id|>assistant<|end_header_id|>\n\n")
                sb.toString()
            }
            ChatTemplate.PHI -> {
                sb.append("<|end|>\n<|system|>\n")
                sb.append(toolResult)
                sb.append("<|end|>\n<|assistant|>\n")
                sb.toString()
            }
            ChatTemplate.GEMMA -> {
                sb.append("<end_of_turn>\n<start_of_turn>user\n")
                sb.append(toolResult)
                sb.append("<end_of_turn>\n<start_of_turn>model\n")
                sb.toString()
            }
            ChatTemplate.MISTRAL -> {
                sb.append(" [INST] $toolResult [/INST]")
                sb.toString()
            }
        }
    }
    
    private fun estimateTokenCount(text: String): Int {
        var cjkCount = 0
        var asciiCount = 0
        for (char in text) {
            if (char.code > 127) cjkCount++ else asciiCount++
        }
        val estimate = (cjkCount * 2 + asciiCount * 0.25).toInt().coerceAtLeast(1)
        return (estimate * 1.2).toInt()
    }
    
    private enum class ChatTemplate(val id: String) {
        CHATML("chatml"),
        LLAMA3("llama3"),
        PHI("phi"),
        GEMMA("gemma"),
        MISTRAL("mistral");

        companion object {
            fun fromModelId(modelId: String): ChatTemplate {
                return when {
                    modelId.startsWith("qwen") -> CHATML
                    modelId.startsWith("llama") -> LLAMA3
                    modelId.startsWith("phi") -> PHI
                    modelId.startsWith("gemma") -> GEMMA
                    modelId.startsWith("mistral") -> MISTRAL
                    else -> CHATML
                }
            }
        }
    }
    
    private fun formatMessage(template: ChatTemplate, role: String, content: String): String {
        return when (template) {
            ChatTemplate.CHATML -> "<|im_start|>$role\n$content<|im_end|>\n"
            ChatTemplate.LLAMA3 -> "<|start_header_id|>$role<|end_header_id|>\n\n$content<|eot_id|>"
            ChatTemplate.PHI -> "<|$role|>\n$content<|end|>\n"
            ChatTemplate.GEMMA -> "<start_of_turn>${if (role == "assistant") "model" else role}\n$content<end_of_turn>\n"
            ChatTemplate.MISTRAL -> if (role == "user") "[INST] $content [/INST]" else " $content"
        }
    }
    
    private suspend fun buildPrompt(userInput: String, contextMessages: List<Message>, modelId: String): String {
        Logger.d(Logger.Tags.VM, "buildPrompt: userInput=${userInput.take(30)}...")
        val template = ChatTemplate.fromModelId(modelId)
        val sb = StringBuilder()
        
        val systemContent = buildSystemPrompt()
        sb.append(formatMessage(template, "system", systemContent))
        
        val systemPromptTokens = estimateTokenCount(sb.toString())
        val userInputTokens = estimateTokenCount(userInput)
        val remainingBudget = tokenBudget - systemPromptTokens - userInputTokens
        
        Logger.d(Logger.Tags.VM, "buildPrompt: template=$template, token budget=${tokenBudget}, system=${systemPromptTokens}, userInput=${userInputTokens}, remaining=${remainingBudget}")
        
        var contextTokensUsed = 0
        val limitedContextMessages = contextMessages.takeLastWhile { msg ->
            val msgTokens = estimateTokenCount(msg.content)
            if (contextTokensUsed + msgTokens <= remainingBudget) {
                contextTokensUsed += msgTokens
                true
            } else {
                Logger.d(Logger.Tags.VM, "buildPrompt: dropping message (${msgTokens} tokens) due to budget")
                false
            }
        }
        
        for (msg in limitedContextMessages) {
            val role = when (msg.role) {
                MessageRole.USER -> "user"
                MessageRole.ASSISTANT -> "assistant"
                MessageRole.SYSTEM -> "system"
            }
            sb.append(formatMessage(template, role, msg.content))
        }
        
        sb.append(formatMessage(template, "user", userInput))
        sb.append(formatMessage(template, "assistant", ""))
        
        Logger.d(Logger.Tags.VM, "buildPrompt: completed, template=$template, ${sb.length} chars, ~${estimateTokenCount(sb.toString())} tokens")
        return sb.toString()
    }
    
    private suspend fun buildSystemPrompt(): String {
        val sb = StringBuilder()
        sb.append("你是NeuralMind AI助手，一个运行在本地设备上的智能助手。")
        
        val activeSkillPrompts = try { skillRepository.getActiveSystemPrompts() } catch (e: Exception) { "" }
        if (activeSkillPrompts.isNotBlank()) {
            sb.append(activeSkillPrompts)
        }
        
        sb.append("\n\n【设备操控工具】\n")
        sb.append("你可以使用以下工具来操控手机。当需要执行操作时，在回复中包含工具调用。\n")
        sb.append("格式：[ACTION:工具名]参数[/ACTION]\n\n")
        sb.append("可用工具：\n")
        sb.append("- launch_app: 打开应用，参数为应用名（桌面显示的名字，如微信、抖音），系统会像人一样在桌面找到图标点击。例：[ACTION:launch_app]微信[/ACTION]\n")
        sb.append("- click_text: 点击屏幕上的文字。例：[ACTION:click_text]确定[/ACTION]\n")
        sb.append("- input_text: 输入文字。格式\"提示|内容\"，或直接输入内容。例：[ACTION:input_text]搜索|天气[/ACTION]\n")
        sb.append("- go_back: 返回。例：[ACTION:go_back][/ACTION]\n")
        sb.append("- go_home: 回到主页。例：[ACTION:go_home][/ACTION]\n")
        sb.append("- open_notifications: 打开通知栏\n")
        sb.append("- open_quick_settings: 打开快捷设置\n")
        sb.append("- open_recents: 打开最近任务\n")
        sb.append("- swipe_up/swipe_down/swipe_left/swipe_right: 滑动\n")
        sb.append("- get_screen: 获取当前屏幕内容\n\n")
        sb.append("【工具调用强制规则】\n")
        sb.append("=== ⚠️ 以下规则为强制性，必须严格遵守！===\n")
        sb.append("规则1: 当用户说\"打开\"、\"启动\"、\"运行\"应用时，**必须、强制、无条件**使用launch_app工具！\n")
        sb.append("       例如：用户说\"打开抖音\" → 必须输出：我来帮你打开抖音应用。[ACTION:launch_app]抖音[/ACTION]\n")
        sb.append("       即使记忆中显示之前打开过，也必须重新调用工具！\n")
        sb.append("规则2: 工具调用是**强制性**的，不能跳过！不允许假装调用工具或跳过工具直接回复！\n")
        sb.append("规则3: 只调用完成任务所需的工具，不要调用多余的工具！\n")
        sb.append("规则4: 每个工具只调用一次，不要重复调用相同的工具！\n")
        sb.append("规则5: 调用工具前先用自然语言告诉用户你要做什么\n")
        sb.append("规则6: launch_app参数用应用的中文名，就是桌面上显示的名字\n")
        sb.append("规则7: 一次只调用1个工具，不要调用更多！\n")
        sb.append("规则8: 完成工具调用后，立即停止生成，不要继续输出任何内容！\n")
        sb.append("规则9: 记忆中的对话仅供参考，不能模仿记忆中的回复模式！必须遵守以上工具调用规则！\n")
        
        val activeMemories = try { memoryRepository.getActiveMemoriesSnapshot() } catch (e: Exception) { emptyList() }
        if (activeMemories.isNotEmpty()) {
            sb.append("\n\n【关于用户的记忆（仅供参考）】\n")
            val relevantMemories = activeMemories
                .sortedByDescending { it.importance }
                .take(5)
            relevantMemories.forEach { memory ->
                sb.append("- [${memory.layer.description}] ${memory.content}\n")
            }
            sb.append("\n⚠️ 注意：记忆中的对话仅供参考，不能作为回复模板！必须遵守工具调用规则！\n")
        }
        
        return sb.toString()
    }
    
    fun clearPromptCache() {
        Logger.d(Logger.Tags.VM, "clearPromptCache()")
        llamaEngine.clearPromptCache()
    }
    
    fun selectModel(model: AIModel) {
        Logger.d(Logger.Tags.VM, "selectModel(model=${model.name})")
        viewModelScope.launch {
            try {
                modelRepository.switchModel(model.id)
                llamaEngine.loadModel(model.id)
                llamaEngine.clearPromptCache()
                _currentConversation.value = _currentConversation.value?.copy(model = model.id)
                Logger.i(Logger.Tags.VM, "selectModel success: ${model.name}")
            } catch (e: Exception) {
                Logger.e(Logger.Tags.VM, "selectModel failed: ${model.name}", e)
            }
        }
    }
    
    fun clearError() {
        Logger.d(Logger.Tags.VM, "clearError")
        _uiState.update { it.copy(error = null) }
    }
    
    override fun onCleared() {
        super.onCleared()
        memoryMonitor.stopMonitoring()
        Logger.i(Logger.Tags.VM, "ChatViewModel: memory monitoring stopped")
    }
}

data class ChatUiState(
    val isLoading: Boolean = false,
    val isStreaming: Boolean = false,
    val error: String? = null,
    val inputText: String = ""
)
