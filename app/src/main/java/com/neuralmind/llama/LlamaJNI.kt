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
    }

    external fun createEngine(): Long

    external fun destroyEngine(engineId: Long)

    external fun loadModel(engineId: Long, modelPath: String): Boolean

    external fun unloadModel(engineId: Long)

    external fun isModelLoaded(engineId: Long): Boolean

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

    external fun stopGeneration(engineId: Long)

    external fun isGenerating(engineId: Long): Boolean

    external fun getModelInfo(engineId: Long): String

    external fun setParameter(engineId: Long, key: String, value: String)

    external fun getParameter(engineId: Long, key: String): String

    external fun getSupportedModels(): Array<String>
}
