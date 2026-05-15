package com.neuralmind.data.search

import com.neuralmind.core.Logger
import com.neuralmind.data.local.db.dao.ContentFingerprintDao
import com.neuralmind.data.local.db.dao.MemoryDao
import com.neuralmind.data.local.db.entity.ContentFingerprintEntity
import com.neuralmind.domain.model.Memory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt

/**
 * Fingerprint-based semantic similarity search index.
 * Stores and检索 int8-quantized embedding vectors for semantic search.
 * 
 * Supports content types:
 * - "memory": memory entities
 * - "message": chat messages
 * - "kv_segment": KV cache segments
 * - "document": document chunks
 */
@Singleton
class FingerprintIndex @Inject constructor(
    private val fingerprintDao: ContentFingerprintDao,
    private val memoryDao: MemoryDao
) {
    companion object {
        private const val TAG = "FingerprintIndex"
        private const val DEFAULT_TOP_K = 5
        // int8 quantization scale
        private const val INT8_SCALE = 127.0f
    }
    
    /**
     * Quantize float embedding to int8 for storage.
     * @param floats original float embedding
     * @return quantized int8 bytes
     */
    private fun quantizeToInt8(floats: FloatArray): ByteArray {
        // Find max absolute value for scaling
        val maxAbs = floats.maxOfOrNull { kotlin.math.abs(it) } ?: 1f
        val scale = if (maxAbs > 0) INT8_SCALE / maxAbs else 1f
        
        return ByteArray(floats.size) { i ->
            (floats[i] * scale).toInt().coerceIn(-127, 127).toByte()
        }
    }
    
    /**
     * Dequantize int8 bytes back to float embedding.
     * @param bytes quantized int8 bytes
     * @param originalScale original scaling factor
     * @return dequantized float embedding
     */
    private fun dequantizeFromInt8(bytes: ByteArray, originalScale: Float): FloatArray {
        val scale = 1f / (originalScale / INT8_SCALE)
        return FloatArray(bytes.size) { i ->
            bytes[i].toInt() * scale
        }
    }
    
    /**
     * Compute cosine similarity between two float vectors.
     */
    private fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        if (a.size != b.size) return 0f
        
        var dotProduct = 0f
        var normA = 0f
        var normB = 0f
        
        for (i in a.indices) {
            dotProduct += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }
        
        val normProd = sqrt(normA) * sqrt(normB)
        return if (normProd > 0f) dotProduct / normProd else 0f
    }
    
    /**
     * Store a fingerprint for content.
     * @param contentType type of content (memory, message, etc.)
     * @param contentId ID of the content
     * @param fingerprint float embedding vector
     * @param summary content summary for display
     * @param keywords comma-separated keywords
     * @param conversationId associated conversation ID
     */
    suspend fun storeFingerprint(
        contentType: String,
        contentId: Long,
        fingerprint: FloatArray,
        summary: String,
        keywords: String,
        conversationId: Long = 0
    ): Long = withContext(Dispatchers.IO) {
        Logger.d(TAG, "storeFingerprint: type=$contentType, id=$contentId, dim=${fingerprint.size}")
        
        try {
            // Quantize to int8 for storage
            val quantized = quantizeToInt8(fingerprint)
            
            val entity = ContentFingerprintEntity(
                contentType = contentType,
                contentId = contentId,
                conversationId = conversationId,
                fingerprint = quantized,
                fingerprintDim = fingerprint.size,
                summary = summary,
                keywords = keywords,
                createdAt = System.currentTimeMillis()
            )
            
            val id = fingerprintDao.insert(entity)
            Logger.i(TAG, "storeFingerprint success: id=$id")
            id
        } catch (e: Exception) {
            Logger.e(TAG, "storeFingerprint failed", e)
            -1L
        }
    }
    
    /**
     * Search for similar content using fingerprint similarity.
     * @param queryFingerprint query embedding
     * @param contentType filter by content type (null for all types)
     * @param topK number of results to return
     * @return list of search results with similarity scores
     */
    suspend fun searchSimilar(
        queryFingerprint: FloatArray,
        contentType: String? = null,
        topK: Int = DEFAULT_TOP_K
    ): List<FingerprintSearchResult> = withContext(Dispatchers.IO) {
        Logger.d(TAG, "searchSimilar: query dim=${queryFingerprint.size}, type=$contentType, topK=$topK")
        
        try {
            // Get all fingerprints or filtered by type
            val fingerprints = if (contentType != null) {
                fingerprintDao.getFingerprintsByType(contentType).first()
            } else {
                fingerprintDao.getAllFingerprints().first()
            }
            
            Logger.d(TAG, "searchSimilar: found ${fingerprints.size} fingerprints to compare")
            
            // Compute similarities
            val results = fingerprints.mapNotNull { entity ->
                try {
                    val storedFingerprint = dequantizeFromInt8(entity.fingerprint, INT8_SCALE)
                    val similarity = cosineSimilarity(queryFingerprint, storedFingerprint)
                    
                    FingerprintSearchResult(
                        contentType = entity.contentType,
                        contentId = entity.contentId,
                        conversationId = entity.conversationId,
                        similarity = similarity,
                        summary = entity.summary,
                        keywords = entity.keywords
                    )
                } catch (e: Exception) {
                    Logger.w(TAG, "Failed to compare fingerprint id=${entity.id}: ${e.message}")
                    null
                }
            }
            
            // Sort by similarity and take top-K
            val sortedResults = results.sortedByDescending { it.similarity }.take(topK)
            
            Logger.d(TAG, "searchSimilar: returning ${sortedResults.size} results")
            sortedResults
        } catch (e: Exception) {
            Logger.e(TAG, "searchSimilar failed", e)
            emptyList()
        }
    }
    
    /**
     * Search for relevant memories using semantic similarity.
     * @param queryText query text to search for
     * @param llamaEngine LLM engine for fingerprint extraction
     * @param topK number of results
     * @return list of relevant memories
     */
    suspend fun searchRelevantMemories(
        queryFingerprint: FloatArray,
        topK: Int = DEFAULT_TOP_K
    ): List<MemorySearchResult> = withContext(Dispatchers.IO) {
        Logger.d(TAG, "searchRelevantMemories: query dim=${queryFingerprint.size}")
        
        try {
            // Search memory fingerprints
            val memoryResults = searchSimilar(queryFingerprint, "memory", topK)
            
            // Convert to MemorySearchResult by fetching actual memory content
            memoryResults.mapNotNull { fpResult ->
                try {
                    val memories = memoryDao.getAllActiveMemories().first()
                    val memory = memories.find { it.id == fpResult.contentId }
                    
                    memory?.let {
                        MemorySearchResult(
                            memoryId = it.id,
                            content = it.content,
                            layer = it.layer,
                            category = it.category,
                            importance = it.importance,
                            similarity = fpResult.similarity
                        )
                    }
                } catch (e: Exception) {
                    Logger.w(TAG, "Failed to fetch memory id=${fpResult.contentId}: ${e.message}")
                    null
                }
            }
        } catch (e: Exception) {
            Logger.e(TAG, "searchRelevantMemories failed", e)
            emptyList()
        }
    }
    
    /**
     * Delete all fingerprints for a specific content.
     */
    suspend fun deleteFingerprint(contentType: String, contentId: Long) = withContext(Dispatchers.IO) {
        try {
            fingerprintDao.deleteByContent(contentType, contentId)
            Logger.i(TAG, "deleteFingerprint: type=$contentType, id=$contentId")
        } catch (e: Exception) {
            Logger.e(TAG, "deleteFingerprint failed", e)
        }
    }
    
    /**
     * Delete all fingerprints for a conversation.
     */
    suspend fun deleteFingerprintsByConversation(conversationId: Long) = withContext(Dispatchers.IO) {
        try {
            fingerprintDao.deleteByConversation(conversationId)
            Logger.i(TAG, "deleteFingerprintsByConversation: conversationId=$conversationId")
        } catch (e: Exception) {
            Logger.e(TAG, "deleteFingerprintsByConversation failed", e)
        }
    }
    
    /**
     * Get fingerprint count.
     */
    suspend fun getFingerprintCount(): Int = withContext(Dispatchers.IO) {
        try {
            fingerprintDao.getFingerprintCount()
        } catch (e: Exception) {
            Logger.e(TAG, "getFingerprintCount failed", e)
            0
        }
    }
}

/**
 * Result of fingerprint similarity search.
 */
data class FingerprintSearchResult(
    val contentType: String,
    val contentId: Long,
    val conversationId: Long,
    val similarity: Float,
    val summary: String,
    val keywords: String
)

/**
 * Result of memory search with additional metadata.
 */
data class MemorySearchResult(
    val memoryId: Long,
    val content: String,
    val layer: String,
    val category: String,
    val importance: Int,
    val similarity: Float
)
