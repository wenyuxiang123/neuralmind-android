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
    val conversationId: Long = 0,  // 0 means global/shared memory across conversations
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
    val detailedDescription: String = "",
    val icon: String,
    val category: String,
    val version: String,
    val author: String,
    val permissions: String = "[]",
    val isInstalled: Boolean = false,
    val isBuiltIn: Boolean = true,
    val downloadUrl: String? = null,
    val installedSize: Long = 0,
    val systemPrompt: String = "",
    val scenarios: String = "",
    val isActive: Boolean = false,
    val isAvailable: Boolean = true
)

@Entity(tableName = "automation_rules")
data class AutomationRuleEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val description: String,
    val triggersJson: String = "[]",
    val conditionsJson: String = "[]",
    val actionsJson: String = "[]",
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

/**
 * KV segment entity for cross-session KV cache persistence.
 * Stores metadata about saved KV cache segments.
 */
@Entity(tableName = "kv_segments")
data class KvSegmentEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val conversationId: Long = 0,
    val segmentIndex: Int = 0,
    val filePath: String = "",
    val tokenCount: Int = 0,
    val turnStart: Int = 0,
    val turnEnd: Int = 0,
    val fingerprint: ByteArray? = null,
    val fingerprintSize: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as KvSegmentEntity
        if (id != other.id) return false
        if (conversationId != other.conversationId) return false
        if (segmentIndex != other.segmentIndex) return false
        if (filePath != other.filePath) return false
        if (tokenCount != other.tokenCount) return false
        if (turnStart != other.turnStart) return false
        if (turnEnd != other.turnEnd) return false
        if (fingerprint != null) {
            if (other.fingerprint == null) return false
            if (!fingerprint.contentEquals(other.fingerprint)) return false
        } else if (other.fingerprint != null) return false
        if (fingerprintSize != other.fingerprintSize) return false
        if (createdAt != other.createdAt) return false
        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + conversationId.hashCode()
        result = 31 * result + segmentIndex
        result = 31 * result + filePath.hashCode()
        result = 31 * result + tokenCount
        result = 31 * result + turnStart
        result = 31 * result + turnEnd
        result = 31 * result + (fingerprint?.contentHashCode() ?: 0)
        result = 31 * result + fingerprintSize
        result = 31 * result + createdAt.hashCode()
        return result
    }
}

/**
 * Content fingerprint entity for semantic similarity search.
 * Stores int8-quantized embedding vectors for memories, messages, KV segments, documents.
 */
@Entity(tableName = "content_fingerprints")
data class ContentFingerprintEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val contentType: String = "",          // "message", "memory", "kv_segment", "document"
    val contentId: Long = 0,               // Corresponding content ID
    val conversationId: Long = 0,          // Associated conversation ID (if any)
    val fingerprint: ByteArray = byteArrayOf(),  // int8-quantized semantic fingerprint
    val fingerprintDim: Int = 0,           // Fingerprint vector dimension
    val summary: String = "",              // Content summary for display
    val keywords: String = "",             // Keywords (comma-separated, for assisted retrieval)
    val createdAt: Long = System.currentTimeMillis()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as ContentFingerprintEntity
        if (id != other.id) return false
        if (contentType != other.contentType) return false
        if (contentId != other.contentId) return false
        if (conversationId != other.conversationId) return false
        if (!fingerprint.contentEquals(other.fingerprint)) return false
        if (fingerprintDim != other.fingerprintDim) return false
        if (summary != other.summary) return false
        if (keywords != other.keywords) return false
        if (createdAt != other.createdAt) return false
        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + contentType.hashCode()
        result = 31 * result + contentId.hashCode()
        result = 31 * result + conversationId.hashCode()
        result = 31 * result + fingerprint.contentHashCode()
        result = 31 * result + fingerprintDim
        result = 31 * result + summary.hashCode()
        result = 31 * result + keywords.hashCode()
        result = 31 * result + createdAt.hashCode()
        return result
    }
}

