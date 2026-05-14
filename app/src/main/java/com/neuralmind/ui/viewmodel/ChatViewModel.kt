package com.neuralmind.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
    private val chatRepository: ChatRepository,
    private val modelRepository: ModelRepository,
    private val memoryRepository: MemoryRepository,
    private val skillRepository: SkillRepository,
    private val llamaEngine: LlamaEngine
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()
    
    val conversations = chatRepository.getActiveConversations()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    
    val installedModels = modelRepository.getInstalledModels()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    
    val allModels = modelRepository.getAllModels()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    
    private val _currentConversation = MutableStateFlow<Conversation?>(null)
    val currentConversation: StateFlow<Conversation?> = _currentConversation.asStateFlow()
    
    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()
    
    private val _streamingMessage = MutableStateFlow<Message?>(null)
    val streamingMessage: StateFlow<Message?> = _streamingMessage.asStateFlow()
    
    // Error event channel for UI to collect
    private val _errorEvent = Channel<String>(Channel.BUFFERED)
    val errorEvent = _errorEvent.receiveAsFlow()
    
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
                
                val contextMessages = _messages.value.takeLast(10)
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
            modelRepository.currentModel.value?.let { model ->
                Logger.d(Logger.Tags.VM, "generateAIResponse: loading model ${model.id}")
                llamaEngine.loadModel(model.id)
            } ?: false
        }
        
        if (modelLoaded) {
            Logger.d(Logger.Tags.VM, "generateAIResponse: model loaded, starting inference")
            val prompt = buildPrompt(userInput, contextMessages)
            
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
     * Build prompt using ChatML format for LLM inference, with memory context and skill prompts injection.
     */
    private suspend fun buildPrompt(userInput: String, contextMessages: List<Message>): String {
        Logger.d(Logger.Tags.VM, "buildPrompt: userInput=${userInput.take(30)}...")
        val sb = StringBuilder()
        
        // System prompt with memory and skill context
        sb.append("<|im_start|>system\n")
        sb.append("你是NeuralMind AI助手，一个运行在本地设备上的智能助手。")
        
        // Inject active skill prompts first
        val activeSkillPrompts = try { skillRepository.getActiveSystemPrompts() } catch (e: Exception) { "" }
        if (activeSkillPrompts.isNotBlank()) {
            sb.append(activeSkillPrompts)
        }
        
        // Inject active memory context
        val activeMemories = memoryRepository.getActiveMemoriesSnapshot()
        if (activeMemories.isNotEmpty()) {
            sb.append("\n\n【关于用户的记忆】\n")
            
            // Sort by importance and limit to 10 most important memories
            val relevantMemories = activeMemories
                .sortedByDescending { it.importance }
                .take(10)
            
            relevantMemories.forEach { memory ->
                sb.append("- [${memory.layer.description}] ${memory.content}\n")
            }
        }
        
        sb.append("<|im_end|>\n")
        
        // Context messages (limited to last 10 to save context)
        for (msg in contextMessages) {
            val role = when (msg.role) {
                MessageRole.USER -> "user"
                MessageRole.ASSISTANT -> "assistant"
                MessageRole.SYSTEM -> "system"
            }
            sb.append("<|im_start|>$role\n")
            sb.append(msg.content)
            sb.append("<|im_end|>\n")
        }
        
        // Current user input
        sb.append("<|im_start|>user\n")
        sb.append(userInput)
        sb.append("<|im_end|>\n")
        
        // Assistant prefix for generation
        sb.append("<|im_start|>assistant\n")
        
        Logger.d(Logger.Tags.VM, "buildPrompt: completed, ${sb.length} chars")
        return sb.toString()
    }
    
    fun selectModel(model: AIModel) {
        Logger.d(Logger.Tags.VM, "selectModel(model=${model.name})")
        viewModelScope.launch {
            try {
                modelRepository.switchModel(model.id)
                llamaEngine.loadModel(model.id)
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
}

data class ChatUiState(
    val isLoading: Boolean = false,
    val isStreaming: Boolean = false,
    val error: String? = null,
    val inputText: String = ""
)
