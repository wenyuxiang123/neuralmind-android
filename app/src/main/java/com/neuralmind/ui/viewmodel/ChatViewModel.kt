package com.neuralmind.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
        _uiState.update { it.copy(inputText = text) }
    }
    
    fun createConversation(title: String, model: String, onCreated: (Long) -> Unit) {
        viewModelScope.launch {
            val id = chatRepository.createConversation(title, model)
            onCreated(id)
        }
    }
    
    fun loadConversation(conversationId: Long) {
        viewModelScope.launch {
            val conversation = chatRepository.getConversationById(conversationId)
            _currentConversation.value = conversation
            conversation?.let { conv ->
                chatRepository.getMessagesByConversation(conv.id).collect { msgs ->
                    _messages.value = msgs
                }
            }
        }
    }
    
    fun sendMessage(content: String) {
        val conversation = _currentConversation.value ?: return
        
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, isStreaming = false, inputText = "") }
            
            val userMessageId = chatRepository.sendMessage(
                conversationId = conversation.id,
                role = MessageRole.USER,
                content = content
            )
            
            memoryRepository.activateMemoryFromUserInput(content)
            
            _uiState.update { it.copy(isLoading = false, isStreaming = true) }
            
            val contextMessages = _messages.value.takeLast(10)
            generateAIResponse(conversation, content, contextMessages)
        }
    }
    
    private suspend fun generateAIResponse(
        conversation: Conversation,
        userInput: String,
        contextMessages: List<Message>
    ) {
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
        
        val modelLoaded = modelRepository.currentModel.value?.let { model ->
            llamaEngine.loadModel(model.id)
        } ?: false
        
        if (modelLoaded) {
            val prompt = buildPrompt(userInput, contextMessages)
            
            llamaEngine.generate(
                prompt = prompt,
                onToken = { token ->
                    tempResponse += token
                    _streamingMessage.value = _streamingMessage.value?.copy(content = tempResponse)
                },
                onComplete = { finalResponse ->
                    viewModelScope.launch {
                        chatRepository.sendMessage(
                            conversationId = conversation.id,
                            role = MessageRole.ASSISTANT,
                            content = finalResponse,
                            model = modelId
                        )
                        _streamingMessage.value = null
                        _uiState.update { it.copy(isStreaming = false) }
                    }
                },
                onError = { error ->
                    viewModelScope.launch {
                        _uiState.update { it.copy(isStreaming = false) }
                        _errorEvent.send(error)
                    }
                }
            )
        } else {
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
     * ChatML format: <|im_start|>role
content<|im_end|>
     * 
     * Context injection strategy:
     * - Get currently active memories via snapshot
     * - Get all active skill system prompts
     * - Sort memories by importance (descending)
     * - Limit to top 10 memories to avoid context overflow
     * - Inject into system prompt with clear formatting
     * 
     * This format is compatible with:
     * - LLaMA 3.x models
     * - Qwen models
     * - Most modern chat-tuned models
     * - llama.cpp tokenization
     */
    private suspend fun buildPrompt(userInput: String, contextMessages: List<Message>): String {
        val sb = StringBuilder()
        
        // System prompt with memory and skill context
        sb.append("<|im_start|>system
")
        sb.append("你是NeuralMind AI助手，一个运行在本地设备上的智能助手。")
        
        // Inject active skill prompts first
        val activeSkillPrompts = skillRepository.getActiveSystemPrompts()
        if (activeSkillPrompts.isNotBlank()) {
            sb.append(activeSkillPrompts)
        }
        
        // Inject active memory context
        val activeMemories = memoryRepository.getActiveMemoriesSnapshot()
        if (activeMemories.isNotEmpty()) {
            sb.append("

【关于用户的记忆】
")
            
            // Sort by importance and limit to 10 most important memories
            val relevantMemories = activeMemories
                .sortedByDescending { it.importance }
                .take(10)
            
            relevantMemories.forEach { memory ->
                sb.append("- [${memory.layer.description}] ${memory.content}
")
            }
        }
        
        sb.append("<|im_end|>
")
        
        // Context messages (limited to last 10 to save context)
        for (msg in contextMessages) {
            val role = when (msg.role) {
                MessageRole.USER -> "user"
                MessageRole.ASSISTANT -> "assistant"
                MessageRole.SYSTEM -> "system"
            }
            sb.append("<|im_start|>$role
")
            sb.append(msg.content)
            sb.append("<|im_end|>
")
        }
        
        // Current user input
        sb.append("<|im_start|>user
")
        sb.append(userInput)
        sb.append("<|im_end|>
")
        
        // Assistant prefix for generation
        sb.append("<|im_start|>assistant
")
        
        return sb.toString()
    }
    
    fun selectModel(model: AIModel) {
        viewModelScope.launch {
            modelRepository.switchModel(model.id)
            llamaEngine.loadModel(model.id)
            _currentConversation.value = _currentConversation.value?.copy(model = model.id)
        }
    }
    
    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}

data class ChatUiState(
    val isLoading: Boolean = false,
    val isStreaming: Boolean = false,
    val error: String? = null,
    val inputText: String = ""
)
