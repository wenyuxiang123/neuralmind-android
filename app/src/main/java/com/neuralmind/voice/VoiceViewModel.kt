package com.neuralmind.voice

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.neuralmind.core.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 语音状态管理 ViewModel
 * 统一管理语音输入（STT）和语音输出（TTS）功能
 */
class VoiceViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "VoiceViewModel"
    }

    // 语音输入状态
    private val _voiceState = MutableStateFlow<VoiceState>(VoiceState.Idle)
    val voiceState: StateFlow<VoiceState> = _voiceState.asStateFlow()

    // 识别到的文本
    private val _recognizedText = MutableStateFlow("")
    val recognizedText: StateFlow<String> = _recognizedText.asStateFlow()

    // 部分识别结果
    private val _partialText = MutableStateFlow("")
    val partialText: StateFlow<String> = _partialText.asStateFlow()

    // TTS 状态
    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    // TTS 开关状态
    private val _ttsEnabled = MutableStateFlow(true)
    val ttsEnabled: StateFlow<Boolean> = _ttsEnabled.asStateFlow()

    // 语音管理器
    private val voiceInputManager = VoiceInputManager(application)
    private val ttsManager = TtsManager(application)

    // 回调上一次识别的文本，用于检测新结果
    private var lastRecognizedText = ""

    init {
        // 设置语音输入回调
        voiceInputManager.setCallback(object : VoiceInputManager.VoiceCallback {
            override fun onResult(text: String) {
                Logger.d(TAG, "onResult: $text")
                _recognizedText.value = text
                _partialText.value = ""
            }

            override fun onPartialResult(text: String) {
                Logger.d(TAG, "onPartialResult: $text")
                _partialText.value = text
            }

            override fun onError(error: String) {
                Logger.e(TAG, "onError: $error")
                _voiceState.value = VoiceState.Error(error)
                _partialText.value = ""
            }

            override fun onStateChanged(state: VoiceInputManager.State) {
                Logger.d(TAG, "onStateChanged: $state")
                when (state) {
                    VoiceInputManager.State.IDLE -> _voiceState.value = VoiceState.Idle
                    VoiceInputManager.State.LISTENING -> _voiceState.value = VoiceState.Listening
                    VoiceInputManager.State.PROCESSING -> _voiceState.value = VoiceState.Processing
                }
            }
        })

        // 设置 TTS 回调
        ttsManager.setCallback(object : TtsManager.TtsCallback {
            override fun onSpeakStart() {
                _isSpeaking.value = true
            }

            override fun onSpeakDone() {
                _isSpeaking.value = false
            }

            override fun onError(error: String) {
                Logger.e(TAG, "TTS error: $error")
                _isSpeaking.value = false
            }
        })

        // 监听识别结果变化，自动清空并更新 lastRecognizedText
        viewModelScope.launch {
            _recognizedText.collect { newText ->
                if (newText.isNotEmpty() && newText != lastRecognizedText) {
                    lastRecognizedText = newText
                    Logger.d(TAG, "recognizedText changed: $newText")
                }
            }
        }
    }

    /**
     * 开始语音监听
     */
    fun startListening() {
        Logger.d(TAG, "startListening")
        _voiceState.value = VoiceState.Listening
        _recognizedText.value = ""
        _partialText.value = ""
        lastRecognizedText = ""
        voiceInputManager.startListening()
    }

    /**
     * 停止语音监听
     */
    fun stopListening() {
        Logger.d(TAG, "stopListening")
        voiceInputManager.stopListening()
    }

    /**
     * 切换监听状态
     */
    fun toggleListening() {
        when (_voiceState.value) {
            is VoiceState.Idle, is VoiceState.Error -> startListening()
            is VoiceState.Listening, is VoiceState.Processing -> stopListening()
        }
    }

    /**
     * 播放文本语音
     * @param text 要播放的文本
     */
    fun speakText(text: String) {
        if (!_ttsEnabled.value) {
            Logger.d(TAG, "TTS is disabled, skipping speak")
            return
        }
        Logger.d(TAG, "speakText: $text")
        ttsManager.speak(text)
    }

    /**
     * 停止播放
     */
    fun stopSpeaking() {
        Logger.d(TAG, "stopSpeaking")
        ttsManager.stop()
    }

    /**
     * 切换 TTS 开关
     */
    fun toggleTts() {
        _ttsEnabled.value = !_ttsEnabled.value
        Logger.d(TAG, "TTS enabled: ${_ttsEnabled.value}")
        if (!_ttsEnabled.value) {
            stopSpeaking()
        }
    }

    /**
     * 检查语音识别是否可用
     */
    fun isVoiceInputAvailable(): Boolean {
        return voiceInputManager.isAvailable()
    }

    /**
     * 检查是否正在监听
     */
    fun isListening(): Boolean {
        return voiceInputManager.isListening()
    }

    override fun onCleared() {
        super.onCleared()
        Logger.d(TAG, "onCleared")
        voiceInputManager.destroy()
        ttsManager.destroy()
    }
}

/**
 * 语音状态 sealed class
 */
sealed class VoiceState {
    object Idle : VoiceState()
    object Listening : VoiceState()
    object Processing : VoiceState()
    data class Error(val message: String) : VoiceState()
}
