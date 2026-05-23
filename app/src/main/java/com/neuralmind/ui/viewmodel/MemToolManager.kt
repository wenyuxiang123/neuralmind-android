package com.neuralmind.ui.viewmodel

import com.neuralmind.core.Logger
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

data class MemoryItem(
    val id: String,
    val type: MemoryType,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val importance: Int = 1
)

enum class MemoryType {
    USER_INPUT,        // 用户输入
    ASSISTANT_RESPONSE, // 助手回复
    TOOL_CALL,         // 工具调用
    TOOL_RESULT,       // 工具结果
    TASK_SUMMARY       // 任务总结
}

data class MemoryStats(
    val totalItems: Int,
    val totalTokens: Int,
    val oldestItemTimestamp: Long?,
    val newestItemTimestamp: Long?
)

class MemToolManager {
    
    private val mutex = Mutex()
    private val memories = mutableListOf<MemoryItem>()
    private val duplicateCheck = ConcurrentHashMap<String, Int>()
    
    companion object {
        private const val MAX_MEMORIES = 50
        private const val MAX_TOKENS = 2000
        private const val TOKEN_ESTIMATE_PER_CHAR = 0.5
        private const val TAG = "MemToolManager"
        
        private val STOP_KEYWORDS = listOf(
            "完成", "搞定", "好了", "可以了", "没问题", "成功",
            "已打开", "已返回", "已点击", "已输入", "已滑动",
            "结束", "stop", "done", "finished", "complete"
        )
        
        private val CONTINUE_KEYWORDS = listOf(
            "继续", "然后", "接着", "接下来", "下一步", "然后呢",
            "还有", "另外", "还有吗", "还有别的", "more"
        )
    }
    
    suspend fun addMemory(type: MemoryType, content: String, importance: Int = 1) {
        mutex.withLock {
            // 检查重复内容
            val contentHash = content.take(100).hashCode().toString()
            val existingCount = duplicateCheck.getOrDefault(contentHash, 0)
            if (existingCount >= 2) {
                Logger.d(TAG, "Skipping duplicate memory: ${content.take(50)}...")
                return@withLock
            }
            duplicateCheck[contentHash] = existingCount + 1
            
            val id = "mem_${System.currentTimeMillis()}_${memories.size}"
            memories.add(
                MemoryItem(
                    id = id,
                    type = type,
                    content = content,
                    importance = importance
                )
            )
            
            // 清理旧的记忆
            cleanUpOldMemories()
            
            Logger.d(TAG, "Added memory: type=$type, content=${content.take(30)}...")
        }
    }
    
    suspend fun getCompressedContext(): String {
        mutex.withLock {
            val sb = StringBuilder()
            
            // 保留最近的重要记忆
            val importantMemories = memories
                .sortedByDescending { it.importance }
                .take(20)
            
            // 保留最近的对话
            val recentMemories = memories.takeLast(30)
            
            // 合并并去重
            val combinedMemories = (importantMemories + recentMemories)
                .distinctBy { it.id }
                .sortedBy { it.timestamp }
            
            for (memory in combinedMemories) {
                val compressedContent = compressContent(memory.content)
                when (memory.type) {
                    MemoryType.USER_INPUT -> {
                        sb.appendLine("用户: $compressedContent")
                    }
                    MemoryType.ASSISTANT_RESPONSE -> {
                        sb.appendLine("助手: $compressedContent")
                    }
                    MemoryType.TOOL_CALL -> {
                        sb.appendLine("工具: $compressedContent")
                    }
                    MemoryType.TOOL_RESULT -> {
                        sb.appendLine("结果: $compressedContent")
                    }
                    MemoryType.TASK_SUMMARY -> {
                        sb.appendLine("总结: $compressedContent")
                    }
                }
            }
            
            val result = sb.toString()
            Logger.d(TAG, "Compressed context: ${result.length} chars")
            return result
        }
    }
    
    private fun compressContent(content: String): String {
        // 移除多余空格和换行
        var compressed = content.replace(Regex("\\s+"), " ").trim()
        
        // 压缩长句子
        if (compressed.length > 100) {
            compressed = compressed.take(100) + "..."
        }
        
        return compressed
    }
    
    private fun cleanUpOldMemories() {
        // 如果超过最大数量，删除最旧的
        while (memories.size > MAX_MEMORIES) {
            val removed = memories.removeFirst()
            val contentHash = removed.content.take(100).hashCode().toString()
            duplicateCheck[contentHash] = duplicateCheck.getOrDefault(contentHash, 1) - 1
            Logger.d(TAG, "Removed old memory: ${removed.content.take(30)}...")
        }
        
        // 如果超过 token 限制，删除不重要的
        var currentTokens = estimateTotalTokens()
        while (currentTokens > MAX_TOKENS && memories.size > 5) {
            val leastImportant = memories.minByOrNull { it.importance }
            if (leastImportant != null) {
                memories.remove(leastImportant)
                val contentHash = leastImportant.content.take(100).hashCode().toString()
                duplicateCheck[contentHash] = duplicateCheck.getOrDefault(contentHash, 1) - 1
                currentTokens = estimateTotalTokens()
            } else {
                break
            }
        }
    }
    
    private fun estimateTotalTokens(): Int {
        return memories.sumOf { 
            (it.content.length * TOKEN_ESTIMATE_PER_CHAR).toInt()
        }
    }
    
    suspend fun shouldStopTask(response: String): Boolean {
        val lowerResponse = response.lowercase()
        
        // 检查是否有停止关键词
        val hasStopKeyword = STOP_KEYWORDS.any { keyword ->
            lowerResponse.contains(keyword.lowercase())
        }
        
        // 检查是否有继续关键词
        val hasContinueKeyword = CONTINUE_KEYWORDS.any { keyword ->
            lowerResponse.contains(keyword.lowercase())
        }
        
        // 如果有继续关键词，不停止
        if (hasContinueKeyword) {
            Logger.d(TAG, "Should NOT stop: found continue keyword")
            return false
        }
        
        // 如果有停止关键词，停止
        if (hasStopKeyword) {
            Logger.d(TAG, "Should stop: found stop keyword")
            return true
        }
        
        return false
    }
    
    suspend fun isDuplicateToolCall(toolName: String, params: String): Boolean {
        mutex.withLock {
            val toolCallKey = "$toolName:$params"
            return memories.any { memory ->
                memory.type == MemoryType.TOOL_CALL && 
                memory.content.contains(toolCallKey) &&
                System.currentTimeMillis() - memory.timestamp < 60000 // 1 分钟内
            }
        }
    }
    
    suspend fun getStats(): MemoryStats {
        mutex.withLock {
            return MemoryStats(
                totalItems = memories.size,
                totalTokens = estimateTotalTokens(),
                oldestItemTimestamp = memories.firstOrNull()?.timestamp,
                newestItemTimestamp = memories.lastOrNull()?.timestamp
            )
        }
    }
    
    suspend fun clearAll() {
        mutex.withLock {
            memories.clear()
            duplicateCheck.clear()
            Logger.d(TAG, "Cleared all memories")
        }
    }
    
    suspend fun addTaskSummary(summary: String) {
        addMemory(MemoryType.TASK_SUMMARY, summary, importance = 3)
    }
}
