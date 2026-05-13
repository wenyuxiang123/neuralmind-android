package com.neuralmind.llama

import android.content.Context
import java.io.File

class LlamaJNI {
    companion object {
        init {
            try {
                System.loadLibrary("neuralmind-jni")
            } catch (e: UnsatisfiedLinkError) {
                e.printStackTrace()
            }
        }

        // Token callback for streaming generation
        internal var _tokenCallback: ((String) -> Unit)? = null

        @JvmStatic
        external fun createEngine(): Long

        @JvmStatic
        external fun destroyEngine(engineId: Long)

        @JvmStatic
        external fun loadModel(engineId: Long, modelPath: String): Boolean

        @JvmStatic
        external fun unloadModel(engineId: Long)

        @JvmStatic
        external fun isModelLoaded(engineId: Long): Boolean

        @JvmStatic
        external fun generate(
            engineId: Long,
            prompt: String,
            maxTokens: Int,
            temperature: Float,
            topP: Float,
            topK: Int,
            repeatPenalty: Float,
            stopSequence: String?
        ): String

        @JvmStatic
        external fun stopGeneration(engineId: Long)

        @JvmStatic
        external fun isGenerating(engineId: Long): Boolean

        @JvmStatic
        external fun getModelInfo(engineId: Long): String

        @JvmStatic
        external fun setParameter(engineId: Long, key: String, value: String)

        @JvmStatic
        external fun getParameter(engineId: Long, key: String): String

        @JvmStatic
        external fun getSupportedModels(): Array<String>
    }

    // Instance method - called by C++ JNI for streaming token callback
    // NOT @JvmStatic because C++ needs a real instance (thiz) to call onToken()
    external fun generateStream(
        engineId: Long,
        prompt: String,
        maxTokens: Int,
        temperature: Float,
        topP: Float,
        topK: Int,
        repeatPenalty: Float,
        stopSequence: String?
    ): String

    // Called by C++ JNI layer for each generated token during streaming
    fun onToken(token: String) {
        _tokenCallback?.invoke(token)
    }
}
