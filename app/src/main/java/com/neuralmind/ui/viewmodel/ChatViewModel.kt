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
import com.neuralmind.domain.model.MemoryLayer
import com.neuralmind.llama.LlamaEngine
import com.neuralmind.skills.SkillCallManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val modelRepository: ModelRepository,
    private val memoryRepository: MemoryRepository,
    private val skillRepository: SkillRepository,
    private val llamaEngine: LlamaEngine,
    private val skillCallManager: SkillCallManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    val conversations = chatRepository.getActiveConversations()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val installedModels = modelRepository.getInstalledModels()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _currentConversation = MutableStateFlow<Conversation?>(null)
    val currentConversation: StateFlow<Conversation?> = _currentConversation.asStateFlow()

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()

    private val _streamingMessage = MutableStateFlow<Message?>(null)
    val streamingMessage: StateFlow<Message?> = _streamingMessage.asStateFlow()

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
            _uiState.update { it.copy(isLoading = true, isStreaming = false) }

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

        val detectedSkills = skillCallManager.detectSkillsFromInput(userInput)
        
        if (detectedSkills.isNotEmpty()) {
            val skillResults = skillCallManager.callDetectedSkills(userInput)
            
            if (skillResults.any { it.success }) {
                val successfulResults = skillResults.filter { it.success }
                val combinedResult = successfulResults.joinToString("\n\n") { it.result }
                
                chatRepository.sendMessage(
                    conversationId = conversation.id,
                    role = MessageRole.ASSISTANT,
                    content = combinedResult,
                    model = modelId
                )
                _streamingMessage.value = null
                _uiState.update { it.copy(isStreaming = false) }
                return
            }
        }

        var tempResponse = ""
        
        val modelLoaded = modelRepository.getCurrentModel().value?.let { model ->
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
                        _uiState.update { it.copy(isStreaming = false, error = error) }
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
        }
    }

    private fun buildPrompt(userInput: String, contextMessages: List<Message>): String {
        val context = contextMessages.joinToString("\n") { msg ->
            when (msg.role) {
                MessageRole.USER -> "User: ${msg.content}"
                MessageRole.ASSISTANT -> "Assistant: ${msg.content}"
                MessageRole.SYSTEM -> "System: ${msg.content}"
            }
        }
        return if (context.isNotEmpty()) {
            "$context\nUser: $userInput\nAssistant:"
        } else {
            "User: $userInput\nAssistant:"
        }
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
    val error: String? = null
)
