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
import com.neuralmind.domain.model.AIModel
import com.neuralmind.domain.model.Conversation
import com.neuralmind.domain.model.Message
import com.neuralmind.domain.model.MessageRole
import com.neuralmind.llama.LlamaEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
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
    private val llamaEngine: LlamaEngine
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
        
        var tempResponse = ""
        
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
            
            llamaEngine.generate(
                prompt = prompt,
                onToken = { token ->
                    tempResponse += token
                    _streamingMessage.value = _streamingMessage.value?.copy(content = tempResponse)
                },
                onComplete = { finalResponse ->
                    viewModelScope.launch {
                        try {
                            chatRepository.sendMessage(
                                conversationId = conversation.id,
                                role = MessageRole.ASSISTANT,
                                content = finalResponse,
                                model = modelId
                            )
                            // Save conversation segment to memory (L1/L2/L3)
                            memoryRepository.saveConversationSegment(userInput, finalResponse, modelId)
                            Logger.i(Logger.Tags.VM, "generateAIResponse: completed, ${finalResponse.length} chars")
                            _streamingMessage.value = null
                            _uiState.update { it.copy(isStreaming = false) }
                        } catch (e: Exception) {
                            Logger.e(Logger.Tags.VM, "generateAIResponse: save response failed", e)
                        }
                    }
                },
                onError = { error ->
                    viewModelScope.launch {
                        Logger.e(Logger.Tags.VM, "generateAIResponse error: $error")
                        _uiState.update { it.copy(isStreaming = false) }
                        _errorEvent.send(error)
                    }
                }
            )
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

