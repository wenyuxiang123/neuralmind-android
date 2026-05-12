package com.neuralmind.domain.usecase.chat

import com.neuralmind.data.repository.ChatRepository
import com.neuralmind.domain.model.Conversation
import com.neuralmind.domain.model.Message
import com.neuralmind.domain.model.MessageRole
import javax.inject.Inject

class GetConversationsUseCase @Inject constructor(
    private val repository: ChatRepository
) {
    operator fun invoke() = repository.getActiveConversations()
}

class GetMessagesUseCase @Inject constructor(
    private val repository: ChatRepository
) {
    operator fun invoke(conversationId: Long) = repository.getMessagesByConversation(conversationId)
}

class CreateConversationUseCase @Inject constructor(
    private val repository: ChatRepository
) {
    suspend operator fun invoke(
        title: String,
        model: String
    ): Long = repository.createConversation(title, model)
}

class SendMessageUseCase @Inject constructor(
    private val repository: ChatRepository
) {
    suspend operator fun invoke(
        conversationId: Long,
        content: String,
        role: MessageRole = MessageRole.USER,
        model: String? = null
    ): Long = repository.sendMessage(conversationId, role, content, model)
}

class UpdateConversationUseCase @Inject constructor(
    private val repository: ChatRepository
) {
    suspend operator fun invoke(conversation: Conversation) = repository.updateConversation(conversation)
}

class DeleteConversationUseCase @Inject constructor(
    private val repository: ChatRepository
) {
    suspend operator fun invoke(conversation: Conversation) = repository.deleteConversation(conversation)
}
