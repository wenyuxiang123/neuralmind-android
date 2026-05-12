package com.neuralmind.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neuralmind.data.repository.ChatRepository
import com.neuralmind.data.repository.ModelRepository
import com.neuralmind.domain.model.AIModel
import com.neuralmind.domain.model.Conversation
import com.neuralmind.domain.model.Message
import com.neuralmind.domain.model.MessageRole
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val modelRepository: ModelRepository
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
            _uiState.update { it.copy(isLoading = true) }

            chatRepository.sendMessage(
                conversationId = conversation.id,
                role = MessageRole.USER,
                content = content
            )

            _uiState.update { it.copy(isLoading = false) }
        }
    }

    fun selectModel(model: AIModel) {
        _currentConversation.value = _currentConversation.value?.copy(model = model.id)
    }
}

data class ChatUiState(
    val isLoading: Boolean = false,
    val error: String? = null
)
