package com.neuralmind.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.content.Context
import com.neuralmind.core.MemoryMonitor
import dagger.hilt.android.qualifiers.ApplicationContext
import com.neuralmind.core.Logger
import com.neuralmind.data.repository.ChatRepository
import com.neuralmind.data.repository.ModelRepository
import com.neuralmind.data.repository.MemoryRepository
import com.neuralmind.data.repository.SkillRepository
import com.neuralmind.tools.DeviceToolExecutor
import com.neuralmind.service.NeuralMindAccessibilityService
import com.neuralmind.domain.model.AIModel
import com.neuralmind.domain.model.Conversation
import com.neuralmind.domain.model.Message
import com.neuralmind.domain.model.MessageRole
import com.neuralmind.llama.LlamaEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val chatRepository: ChatRepository,
    private val modelRepository: ModelRepository,
    private val memoryRepository: MemoryRepository,
    private val skillRepository: SkillRepository,
    private val llamaEngine: LlamaEngine,
    private val deviceToolExecutor: DeviceToolExecutor,
    private val memoryMonitor: MemoryMonitor
) : ViewModel() {
    
    private var lastExecutedTools = mutableSetOf<String>()
    private val memToolManager = MemToolManager()
    
    init {
        memoryMonitor.startMonitoring()
        Logger.i(Logger.Tags.VM, "ChatViewModel: memory monitoring started")
    }
    
    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()
    
    val conversations = chatRepository.getActiveConversations()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    
    val installedModels = modelRepository.getInstalledModels()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    
    val allModels = modelRepository.getAllModels()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    
    private val _currentConversation = MutableStateFlow<Conversation?>(null)
    val currentConversation: StateFlow<Conversation?> = _currentConversation
    
    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages
    
    private val _streamingMessage = MutableStateFlow<Message?>(null)
    val streamingMessage: StateFlow<Message?> = _streamingMessage
    
    private val _errorEvent = Channel<String>(Channel.BUFFERED)
    val errorEvent = _errorEvent.receiveAsFlow()
    
    private val reservedOutputTokens = 512
    private val tokenBudget: Int
        get() = llamaEngine.getNctx() - reservedOutputTokens
    private val maxResponseLength = 500
    
    fun updateInputText(text: String) {
        Logger.d(Logger.Tags.VM, "updateInputText(text=${text.take(20)}...)")
        _uiState.update { it.copy(inputText = text) }
    }
    
    fun createConversation(title: String, model: String, onCreated: (Long) -> Unit) {
        Logger.d(Logger.Tags.VM, "createConversation(title=$title, model=$model)")
        viewModelScope.launch {
            try {
                val id = chatRepository.createConversation(title, model)
                Logger.i(Logger.Tags.VM, "createConversation success: id=$id")
                onCreated(id)
            } catch (e: Exception) {
                Logger.e(Logger.Tags.VM, "createConversation failed: title=$title", e)
            }
        }
    }
    
    fun loadConversation(conversationId: Long) {
        Logger.d(Logger.Tags.VM, "loadConversation(conversationId=$conversationId)")
        viewModelScope.launch {
            try {
                val conversation = chatRepository.getConversationById(conversationId)
                _currentConversation.value = conversation
                Logger.i(Logger.Tags.VM, "loadConversation success: ${conversation?.title}")
                
                conversation?.let { conv ->
                    chatRepository.getMessagesByConversation(conv.id).collect { msgs ->
                        _messages.value = msgs
                        Logger.d(Logger.Tags.VM, "loadConversation: loaded ${msgs.size} messages")
                    }
                }
            } catch (e: Exception) {
                Logger.e(Logger.Tags.VM, "loadConversation failed: conversationId=$conversationId", e)
            }
        }
    }
    
    fun sendMessage(content: String) {
        val conversation = _currentConversation.value
        if (conversation == null) {
            Logger.w(Logger.Tags.VM, "sendMessage: no current conversation")
            return
        }
        
        Logger.d(Logger.Tags.VM, "sendMessage(content=${content.take(50)}...)")
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, isStreaming = false, inputText = "") }
            
            try {
                val userMessageId = chatRepository.sendMessage(
                    conversationId = conversation.id,
                    role = MessageRole.USER,
                    content = content
                )
                Logger.d(Logger.Tags.VM, "sendMessage: user message sent, id=$userMessageId")
                
                memoryRepository.activateMemoryFromUserInput(content)
                
                _uiState.update { it.copy(isLoading = false, isStreaming = true) }
                
                val contextMessages = _messages.value.dropLast(1).takeLast(10)
                generateAIResponse(conversation, content, contextMessages)
            } catch (e: Exception) {
                Logger.e(Logger.Tags.VM, "sendMessage failed", e)
                _uiState.update { it.copy(isLoading = false, isStreaming = false) }
                _errorEvent.send("发送消息失败: ${e.message}")
            }
        }
    }
    
    private suspend fun generateAIResponse(
        conversation: Conversation,
        userInput: String,
        contextMessages: List<Message>
    ) {
        Logger.d(Logger.Tags.VM, "generateAIResponse: building prompt")
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
        
        val modelLoaded = if (llamaEngine.isModelLoaded.value) {
            Logger.d(Logger.Tags.VM, "generateAIResponse: model already loaded")
            true
        } else {
            val targetModelId = modelRepository.currentModel.value?.id ?: modelId
            if (targetModelId.isNotEmpty()) {
                Logger.d(Logger.Tags.VM, "generateAIResponse: loading model $targetModelId")
                llamaEngine.loadModel(targetModelId)
            } else {
                Logger.w(Logger.Tags.VM, "generateAIResponse: no model available")
                false
            }
        }
        
        if (modelLoaded) {
            Logger.d(Logger.Tags.VM, "generateAIResponse: model loaded, starting inference")
            
            if (memoryMonitor.isMemoryPressure()) {
                Logger.w(Logger.Tags.VM, "Memory pressure detected (${memoryMonitor.getMemoryUsagePercent().toInt()}%), clearing KV cache and L1 memories")
                llamaEngine.clearKvRange()
                memoryRepository.clearLayerMemories(com.neuralmind.domain.model.MemoryLayer.L1_WORKING)
            }
            
            val prompt = buildPrompt(userInput, contextMessages, modelId)
            generateWithToolLoop(conversation, prompt, modelId, userInput)
        } else {
            Logger.w(Logger.Tags.VM, "generateAIResponse: model not loaded, sending fallback response")
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
    
    private suspend fun handleTimeout(
        conversation: Conversation,
        modelId: String,
        userInput: String,
        allDisplayText: String,
        originalAiResponse: String
    ) {
        Logger.w(Logger.Tags.VM, "handleTimeout: stopping generation")
        val safeDisplayText = if (allDisplayText.length > maxResponseLength) {
            allDisplayText.take(maxResponseLength)
        } else {
            allDisplayText
        }
        chatRepository.sendMessage(
            conversationId = conversation.id,
            role = MessageRole.ASSISTANT,
            content = safeDisplayText.ifBlank { "推理超时，请重试" },
            model = modelId
        )
        memoryRepository.saveConversationSegment(userInput, safeDisplayText, modelId)
        _streamingMessage.value = null
        _uiState.update { it.copy(isStreaming = false) }
        memoryMonitor.stopInferenceTimeout()
    }
    
    private suspend fun finalizeResponse(
        conversation: Conversation,
        modelId: String,
        userInput: String,
        allDisplayText: String,
        originalAiResponse: String
    ) {
        val safeDisplayText = if (allDisplayText.length > maxResponseLength) {
            allDisplayText.take(maxResponseLength)
        } else {
            allDisplayText
        }
        chatRepository.sendMessage(
            conversationId = conversation.id,
            role = MessageRole.ASSISTANT,
            content = safeDisplayText,
            model = modelId
        )
        memoryRepository.saveConversationSegment(userInput, safeDisplayText, modelId)
        Logger.i(Logger.Tags.VM, "generateWithToolLoop: completed, ${safeDisplayText.length} chars")
        _streamingMessage.value = null
        _uiState.update { it.copy(isStreaming = false) }
        memoryMonitor.stopInferenceTimeout()
    }
    
    private suspend fun generateWithToolLoop(
        conversation: Conversation,
        initialPrompt: String,
        modelId: String,
        userInput: String
    ) {
        Logger.i(Logger.Tags.VM, "=== generateWithToolLoop START ===")
        Logger.d(Logger.Tags.VM, "generateWithToolLoop: conversationId=${conversation.id}, modelId=$modelId")
        Logger.d(Logger.Tags.VM, "generateWithToolLoop: userInput='${userInput.take(50)}...'")
        Logger.d(Logger.Tags.VM, "generateWithToolLoop: promptLength=${initialPrompt.length}, promptTokens=${estimateTokenCount(initialPrompt)}")
        
        val startTime = System.currentTimeMillis()
        val maxTotalTimeMs = 5 * 60 * 1000L // 5分钟总时间限制
        val maxIterations = 20 // 增加到20次迭代
        
        lastExecutedTools.clear()
        memToolManager.clearAll()
        memToolManager.addMemory(MemoryType.USER_INPUT, userInput, importance = 3)
        
        var currentPrompt = initialPrompt
        var iteration = 0
        var allDisplayText = ""
        var originalAiResponse = ""
        var consecutiveNoToolCalls = 0
        
        Logger.d(Logger.Tags.VM, "generateWithToolLoop: starting inference timeout (120s)")
        memoryMonitor.startInferenceTimeout(120_000L)
        
        while (iteration < maxIterations) {
            iteration++
            Logger.i(Logger.Tags.VM, "--- Iteration $iteration/$maxIterations ---")
            
            // 检查总时间限制
            val totalElapsed = System.currentTimeMillis() - startTime
            if (totalElapsed > maxTotalTimeMs) {
                Logger.w(Logger.Tags.VM, "generateWithToolLoop: total time limit (${maxTotalTimeMs/1000}s) reached")
                finalizeResponse(conversation, modelId, userInput, allDisplayText, originalAiResponse)
                return
            }
            
            var tempResponse = ""
            var tokenCount = 0
            
            if (memoryMonitor.isInferenceTimeout()) {
                Logger.w(Logger.Tags.VM, "generateWithToolLoop: timeout detected before generation")
                handleTimeout(conversation, modelId, userInput, allDisplayText, originalAiResponse)
                return
            }
            
            _streamingMessage.value = Message(
                id = -1,
                conversationId = conversation.id,
                role = MessageRole.ASSISTANT,
                content = if (allDisplayText.isNotBlank()) allDisplayText else "",
                timestamp = System.currentTimeMillis(),
                model = modelId
            )
            
            val responseDeferred = CompletableDeferred<String>()
            val generationStartTime = System.currentTimeMillis()
            
            Logger.d(Logger.Tags.VM, "generateWithToolLoop: calling llamaEngine.generate(), prompt=${currentPrompt.take(100)}...")
            
            llamaEngine.generate(
                prompt = currentPrompt,
                onToken = { token ->
                    tokenCount++
                    tempResponse += token
                    _streamingMessage.value = _streamingMessage.value?.copy(
                        content = allDisplayText + tempResponse
                    )
                    if (tokenCount % 10 == 0) {
                        Logger.v(Logger.Tags.VM, "generateWithToolLoop: received $tokenCount tokens, tempResponse=${tempResponse.length} chars")
                    }
                },
                onComplete = { finalResponse ->
                    val elapsed = System.currentTimeMillis() - generationStartTime
                    Logger.i(Logger.Tags.VM, "generateWithToolLoop: generation completed in ${elapsed}ms, ${finalResponse.length} chars, $tokenCount tokens")
                    responseDeferred.complete(finalResponse)
                },
                onError = { error ->
                    val elapsed = System.currentTimeMillis() - generationStartTime
                    Logger.e(Logger.Tags.VM, "generateWithToolLoop: generation error after ${elapsed}ms: $error")
                    responseDeferred.completeExceptionally(Exception(error))
                }
            )
            
            Logger.d(Logger.Tags.VM, "generateWithToolLoop: waiting for response...")
            val response = try {
                responseDeferred.await()
            } catch (e: Exception) {
                Logger.e(Logger.Tags.VM, "generateWithToolLoop error: ${e.message}", e)
                chatRepository.sendMessage(
                    conversationId = conversation.id,
                    role = MessageRole.ASSISTANT,
                    content = allDisplayText.ifBlank { "生成失败: ${e.message}" },
                    model = modelId
                )
                _streamingMessage.value = null
                _uiState.update { it.copy(isStreaming = false) }
                memoryMonitor.stopInferenceTimeout()
                return
            }
            
            Logger.d(Logger.Tags.VM, "generateWithToolLoop: response received, length=${response.length}")
            Logger.v(Logger.Tags.VM, "generateWithToolLoop: response content='${response.take(200)}...'")
            
            if (memoryMonitor.isInferenceTimeout()) {
                Logger.w(Logger.Tags.VM, "generateWithToolLoop: timeout detected after generation")
                handleTimeout(conversation, modelId, userInput, allDisplayText, originalAiResponse)
                return
            }
            
            Logger.d(Logger.Tags.VM, "generateWithToolLoop: parsing tool calls...")
            val (cleanText, toolCalls) = deviceToolExecutor.parseToolCalls(response)
            Logger.i(Logger.Tags.VM, "generateWithToolLoop: parsed ${toolCalls.size} tool calls, cleanText=${cleanText.length} chars")
            
            // 检查是否需要停止（先检查是否有工具调用）
            if (toolCalls.isEmpty()) {
                consecutiveNoToolCalls++
                Logger.w(Logger.Tags.VM, "generateWithToolLoop: no tool calls (consecutive: $consecutiveNoToolCalls)")
                
                // 连续2次没有工具调用才停止
                if (consecutiveNoToolCalls >= 2) {
                    Logger.i(Logger.Tags.VM, "generateWithToolLoop: no tool calls for 2 iterations, finalizing response")
                    val safeCleanText = if (cleanText.length > maxResponseLength) {
                        cleanText.take(maxResponseLength)
                    } else {
                        cleanText
                    }
                    allDisplayText += safeCleanText
                    originalAiResponse += cleanText
                    finalizeResponse(conversation, modelId, userInput, allDisplayText, originalAiResponse)
                    return
                }
                
                // 只有1次没有工具调用，继续循环（给模型第二次机会）
                Logger.i(Logger.Tags.VM, "generateWithToolLoop: giving model another chance...")
                allDisplayText += cleanText + "\n"
                originalAiResponse += cleanText
                memToolManager.addMemory(MemoryType.ASSISTANT_RESPONSE, cleanText, importance = 1)
                continue
            }
            
            // 有工具调用，重置计数器
            consecutiveNoToolCalls = 0
            
            Logger.i(Logger.Tags.VM, "Found ${toolCalls.size} tool calls, executing...")
            toolCalls.forEachIndexed { index, call ->
                Logger.d(Logger.Tags.VM, "ToolCall[$index]: name=${call.name}, params='${call.params}'")
            }
            
            originalAiResponse += response
            
            if (cleanText.isNotBlank()) {
                allDisplayText += cleanText + "\n"
                memToolManager.addMemory(MemoryType.ASSISTANT_RESPONSE, cleanText, importance = 1)
            }
            
            val toolResults = StringBuilder()
            var hasNewToolExecution = false
            
            for (call in toolCalls) {
                val toolKey = "${call.name}:${call.params}"
                if (lastExecutedTools.contains(toolKey)) {
                    Logger.w(Logger.Tags.VM, "Skipping duplicate tool execution: $toolKey")
                    continue
                }
                
                // 检查重复工具调用（1分钟内）
                if (memToolManager.isDuplicateToolCall(call.name, call.params)) {
                    Logger.w(Logger.Tags.VM, "Skipping duplicate tool call (recent): $toolKey")
                    continue
                }
                
                Logger.d(Logger.Tags.VM, "Executing tool: ${call.name} → ${call.params}")
                val result = deviceToolExecutor.executeTool(call)
                Logger.d(Logger.Tags.VM, "Tool result: success=${result.success}, msg=${result.message}")
                
                lastExecutedTools.add(toolKey)
                hasNewToolExecution = true
                
                // 压缩工具结果，减少上下文占用
                val compressedResult = compressToolResult(result.message)
                toolResults.append(compressedResult).append("\n")
                
                // 记录到 MemTool
                memToolManager.addMemory(MemoryType.TOOL_CALL, "${call.name}:${call.params}", importance = 2)
                memToolManager.addMemory(MemoryType.TOOL_RESULT, result.message, importance = 2)
            }
            
            allDisplayText += toolResults.toString()
            
            val safeDisplayText = if (allDisplayText.length > maxResponseLength) {
                allDisplayText.take(maxResponseLength)
            } else {
                allDisplayText
            }
            _streamingMessage.value = _streamingMessage.value?.copy(content = safeDisplayText)
            
            if (!hasNewToolExecution) {
                Logger.w(Logger.Tags.VM, "generateWithToolLoop: no new tool executions, ending loop")
                finalizeResponse(conversation, modelId, userInput, allDisplayText, originalAiResponse)
                return
            }
            
            // 检查是否需要停止 - MemTool 智能停止条件（在工具执行后检查）
            if (memToolManager.shouldStopTask(response)) {
                Logger.i(Logger.Tags.VM, "generateWithToolLoop: task complete according to MemTool stop conditions")
                finalizeResponse(conversation, modelId, userInput, allDisplayText, originalAiResponse)
                return
            }
            
            val template = ChatTemplate.fromModelId(modelId)
            currentPrompt = buildToolResultPrompt(template, currentPrompt, cleanText, toolResults.toString())
            Logger.d(Logger.Tags.VM, "generateWithToolLoop: updated prompt for next iteration, length=${currentPrompt.length}")
            
            llamaEngine.clearPromptCache()
        }
        
        Logger.w(Logger.Tags.VM, "generateWithToolLoop: max iterations ($maxIterations) reached")
        finalizeResponse(conversation, modelId, userInput, allDisplayText, originalAiResponse)
        Logger.i(Logger.Tags.VM, "=== generateWithToolLoop END ===")
    }
    
    // 压缩工具结果，减少上下文占用
    private fun compressToolResult(result: String): String {
        return when {
            result.startsWith("已打开应用:") -> "✓ 已打开"
            result.startsWith("已返回") -> "✓ 返回"
            result.startsWith("已回到主页") -> "✓ 主页"
            result.startsWith("已点击") -> "✓ 点击"
            result.startsWith("已输入") -> "✓ 输入"
            result.startsWith("已滑动") -> "✓ 滑动"
            result.startsWith("未找到") -> "✗ 未找到"
            else -> {
                val shortResult = result.take(30)
                if (result.length > 30) "$shortResult..." else shortResult
            }
        }
    }
    
    private fun buildToolResultPrompt(
        template: ChatTemplate,
        originalPrompt: String,
        cleanResponse: String,
        toolResult: String
    ): String {
        val sb = StringBuilder()
        sb.append(originalPrompt)
        
        // 简化工具结果，只保留核心信息
        val simpleResult = toolResult
            .replace("launch_app(", "")
            .replace("): 已打开应用: ", "")
            .replace("go_back(): 已返回", "已返回")
            .replace("go_home(): 已回到主页", "已回到主页")
            .replace(")", "")
        
        return when (template) {
            ChatTemplate.CHATML -> {
                sb.append("<|im_end|>\n<|im_start|>user\n")
                sb.append(simpleResult)
                sb.append("<|im_end|>\n<|im_start|>assistant\n")
                sb.toString()
            }
            ChatTemplate.LLAMA3 -> {
                sb.append("<|eot_id|>")
                sb.append("<|start_header_id|>user<|end_header_id|>\n\n")
                sb.append(simpleResult)
                sb.append("<|eot_id|>")
                sb.append("<|start_header_id|>assistant<|end_header_id|>\n\n")
                sb.toString()
            }
            ChatTemplate.PHI -> {
                sb.append("<|end|>\n<|user|>\n")
                sb.append(simpleResult)
                sb.append("<|end|>\n<|assistant|>\n")
                sb.toString()
            }
            ChatTemplate.GEMMA -> {
                sb.append("<end_of_turn>\n<start_of_turn>user\n")
                sb.append(simpleResult)
                sb.append("<end_of_turn>\n<start_of_turn>model\n")
                sb.toString()
            }
            ChatTemplate.MISTRAL -> {
                sb.append("[/INST]\n[INST] ")
                sb.append(simpleResult)
                sb.append(" [/INST]")
                sb.toString()
            }
        }
    }
    
    private fun estimateTokenCount(text: String): Int {
        var cjkCount = 0
        var asciiCount = 0
        for (char in text) {
            if (char.code > 127) cjkCount++ else asciiCount++
        }
        val estimate = (cjkCount * 2 + asciiCount * 0.25).toInt().coerceAtLeast(1)
        return (estimate * 1.2).toInt()
    }
    
    private enum class ChatTemplate(val id: String) {
        CHATML("chatml"),
        LLAMA3("llama3"),
        PHI("phi"),
        GEMMA("gemma"),
        MISTRAL("mistral");

        companion object {
            fun fromModelId(modelId: String): ChatTemplate {
                return when {
                    modelId.startsWith("qwen") -> CHATML
                    modelId.startsWith("llama") -> LLAMA3
                    modelId.startsWith("phi") -> PHI
                    modelId.startsWith("gemma") -> GEMMA
                    modelId.startsWith("mistral") -> MISTRAL
                    else -> CHATML
                }
            }
        }
    }
    
    private fun formatMessage(template: ChatTemplate, role: String, content: String): String {
        return when (template) {
            ChatTemplate.CHATML -> "<|im_start|>$role\n$content<|im_end|>\n"
            ChatTemplate.LLAMA3 -> "<|start_header_id|>$role<|end_header_id|>\n\n$content<|eot_id|>"
            ChatTemplate.PHI -> "<|$role|>\n$content<|end|>\n"
            ChatTemplate.GEMMA -> "<start_of_turn>${if (role == "assistant") "model" else role}\n$content<end_of_turn>\n"
            ChatTemplate.MISTRAL -> if (role == "user") "[INST] $content [/INST]" else " $content"
        }
    }
    
    private suspend fun buildPrompt(userInput: String, contextMessages: List<Message>, modelId: String): String {
        Logger.d(Logger.Tags.VM, "buildPrompt: userInput=${userInput.take(30)}...")
        val template = ChatTemplate.fromModelId(modelId)
        val sb = StringBuilder()
        
        val systemContent = buildSystemPrompt(template, userInput)
        sb.append(formatMessage(template, "system", systemContent))
        
        val systemPromptTokens = estimateTokenCount(sb.toString())
        val userInputTokens = estimateTokenCount(userInput)
        val remainingBudget = tokenBudget - systemPromptTokens - userInputTokens
        
        Logger.d(Logger.Tags.VM, "buildPrompt: template=$template, token budget=${tokenBudget}, system=${systemPromptTokens}, userInput=${userInputTokens}, remaining=${remainingBudget}")
        
        var contextTokensUsed = 0
        val limitedContextMessages = contextMessages.takeLastWhile { msg ->
            val msgTokens = estimateTokenCount(msg.content)
            if (contextTokensUsed + msgTokens <= remainingBudget) {
                contextTokensUsed += msgTokens
                true
            } else {
                Logger.d(Logger.Tags.VM, "buildPrompt: dropping message (${msgTokens} tokens) due to budget")
                false
            }
        }
        
        for (msg in limitedContextMessages) {
            val role = when (msg.role) {
                MessageRole.USER -> "user"
                MessageRole.ASSISTANT -> "assistant"
                MessageRole.SYSTEM -> "system"
            }
            sb.append(formatMessage(template, role, msg.content))
        }
        
        sb.append(formatMessage(template, "user", userInput))
        sb.append(formatMessage(template, "assistant", ""))
        
        Logger.d(Logger.Tags.VM, "buildPrompt: completed, template=$template, ${sb.length} chars, ~${estimateTokenCount(sb.toString())} tokens")
        return sb.toString()
    }
    
    private suspend fun buildSystemPrompt(template: ChatTemplate, userInput: String = ""): String {
        val sb = StringBuilder()
        val selectedTools = ToolRegistry.selectToolsForUserInput(userInput)
        val toolsList = ToolRegistry.buildToolsList(selectedTools)
        
        return when (template) {
            ChatTemplate.CHATML -> {
                sb.append("""你是一个Android手机控制助手，只能通过工具操作手机。

【工具调用格式 - 必须严格遵守】
正确格式：[ACTION:launch_app]抖音[/ACTION]
错误格式：launch_app:抖音 或 launch_app(抖音) 或 <tool_call>
- 工具名用方括号括起来
- 参数用方括号括起来
- 不要使用冒号、圆括号或其他符号

【可用工具】
${toolsList}

【严格禁止】
- 禁止输出 "assistant"、"user"、"Human"、"AI" 等任何前缀
- 禁止使用冒号格式（如 launch_app:抖音）
- 禁止使用圆括号格式（如 launch_app(抖音)）
- 禁止输出任何解释、标题或多余文字
- 禁止重复之前的对话或示例

【重要规则】
- 可以连续调用多个工具
- 每次调用后等待工具结果再继续
- 任务完成后直接回答

【正确示例】
用户：打开抖音
[ACTION:launch_app]抖音[/ACTION]

用户：返回
[ACTION:go_back][/ACTION]

用户：你好
你好！有什么可以帮你的吗？""")
                sb.toString()
            }
            
            ChatTemplate.LLAMA3 -> {
                sb.append("""你是Android手机控制助手，只能用工具操作手机。

【工具调用格式】
正确格式：[ACTION:launch_app]抖音[/ACTION]
- 只能用方括号格式
- 绝对不要输出 "assistant"

【可用工具】
${toolsList}

【重要规则】
- 可以连续调用多个工具
- 每次调用后等待工具结果再继续
- 任务完成后直接回答

【示例】
用户：打开抖音
[ACTION:launch_app]抖音[/ACTION]

用户：你好
你好！""")
                sb.toString()
            }
            
            ChatTemplate.PHI -> {
                sb.append("""你是一个Android手机控制助手，只能通过工具操作手机。

【工具调用格式 - 必须严格遵守】
正确格式：[ACTION:launch_app]抖音[/ACTION]
错误格式：launch_app:抖音 或 launch_app(抖音)

【可用工具】
${toolsList}

【严格禁止】
- 禁止输出 "assistant"、冒号格式、圆括号格式
- 禁止输出任何前缀、解释或多余文字

【重要规则】
- 可以连续调用多个工具
- 每次调用后等待工具结果再继续
- 任务完成后直接回答

【示例】
用户：打开抖音
[ACTION:launch_app]抖音[/ACTION]""")
                sb.toString()
            }
            
            ChatTemplate.GEMMA -> {
                sb.append("""你是一个Android手机控制助手，只能通过工具操作手机。

【工具调用格式 - 必须严格遵守】
正确格式：[ACTION:launch_app]抖音[/ACTION]
错误格式：launch_app:抖音 或 launch_app(抖音)

【可用工具】
${toolsList}

【严格禁止】
- 禁止输出 "assistant"、冒号格式、圆括号格式
- 禁止输出任何前缀、解释或多余文字

【重要规则】
- 可以连续调用多个工具
- 每次调用后等待工具结果再继续
- 任务完成后直接回答

【示例】
用户：打开抖音
[ACTION:launch_app]抖音[/ACTION]""")
                sb.toString()
            }
            
            ChatTemplate.MISTRAL -> {
                sb.append("""你是一个Android手机控制助手，只能通过工具操作手机。

【工具调用格式 - 必须严格遵守】
正确格式：[ACTION:launch_app]抖音[/ACTION]
错误格式：launch_app:抖音 或 launch_app(抖音)

【可用工具】
${toolsList}

【严格禁止】
- 禁止输出 "assistant"、冒号格式、圆括号格式
- 禁止输出任何前缀、解释或多余文字

【重要规则】
- 可以连续调用多个工具
- 每次调用后等待工具结果再继续
- 任务完成后直接回答

【示例】
用户：打开抖音
[ACTION:launch_app]抖音[/ACTION]""")
                sb.toString()
            }
        }
    }
    
    fun clearPromptCache() {
        Logger.d(Logger.Tags.VM, "clearPromptCache()")
        llamaEngine.clearPromptCache()
    }
    
    fun selectModel(model: AIModel) {
        Logger.d(Logger.Tags.VM, "selectModel(model=${model.name})")
        viewModelScope.launch {
            try {
                modelRepository.switchModel(model.id)
                llamaEngine.loadModel(model.id)
                llamaEngine.clearPromptCache()
                _currentConversation.value = _currentConversation.value?.copy(model = model.id)
                Logger.i(Logger.Tags.VM, "selectModel success: ${model.name}")
            } catch (e: Exception) {
                Logger.e(Logger.Tags.VM, "selectModel failed: ${model.name}", e)
            }
        }
    }
    
    fun clearError() {
        Logger.d(Logger.Tags.VM, "clearError")
        _uiState.update { it.copy(error = null) }
    }
    
    override fun onCleared() {
        super.onCleared()
        memoryMonitor.stopMonitoring()
        Logger.i(Logger.Tags.VM, "ChatViewModel: memory monitoring stopped")
    }
}

data class ChatUiState(
    val isLoading: Boolean = false,
    val isStreaming: Boolean = false,
    val error: String? = null,
    val inputText: String = ""
)
