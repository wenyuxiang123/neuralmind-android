package com.neuralmind.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import java.util.UUID

/**
 * TTS 语音合成管理器
 * 使用 Android 原生 TextToSpeech 实现语音输出功能
 */
class TtsManager(private val context: Context) {

    interface TtsCallback {
        fun onSpeakStart()
        fun onSpeakDone()
        fun onError(error: String)
    }

    private var textToSpeech: TextToSpeech? = null
    private var callback: TtsCallback? = null
    private var isReady = false

    /**
     * 设置回调
     */
    fun setCallback(callback: TtsCallback) {
        this.callback = callback
    }

    /**
     * 检查是否正在播放
     */
    fun isSpeaking(): Boolean {
        return textToSpeech?.isSpeaking ?: false
    }

    /**
     * 检查 TTS 是否就绪
     */
    fun isReady(): Boolean {
        return isReady
    }

    /**
     * 初始化 TTS
     */
    init {
        textToSpeech = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = textToSpeech?.setLanguage(Locale.CHINESE)
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    // 中文不支持，尝试默认语言
                    textToSpeech?.setLanguage(Locale.getDefault())
                }
                isReady = true

                // 设置 UtteranceProgressListener
                textToSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        callback?.onSpeakStart()
                    }

                    override fun onDone(utteranceId: String?) {
                        callback?.onSpeakDone()
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        callback?.onError("语音播放错误")
                    }

                    override fun onError(utteranceId: String?, errorCode: Int) {
                        callback?.onError("语音播放错误: $errorCode")
                    }
                })
            } else {
                isReady = false
                callback?.onError("TTS 初始化失败")
            }
        }
    }

    /**
     * 播放文本语音
     * @param text 要播放的文本
     */
    fun speak(text: String) {
        if (!isReady) {
            callback?.onError("TTS 未就绪")
            return
        }

        if (text.isBlank()) {
            return
        }

        // 生成唯一的 utteranceId
        val utteranceId = "neuralmind_tts_${UUID.randomUUID()}"
        textToSpeech?.speak(text, TextToSpeech.QUEUE_ADD, null, utteranceId)
    }

    /**
     * 停止播放
     */
    fun stop() {
        textToSpeech?.stop()
    }

    /**
     * 设置语速
     * @param speed 语速，范围 0.5-2.0，默认 1.0
     */
    fun setSpeed(speed: Float) {
        val validSpeed = speed.coerceIn(0.5f, 2.0f)
        textToSpeech?.setSpeechRate(validSpeed)
    }

    /**
     * 设置音调
     * @param pitch 音调，范围 0.5-2.0，默认 1.0
     */
    fun setPitch(pitch: Float) {
        val validPitch = pitch.coerceIn(0.5f, 2.0f)
        textToSpeech?.setPitch(validPitch)
    }

    /**
     * 销毁资源
     */
    fun destroy() {
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        textToSpeech = null
        isReady = false
        callback = null
    }
}
