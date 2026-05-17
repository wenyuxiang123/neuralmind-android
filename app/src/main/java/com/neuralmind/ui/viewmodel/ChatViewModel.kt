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
    private val deviceToolExecutor: DeviceToolExecutor
) : ViewModel() {
    
    // Memory monitor for detecting high memory pressure
    private val memoryMonitor = MemoryMonitor(context)
    
    init {
        // Start memory monitoring
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
    
    // Error event channel for UI to collect
    private val _errorEvent = Channel<String>(Channel.BUFFERED)
    val errorEvent = _errorEvent.receiveAsFlow()
    
    // Reserve tokens for generation output
    private val reservedOutputTokens = 512
    // Dynamic token budget using actual n_ctx from C++ engine
    // 512 reserved = safer margin for estimation error + generation space
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
                
                // Get context messages WITHOUT the current user message (we'll add it separately)
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
            // Try currentModel first, fallback to conversation's model
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
            
            // Check memory pressure before inference
            if (memoryMonitor.isMemoryPressure()) {
                Logger.w(Logger.Tags.VM, "Memory pressure detected (${memoryMonitor.getMemoryUsagePercent().toInt()}%), clearing KV cache and L1 memories")
                llamaEngine.clearKvRange()
                memoryRepository.clearLayerMemories(com.neuralmind.domain.model.MemoryLayer.L1_WORKING)
            }
            
            val prompt = buildPrompt(userInput, contextMessages, modelId)
            
            // 使用带工具执行循环的生成
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
    
    /**
     * 带工具执行循环的 AI 生成
     * 最多执行 MAX_TOOL_ITERATIONS 轮工具调用
     */
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
        
        while (iteration < maxIterations) {
            iteration++
            var tempResponse = ""
            
            _streamingMessage.value = Message(
                id = -1,
                conversationId = conversation.id,
                role = MessageRole.ASSISTANT,
                content = if (allDisplayText.isNotBlank()) allDisplayText else "",
                timestamp = System.currentTimeMillis(),
                model = modelId
            )
            
            // 用 CompletableDeferred 等待生成完成
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
            
            // 等待生成完成
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
                return
            }
            
            // 解析工具调用
            val (cleanText, toolCalls) = deviceToolExecutor.parseToolCalls(response)
            
            if (toolCalls.isEmpty()) {
                // 没有工具调用，保存并结束
                allDisplayText += cleanText
                chatRepository.sendMessage(
                    conversationId = conversation.id,
                    role = MessageRole.ASSISTANT,
                    content = allDisplayText,
                    model = modelId
                )
                memoryRepository.saveConversationSegment(userInput, allDisplayText, modelId)
                Logger.i(Logger.Tags.VM, "generateWithToolLoop: completed, ${allDisplayText.length} chars")
                _streamingMessage.value = null
                _uiState.update { it.copy(isStreaming = false) }
                return
            }
            
            // 有工具调用
            Logger.i(Logger.Tags.VM, "Found ${toolCalls.size} tool calls, executing...")
            
            // 保存 AI 的文字部分
            if (cleanText.isNotBlank()) {
                allDisplayText += cleanText + "\n"
            }
            
            // 执行工具
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
            
            // 更新流式消息
            _streamingMessage.value = _streamingMessage.value?.copy(content = allDisplayText)
            
            // 构建下一轮 prompt
            val template = ChatTemplate.fromModelId(modelId)
            currentPrompt = buildToolResultPrompt(template, currentPrompt, response, toolResults.toString())
            
            // 清除 KV 缓存，因为我们要发送新的 prompt
            llamaEngine.clearPromptCache()
        }
        
        // 超过最大迭代次数，保存已有结果
        Logger.w(Logger.Tags.VM, "generateWithToolLoop: max iterations ($maxIterations) reached")
        chatRepository.sendMessage(
            conversationId = conversation.id,
            role = MessageRole.ASSISTANT,
            content = allDisplayText,
            model = modelId
        )
        _streamingMessage.value = null
        _uiState.update { it.copy(isStreaming = false) }
    }
    
    /**
     * 构建包含工具执行结果的 prompt，让 AI 继续对话
     */
    private fun buildToolResultPrompt(
        template: ChatTemplate,
        originalPrompt: String,
        aiResponse: String,
        toolResult: String
    ): String {
        val sb = StringBuilder()
        
        // 原始 prompt 中已经包含了历史对话和用户输入
        // 我们需要追加 AI 的回复 + 工具结果 + 新的 assistant 前缀
        sb.append(originalPrompt)
        
        // 追加 AI 的原始回复（包含工具调用）
        when (template) {
            ChatTemplate.CHATML -> {
                sb.append(aiResponse)
                sb.append("<|im_end|>\n")
                // 工具结果作为 system 消息
                sb.append("<|im_start|>system\n")
                sb.append(toolResult)
                sb.append("<|im_end|>\n")
                sb.append("<|im_start|>assistant\n")
            }
            ChatTemplate.LLAMA3 -> {
                sb.append(aiResponse)
                sb.append("<|eot_id|>")
                // 工具结果作为 system 消息
                sb.append("<|start_header_id|>system<|end_header_id|>\n\n")
                sb.append(toolResult)
                sb.append("<|eot_id|>")
                sb.append("<|start_header_id|>assistant<|end_header_id|>\n\n")
            }
            ChatTemplate.PHI -> {
                sb.append(aiResponse)
                sb.append("<|end|>\n")
                sb.append("<|system|>\n")
                sb.append(toolResult)
                sb.append("<|end|>\n")
                sb.append("<|assistant|>\n")
            }
            ChatTemplate.GEMMA -> {
                sb.append(aiResponse)
                sb.append("<end_of_turn>\n")
                sb.append("<start_of_turn>user\n")
                sb.append(toolResult)
                sb.append("<end_of_turn>\n")
                sb.append("<start_of_turn>model\n")
            }
            ChatTemplate.MISTRAL -> {
                sb.append(aiResponse)
                sb.append(" ")
                sb.append("[INST] $toolResult [/INST]")
            }
        }
        
        return sb.toString()
    }
    
    /**
     * Estimate token count from text.
     * Chinese: ~1.5 tokens per character
     * English: ~1 token per word (space-separated)
     */
    private fun estimateTokenCount(text: String): Int {
        // Conservative estimation to prevent prompt overflow
        // CJK/special tokens: ~2 tokens each, ASCII: ~0.25 tokens each (4 chars/token)
        var cjkCount = 0
        var asciiCount = 0
        for (char in text) {
            if (char.code > 127) cjkCount++ else asciiCount++
        }
        val estimate = (cjkCount * 2 + asciiCount * 0.25).toInt().coerceAtLeast(1)
        // Add 20% safety margin for template tokens and estimation error
        return (estimate * 1.2).toInt()
    }
    
    /**
     * Build prompt using ChatML format for LLM inference, with memory context and skill prompts injection.
     * With token budget control to prevent context overflow.
     * FIX: contextMessages does NOT include the current user message, we add it separately.
     */    /**
     * Chat template format for different model families.
     */
    private enum class ChatTemplate(val id: String) {
        CHATML("chatml"),       // Qwen2.5
        LLAMA3("llama3"),       // Llama 3.x
        PHI("phi"),             // Phi-3.5
        GEMMA("gemma"),         // Gemma
        MISTRAL("mistral");     // Mistral

        companion object {
            fun fromModelId(modelId: String): ChatTemplate {
                return when {
                    modelId.startsWith("qwen") -> CHATML
                    modelId.startsWith("llama") -> LLAMA3
                    modelId.startsWith("phi") -> PHI
                    modelId.startsWith("gemma") -> GEMMA
                    modelId.startsWith("mistral") -> MISTRAL
                    else -> CHATML  // default fallback
                }
            }
        }
    }


    private suspend fun buildPrompt(userInput: String, contextMessages: List<Message>, modelId: String): String {
        Logger.d(Logger.Tags.VM, "buildPrompt: userInput=${userInput.take(30)}...")
        val template = ChatTemplate.fromModelId(modelId)
        val sb = StringBuilder()
        
        // System prompt content
        val systemContent = StringBuilder()
        systemContent.append("你是NeuralMind AI助手，一个运行在本地设备上的智能助手。")
        
        // Inject active skill prompts first
        val activeSkillPrompts = try { skillRepository.getActiveSystemPrompts() } catch (e: Exception) { "" }
        if (activeSkillPrompts.isNotBlank()) {
            systemContent.append(activeSkillPrompts)
        }
        
        // Inject active memory context
        val activeMemories = memoryRepository.getActiveMemoriesSnapshot()
        if (activeMemories.isNotEmpty()) {
            systemContent.append("\n\n【关于用户的记忆】\n")
            val relevantMemories = activeMemories
                .sortedByDescending { it.importance }
                .take(5)
            relevantMemories.forEach { memory ->
                systemContent.append("- [${memory.layer.description}] ${memory.content}\n")
            }
        }
        
        // 注入设备工具定义
        systemContent.append("\n\n【设备操控工具】\n")
        systemContent.append("你可以使用以下工具来操控手机。当需要执行操作时，在回复中包含工具调用。\n")
        systemContent.append("格式：[ACTION:工具名]参数[/ACTION]\n\n")
        systemContent.append("可用工具：\n")
        systemContent.append("- launch_app: 打开应用。参数为应用名或包名。例：[ACTION:launch_app]微信[/ACTION]\n")
        systemContent.append("- click_text: 点击屏幕上的文字。例：[ACTION:click_text]确定[/ACTION]\n")
        systemContent.append("- input_text: 输入文字。格式\"提示|内容\"，或直接输入内容。例：[ACTION:input_text]搜索|天气[/ACTION]\n")
        systemContent.append("- go_back: 返回。例：[ACTION:go_back][/ACTION]\n")
        systemContent.append("- go_home: 回到主页。例：[ACTION:go_home][/ACTION]\n")
        systemContent.append("- open_notifications: 打开通知栏\n")
        systemContent.append("- open_quick_settings: 打开快捷设置\n")
        systemContent.append("- open_recents: 打开最近任务\n")
        systemContent.append("- swipe_up/swipe_down/swipe_left/swipe_right: 滑动\n")
        systemContent.append("- get_screen: 获取当前屏幕内容\n")
        systemContent.append("- search_app: 搜索应用包名。例：[ACTION:search_app]微信[/ACTION]\n\n")
        systemContent.append("重要规则：\n")
        systemContent.append("1. 需要执行操作时才使用工具，纯对话不需要\n")
        systemContent.append("2. 调用工具前先用自然语言告诉用户你要做什么\n")
        systemContent.append("3. 不确定包名时直接用中文名，系统会自动查找\n")
        systemContent.append("4. 一次可以调用多个工具，每个单独一行\n")
        systemContent.append("5. 看不到屏幕时先用get_screen查看\n")
        
        // Format based on template
        when (template) {
            ChatTemplate.CHATML -> {
                sb.append("<|im_start|>system\n")
                sb.append(systemContent.toString())
                sb.append("<|im_end|>\n")
            }
            ChatTemplate.LLAMA3 -> {
                sb.append("<|start_header_id|>system<|end_header_id|>\n\n")
                sb.append(systemContent.toString())
                sb.append("<|eot_id|>")
            }
            ChatTemplate.PHI -> {
                sb.append("<|system|>\n")
                sb.append(systemContent.toString())
                sb.append("<|end|>\n")
            }
            ChatTemplate.GEMMA -> {
                sb.append("<start_of_turn>user\n")
                // Gemma doesn't have a separate system role, prepend to first user message
                sb.append(systemContent.toString())
                sb.append("\n\n")
            }
            ChatTemplate.MISTRAL -> {
                sb.append("[INST] ")
                sb.append(systemContent.toString())
                sb.append("\n\n")
            }
        }
        
        // Calculate token budget for context messages
        val systemPromptTokens = estimateTokenCount(sb.toString())
        val userInputTokens = estimateTokenCount(userInput)
        val remainingBudget = tokenBudget - systemPromptTokens - userInputTokens
        
        Logger.d(Logger.Tags.VM, "buildPrompt: template=$template, token budget=${tokenBudget}, system=${systemPromptTokens}, userInput=${userInputTokens}, remaining=${remainingBudget}")
        
        // Context messages with token budget control
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
            when (template) {
                ChatTemplate.CHATML -> {
                    val role = when (msg.role) {
                        MessageRole.USER -> "user"
                        MessageRole.ASSISTANT -> "assistant"
                        MessageRole.SYSTEM -> "system"
                    }
                    sb.append("<|im_start|>$role\n")
                    sb.append(msg.content)
                    sb.append("<|im_end|>\n")
                }
                ChatTemplate.LLAMA3 -> {
                    val role = when (msg.role) {
                        MessageRole.USER -> "user"
                        MessageRole.ASSISTANT -> "assistant"
                        MessageRole.SYSTEM -> "system"
                    }
                    sb.append("<|start_header_id|>$role<|end_header_id|>\n\n")
                    sb.append(msg.content)
                    sb.append("<|eot_id|>")
                }
                ChatTemplate.PHI -> {
                    val role = when (msg.role) {
                        MessageRole.USER -> "user"
                        MessageRole.ASSISTANT -> "assistant"
                        MessageRole.SYSTEM -> "system"
                    }
                    sb.append("<|$role|>\n")
                    sb.append(msg.content)
                    sb.append("<|end|>\n")
                }
                ChatTemplate.GEMMA -> {
                    val role = when (msg.role) {
                        MessageRole.USER -> "user"
                        MessageRole.ASSISTANT -> "model"
                        MessageRole.SYSTEM -> "user"
                    }
                    sb.append("<start_of_turn>$role\n")
                    sb.append(msg.content)
                    sb.append("<end_of_turn>\n")
                }
                ChatTemplate.MISTRAL -> {
                    if (msg.role == MessageRole.USER) {
                        sb.append("[INST] ${msg.content} [/INST]")
                    } else {
                        sb.append(" ${msg.content}")
                    }
                }
            }
        }
        
        // Current user input + assistant prefix
        when (template) {
            ChatTemplate.CHATML -> {
                sb.append("<|im_start|>user\n")
                sb.append(userInput)
                sb.append("<|im_end|>\n")
                sb.append("<|im_start|>assistant\n")
            }
            ChatTemplate.LLAMA3 -> {
                sb.append("<|start_header_id|>user<|end_header_id|>\n\n")
                sb.append(userInput)
                sb.append("<|eot_id|><|start_header_id|>assistant<|end_header_id|>\n\n")
            }
            ChatTemplate.PHI -> {
                sb.append("<|user|>\n")
                sb.append(userInput)
                sb.append("<|end|>\n")
                sb.append("<|assistant|>\n")
            }
            ChatTemplate.GEMMA -> {
                sb.append("<start_of_turn>user\n")
                sb.append(userInput)
                sb.append("<end_of_turn>\n<start_of_turn>model\n")
            }
            ChatTemplate.MISTRAL -> {
                sb.append("[INST] $userInput [/INST]")
            }
        }
        
        Logger.d(Logger.Tags.VM, "buildPrompt: completed, template=$template, ${sb.length} chars, ~${estimateTokenCount(sb.toString())} tokens")
        return sb.toString()
    }
    
    /**
     * Clear prompt cache when switching conversations or resetting context.
     */
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
                // Clear prompt cache when switching models (context is incompatible)
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

