package com.neuralmind.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.neuralmind.domain.model.MessageRole

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val model: String,
    val createdAt: Long,
    val updatedAt: Long,
    val messageCount: Int = 0,
    val isPinned: Boolean = false,
    val isArchived: Boolean = false
)

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val conversationId: Long,
    val role: MessageRole,
    val content: String,
    val timestamp: Long,
    val model: String? = null,
    val tokens: Int? = null
)

@Entity(tableName = "models")
data class ModelEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val description: String,
    val size: Long,
    val parameters: Int,
    val quantization: String,
    val category: String,
    val downloadUrl: String,
    val checksum: String,
    val minRam: Long,
    val minStorage: Long,
    val recommendedRam: Long,
    val supportsGpu: Boolean,
    val supportsNnapi: Boolean,
    val isInstalled: Boolean = false,
    val isDownloading: Boolean = false,
    val downloadProgress: Float = 0f,
    val localPath: String? = null
)

@Entity(tableName = "memories")
data class MemoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val layer: String,
    val content: String,
    val category: String,
    val importance: Int,
    val accessCount: Int = 0,
    val lastAccessed: Long,
    val createdAt: Long,
    val updatedAt: Long,
    val isActive: Boolean = true
)

@Entity(tableName = "skills")
data class SkillEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val description: String,
    val icon: String,
    val category: String,
    val version: String,
    val author: String,
    val permissions: String,
    val isInstalled: Boolean = false,
    val isBuiltIn: Boolean = true,
    val downloadUrl: String? = null,
    val installedSize: Long = 0
)

@Entity(tableName = "automation_rules")
data class AutomationRuleEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val description: String,
    val triggersJson: String,
    val conditionsJson: String,
    val actionsJson: String,
    val isEnabled: Boolean = true,
    val lastTriggered: Long? = null,
    val triggerCount: Int = 0,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(tableName = "tool_modules")
data class ToolModuleEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val description: String,
    val version: String,
    val category: String,
    val downloadSize: Long,
    val installedSize: Long,
    val icon: String,
    val downloadUrl: String,
    val isInstalled: Boolean = false,
    val isDownloading: Boolean = false,
    val downloadProgress: Float = 0f,
    val localPath: String? = null
)
