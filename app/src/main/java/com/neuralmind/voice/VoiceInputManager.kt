package com.neuralmind.voice

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.Locale

/**
 * 语音识别管理器
 * 使用 Android 原生 SpeechRecognizer 实现语音输入功能
 */
class VoiceInputManager(private val context: Context) {

    interface VoiceCallback {
        fun onResult(text: String)           // 识别完成
        fun onPartialResult(text: String)      // 部分识别结果
        fun onError(error: String)            // 错误
        fun onStateChanged(state: State)      // 状态变化
    }

    enum class State {
        IDLE, LISTENING, PROCESSING
    }

    private var speechRecognizer: SpeechRecognizer? = null
    private var callback: VoiceCallback? = null
    private var currentState: State = State.IDLE
    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * 设置回调
     */
    fun setCallback(callback: VoiceCallback) {
        this.callback = callback
    }

    /**
     * 检查语音识别是否可用
     */
    fun isAvailable(): Boolean {
        return SpeechRecognizer.isRecognitionAvailable(context)
    }

    /**
     * 是否正在监听
     */
    fun isListening(): Boolean {
        return currentState == State.LISTENING || currentState == State.PROCESSING
    }

    /**
     * 开始语音监听
     */
    fun startListening() {
        // 如果已经在监听，先停止
        if (isListening()) {
            stopListening()
        }

        // 检查是否可用
        if (!isAvailable()) {
            callback?.onError("当前设备不支持语音识别")
            return
        }

        // 在主线程创建 SpeechRecognizer
        mainHandler.post {
            try {
                // 先销毁旧的 recognizer
                speechRecognizer?.destroy()
                speechRecognizer = null

                // 创建新的 SpeechRecognizer
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
                speechRecognizer?.setRecognitionListener(recognitionListener)

                // 设置 Intent
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.CHINESE.toString())
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, Locale.CHINESE.toString())
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                }

                // 启动监听
                speechRecognizer?.startListening(intent)
                currentState = State.LISTENING
                callback?.onStateChanged(State.LISTENING)

            } catch (e: Exception) {
                currentState = State.IDLE
                callback?.onError("语音识别启动失败: ${e.message}")
                callback?.onStateChanged(State.IDLE)
            }
        }
    }

    /**
     * 停止语音监听
     */
    fun stopListening() {
        mainHandler.post {
            try {
                speechRecognizer?.stopListening()
            } catch (e: Exception) {
                // 忽略停止时的错误
            }
            currentState = State.IDLE
            callback?.onStateChanged(State.IDLE)
        }
    }

    /**
     * 销毁资源
     */
    fun destroy() {
        mainHandler.post {
            speechRecognizer?.destroy()
            speechRecognizer = null
            currentState = State.IDLE
            callback = null
        }
    }

    /**
     * RecognitionListener 实现
     */
    private val recognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: android.os.Bundle?) {
            currentState = State.LISTENING
            callback?.onStateChanged(State.LISTENING)
        }

        override fun onBeginningOfSpeech() {
            currentState = State.LISTENING
        }

        override fun onRmsChanged(rmsdB: Float) {
            // 可以在这里处理音量变化
        }

        override fun onBufferReceived(buffer: ByteArray?) {
        }

        override fun onEndOfSpeech() {
            currentState = State.PROCESSING
            callback?.onStateChanged(State.PROCESSING)
        }

        override fun onError(error: Int) {
            val errorMessage = when (error) {
                SpeechRecognizer.ERROR_AUDIO -> "音频录制错误"
                SpeechRecognizer.ERROR_CLIENT -> "客户端错误"
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "权限不足"
                SpeechRecognizer.ERROR_NETWORK -> "网络错误"
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "网络超时"
                SpeechRecognizer.ERROR_NO_MATCH -> "没有识别到语音，请重试"
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "识别服务忙碌，请稍后重试"
                SpeechRecognizer.ERROR_SERVER -> "服务器错误"
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "没有听到语音，请重试"
                else -> "未知错误"
            }
            currentState = State.IDLE
            callback?.onError(errorMessage)
            callback?.onStateChanged(State.IDLE)
        }

        override fun onResults(results: android.os.Bundle?) {
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val result = matches?.firstOrNull() ?: ""
            currentState = State.IDLE
            if (result.isNotEmpty()) {
                callback?.onResult(result)
            }
            callback?.onStateChanged(State.IDLE)
        }

        override fun onPartialResults(partialResults: android.os.Bundle?) {
            val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val partial = matches?.firstOrNull() ?: ""
            if (partial.isNotEmpty()) {
                callback?.onPartialResult(partial)
            }
        }

        override fun onEvent(eventType: Int, params: android.os.Bundle?) {
        }
    }
}
