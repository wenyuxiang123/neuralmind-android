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
        originalAiResponse: String,
        reason: String
    ) {
        Logger.w(Logger.Tags.VM, "handleTimeout: $reason")
        chatRepository.sendMessage(
            conversationId = conversation.id,
            role = MessageRole.ASSISTANT,
            content = allDisplayText.ifBlank { "推理超时，请重试" },
            model = modelId
        )
        memoryRepository.saveConversationSegment(userInput, originalAiResponse, modelId)
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
        chatRepository.sendMessage(
            conversationId = conversation.id,
            role = MessageRole.ASSISTANT,
            content = allDisplayText,
            model = modelId
        )
        memoryRepository.saveConversationSegment(userInput, originalAiResponse, modelId)
        Logger.i(Logger.Tags.VM, "generateWithToolLoop: completed, ${allDisplayText.length} chars")
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
        
        lastExecutedTools.clear()
        
        var currentPrompt = initialPrompt
        val maxIterations = 2
        var iteration = 0
        var allDisplayText = ""
        var originalAiResponse = ""
        
        Logger.d(Logger.Tags.VM, "generateWithToolLoop: starting inference timeout (60s)")
        memoryMonitor.startInferenceTimeout(60_000L)
        
        while (iteration < maxIterations) {
            iteration++
            Logger.i(Logger.Tags.VM, "--- Iteration $iteration/$maxIterations ---")
            var tempResponse = ""
            var tokenCount = 0
            
            if (memoryMonitor.isInferenceTimeout()) {
                Logger.w(Logger.Tags.VM, "generateWithToolLoop: timeout detected before generation")
                handleTimeout(conversation, modelId, userInput, allDisplayText, originalAiResponse, "stopping generation")
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
                handleTimeout(conversation, modelId, userInput, allDisplayText, originalAiResponse, "after tool execution")
                return
            }
            
            Logger.d(Logger.Tags.VM, "generateWithToolLoop: parsing tool calls...")
            val (cleanText, toolCalls) = deviceToolExecutor.parseToolCalls(response)
            Logger.i(Logger.Tags.VM, "generateWithToolLoop: parsed ${toolCalls.size} tool calls, cleanText=${cleanText.length} chars")
            
            if (toolCalls.isEmpty()) {
                Logger.i(Logger.Tags.VM, "generateWithToolLoop: no tool calls found, finalizing response")
                allDisplayText += cleanText
                originalAiResponse += cleanText
                finalizeResponse(conversation, modelId, userInput, allDisplayText, originalAiResponse)
                return
            }
            
            Logger.i(Logger.Tags.VM, "Found ${toolCalls.size} tool calls, executing...")
            toolCalls.forEachIndexed { index, call ->
                Logger.d(Logger.Tags.VM, "ToolCall[$index]: name=${call.name}, params='${call.params}'")
            }
            
            originalAiResponse += response
            
            if (cleanText.isNotBlank()) {
                allDisplayText += cleanText + "\n"
            }
            
            val toolResults = StringBuilder()
            var hasNewToolExecution = false
            
            for (call in toolCalls) {
                val toolKey = "${call.name}:${call.params}"
                if (lastExecutedTools.contains(toolKey)) {
                    Logger.w(Logger.Tags.VM, "Skipping duplicate tool execution: $toolKey")
                    continue
                }
                
                Logger.d(Logger.Tags.VM, "Executing tool: ${call.name} → ${call.params}")
                val result = deviceToolExecutor.executeTool(call)
                Logger.d(Logger.Tags.VM, "Tool result: success=${result.success}, msg=${result.message}")
                
                lastExecutedTools.add(toolKey)
                hasNewToolExecution = true
                
                toolResults.append("${call.name}(${call.params}): ${result.message}\n")
            }
            
            allDisplayText += toolResults.toString()
            
            _streamingMessage.value = _streamingMessage.value?.copy(content = allDisplayText)
            
            if (!hasNewToolExecution) {
                Logger.w(Logger.Tags.VM, "generateWithToolLoop: no new tool executions, ending loop")
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
    
    private fun buildToolResultPrompt(
        template: ChatTemplate,
        originalPrompt: String,
        cleanResponse: String,
        toolResult: String
    ): String {
        val sb = StringBuilder()
        sb.append(originalPrompt)
        
        return when (template) {
            ChatTemplate.CHATML -> {
                sb.append("<|im_end|>\n<|im_start|>user\n")
                sb.append("[Result] ")
                sb.append(toolResult)
                sb.append("<|im_end|>\n<|im_start|>assistant\n")
                sb.toString()
            }
            ChatTemplate.LLAMA3 -> {
                sb.append("<|eot_id|>")
                sb.append("<|start_header_id|>user<|end_header_id|>\n\n")
                sb.append("[Result] ")
                sb.append(toolResult)
                sb.append("<|eot_id|>")
                sb.append("<|start_header_id|>assistant<|end_header_id|>\n\n")
                sb.toString()
            }
            ChatTemplate.PHI -> {
                sb.append("<|end|>\n<|user|>\n")
                sb.append("[Result] ")
                sb.append(toolResult)
                sb.append("<|end|>\n<|assistant|>\n")
                sb.toString()
            }
            ChatTemplate.GEMMA -> {
                sb.append("<end_of_turn>\n<start_of_turn>user\n")
                sb.append("[Result] ")
                sb.append(toolResult)
                sb.append("<end_of_turn>\n<start_of_turn>model\n")
                sb.toString()
            }
            ChatTemplate.MISTRAL -> {
                sb.append("[/INST]\n[INST] [Result] ")
                sb.append(toolResult)
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
        
        val systemContent = buildSystemPrompt()
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
    
    private suspend fun buildSystemPrompt(): String {
        val sb = StringBuilder()
        
        sb.append("【设备操控工具】\n")
        sb.append("你可以使用以下工具操控手机，必须严格按格式调用：\n\n")
        sb.append("格式：[ACTION:工具名]参数[/ACTION]\n\n")
        sb.append("工具列表：\n")
        sb.append("- launch_app: 打开应用，参数=应用名。例：[ACTION:launch_app]抖音[/ACTION]\n")
        sb.append("- click_text: 点击文字，参数=文字内容。例：[ACTION:click_text]确定[/ACTION]\n")
        sb.append("- input_text: 输入文字，参数=内容。例：[ACTION:input_text]你好[/ACTION]\n")
        sb.append("- go_back: 返回，无参数。例：[ACTION:go_back][/ACTION]\n")
        sb.append("- go_home: 回到主页，无参数。例：[ACTION:go_home][/ACTION]\n")
        sb.append("- swipe_up/swipe_down/swipe_left/swipe_right: 滑动屏幕\n\n")
        sb.append("重要提示：\n")
        sb.append("1. 用户说\"打开\"+应用名时，必须使用launch_app工具！\n")
        sb.append("2. 工具调用格式：先用自然语言说明，再用[ACTION:...]标签\n")
        sb.append("3. 例如：我来帮你打开抖音。[ACTION:launch_app]抖音[/ACTION]\n\n")
        
        sb.append("你是NeuralMind AI助手，运行在本地设备上。")
        
        val activeSkillPrompts = try { skillRepository.getActiveSystemPrompts() } catch (e: Exception) { "" }
        if (activeSkillPrompts.isNotBlank()) {
            sb.append(activeSkillPrompts)
        }
        
        val activeMemories = try { memoryRepository.getActiveMemoriesSnapshot() } catch (e: Exception) { emptyList() }
        if (activeMemories.isNotEmpty()) {
            val relevantMemories = activeMemories
                .sortedByDescending { it.importance }
                .take(3)
            if (relevantMemories.isNotEmpty()) {
                sb.append("\n\n【关于用户的信息】\n")
                relevantMemories.forEach { memory ->
                    sb.append("- ${memory.content}\n")
                }
            }
        }
        
        return sb.toString()
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
