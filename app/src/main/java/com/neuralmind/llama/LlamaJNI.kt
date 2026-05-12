package com.neuralmind.llama

import android.content.Context
import java.io.File

class LlamaJNI {
    companion object {
        init {
            System.loadLibrary("llama")
        }
    }

    external fun createEngine(): Long

    external fun destroyEngine(engineId: Long): Boolean

    external fun loadModel(engineId: Long, modelPath: String): Boolean

    external fun unloadModel(engineId: Long): Unit

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

    external fun stopGeneration(engineId: Long): Unit

    external fun getModelInfo(engineId: Long): String
}
