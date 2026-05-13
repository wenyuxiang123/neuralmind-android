package com.neuralmind.data.repository

import com.neuralmind.core.Logger
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
        Logger.d(Logger.Tags.REPO, "getAllConversations() called")
        return conversationDao.getAllConversations().map { entities ->
            entities.map { it.toDomain() }
        }
    }
    
    fun getActiveConversations(): Flow<List<Conversation>> {
        Logger.d(Logger.Tags.REPO, "getActiveConversations() called")
        return conversationDao.getActiveConversations().map { entities ->
            entities.map { it.toDomain() }
        }
    }
    
    suspend fun getConversationById(id: Long): Conversation? {
        Logger.d(Logger.Tags.REPO, "getConversationById(id=$id)")
        return conversationDao.getConversationById(id)?.toDomain()
    }
    
    suspend fun createConversation(title: String, model: String): Long {
        Logger.d(Logger.Tags.REPO, "createConversation(title=$title, model=$model)")
        return try {
            val entity = ConversationEntity(
                title = title,
                model = model,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
            val id = conversationDao.insert(entity)
            Logger.i(Logger.Tags.REPO, "createConversation success: id=$id, title=$title")
            id
        } catch (e: Exception) {
            Logger.e(Logger.Tags.REPO, "createConversation failed: title=$title", e)
            throw e
        }
    }
    
    suspend fun updateConversation(conversation: Conversation) {
        Logger.d(Logger.Tags.REPO, "updateConversation(id=${conversation.id})")
        try {
            conversationDao.update(conversation.toEntity())
            Logger.i(Logger.Tags.REPO, "updateConversation success: id=${conversation.id}")
        } catch (e: Exception) {
            Logger.e(Logger.Tags.REPO, "updateConversation failed: id=${conversation.id}", e)
        }
    }
    
    suspend fun deleteConversation(conversation: Conversation) {
        Logger.d(Logger.Tags.REPO, "deleteConversation(id=${conversation.id})")
        try {
            conversationDao.delete(conversation.toEntity())
            messageDao.deleteByConversation(conversation.id)
            Logger.i(Logger.Tags.REPO, "deleteConversation success: id=${conversation.id}")
        } catch (e: Exception) {
            Logger.e(Logger.Tags.REPO, "deleteConversation failed: id=${conversation.id}", e)
        }
    }
    
    suspend fun pinConversation(id: Long, isPinned: Boolean) {
        Logger.d(Logger.Tags.REPO, "pinConversation(id=$id, isPinned=$isPinned)")
        try {
            conversationDao.setPinned(id, isPinned)
            Logger.i(Logger.Tags.REPO, "pinConversation success: id=$id")
        } catch (e: Exception) {
            Logger.e(Logger.Tags.REPO, "pinConversation failed: id=$id", e)
        }
    }
    
    suspend fun archiveConversation(id: Long, isArchived: Boolean) {
        Logger.d(Logger.Tags.REPO, "archiveConversation(id=$id, isArchived=$isArchived)")
        try {
            conversationDao.setArchived(id, isArchived)
            Logger.i(Logger.Tags.REPO, "archiveConversation success: id=$id")
        } catch (e: Exception) {
            Logger.e(Logger.Tags.REPO, "archiveConversation failed: id=$id", e)
        }
    }
    
    fun getMessagesByConversation(conversationId: Long): Flow<List<Message>> {
        Logger.d(Logger.Tags.REPO, "getMessagesByConversation(conversationId=$conversationId)")
        return messageDao.getMessagesByConversation(conversationId).map { entities ->
            entities.map { it.toDomain() }
        }
    }
    
    suspend fun sendMessage(conversationId: Long, role: MessageRole, content: String, model: String? = null): Long {
        Logger.d(Logger.Tags.REPO, "sendMessage(conversationId=$conversationId, role=${role.name})")
        return try {
            val entity = MessageEntity(
                conversationId = conversationId,
                role = role,
                content = content,
                timestamp = System.currentTimeMillis(),
                model = model
            )
            val messageId = messageDao.insert(entity)
            conversationDao.incrementMessageCount(conversationId)
            Logger.i(Logger.Tags.REPO, "sendMessage success: messageId=$messageId")
            messageId
        } catch (e: Exception) {
            Logger.e(Logger.Tags.REPO, "sendMessage failed: conversationId=$conversationId", e)
            throw e
        }
    }
    
    suspend fun deleteMessage(message: Message) {
        Logger.d(Logger.Tags.REPO, "deleteMessage(id=${message.id})")
        try {
            messageDao.delete(message.toEntity())
            Logger.i(Logger.Tags.REPO, "deleteMessage success: id=${message.id}")
        } catch (e: Exception) {
            Logger.e(Logger.Tags.REPO, "deleteMessage failed: id=${message.id}", e)
        }
    }
    
    suspend fun insertDefaultData() {
        Logger.d(Logger.Tags.REPO, "insertDefaultData() called")
        // 默认数据为空，因为初始对话由用户手动创建
        // 如果需要创建默认欢迎对话，可以在这里添加
        Logger.i(Logger.Tags.REPO, "insertDefaultData completed")
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
