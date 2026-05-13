package com.neuralmind.data.repository

import com.neuralmind.data.local.db.dao.ConversationDao
import com.neuralmind.data.local.db.dao.MessageDao
import com.neuralmind.data.local.db.entity.ConversationEntity
import com.neuralmind.data.local.db.entity.MessageEntity
import com.neuralmind.domain.model.Conversation
import com.neuralmind.domain.model.Message
import com.neuralmind.domain.model.MessageRole
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class ChatRepository @Inject constructor(
    private val conversationDao: ConversationDao,
    private val messageDao: MessageDao
) {
    fun getAllConversations(): Flow<List<Conversation>> {
        return conversationDao.getAllConversations().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    fun getActiveConversations(): Flow<List<Conversation>> {
        return conversationDao.getActiveConversations().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    suspend fun getConversationById(id: Long): Conversation? {
        return conversationDao.getConversationById(id)?.toDomain()
    }

    suspend fun createConversation(title: String, model: String): Long {
        val entity = ConversationEntity(
            title = title,
            model = model,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        return conversationDao.insert(entity)
    }

    suspend fun updateConversation(conversation: Conversation) {
        conversationDao.update(conversation.toEntity())
    }

    suspend fun deleteConversation(conversation: Conversation) {
        conversationDao.delete(conversation.toEntity())
        messageDao.deleteByConversation(conversation.id)
    }

    suspend fun pinConversation(id: Long, isPinned: Boolean) {
        conversationDao.setPinned(id, isPinned)
    }

    suspend fun archiveConversation(id: Long, isArchived: Boolean) {
        conversationDao.setArchived(id, isArchived)
    }

    fun getMessagesByConversation(conversationId: Long): Flow<List<Message>> {
        return messageDao.getMessagesByConversation(conversationId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    suspend fun sendMessage(conversationId: Long, role: MessageRole, content: String, model: String? = null): Long {
        val entity = MessageEntity(
            conversationId = conversationId,
            role = role,
            content = content,
            timestamp = System.currentTimeMillis(),
            model = model
        )
        val messageId = messageDao.insert(entity)
        conversationDao.incrementMessageCount(conversationId)
        return messageId
    }

    suspend fun deleteMessage(message: Message) {
        messageDao.delete(message.toEntity())
    }

    suspend fun insertDefaultData() {
        // 默认数据为空，因为初始对话由用户手动创建
        // 如果需要创建默认欢迎对话，可以在这里添加
    }

    private fun ConversationEntity.toDomain() = Conversation(
        id = id,
        title = title,
        model = model,
        createdAt = createdAt,
        updatedAt = updatedAt,
        messageCount = messageCount,
        isPinned = isPinned,
        isArchived = isArchived
    )

    private fun Conversation.toEntity() = ConversationEntity(
        id = id,
        title = title,
        model = model,
        createdAt = createdAt,
        updatedAt = updatedAt,
        messageCount = messageCount,
        isPinned = isPinned,
        isArchived = isArchived
    )

    private fun MessageEntity.toDomain() = Message(
        id = id,
        conversationId = conversationId,
        role = role,
        content = content,
        timestamp = timestamp,
        model = model,
        tokens = tokens
    )

    private fun Message.toEntity() = MessageEntity(
        id = id,
        conversationId = conversationId,
        role = role,
        content = content,
        timestamp = timestamp,
        model = model,
        tokens = tokens
    )
}
