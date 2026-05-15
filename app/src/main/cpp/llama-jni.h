#pragma once
#include <jni.h>

// JNI function declarations matching llama-jni.cpp
// Note: native methods are declared in LlamaJNI.kt companion object without @JvmStatic
extern "C" {
    JNIEXPORT jlong JNICALL Java_com_neuralmind_llama_LlamaJNI_createEngine(JNIEnv* env, jobject thiz);
    JNIEXPORT void JNICALL Java_com_neuralmind_llama_LlamaJNI_destroyEngine(JNIEnv* env, jobject thiz, jlong engineId);
    JNIEXPORT jboolean JNICALL Java_com_neuralmind_llama_LlamaJNI_loadModel(JNIEnv* env, jobject thiz, jlong engineId, jstring modelPath);
    JNIEXPORT void JNICALL Java_com_neuralmind_llama_LlamaJNI_unloadModel(JNIEnv* env, jobject thiz, jlong engineId);
    JNIEXPORT jboolean JNICALL Java_com_neuralmind_llama_LlamaJNI_isModelLoaded(JNIEnv* env, jobject thiz, jlong engineId);
    JNIEXPORT jstring JNICALL Java_com_neuralmind_llama_LlamaJNI_generate(
            JNIEnv* env, jobject thiz, jlong engineId, jstring prompt,
            jint maxTokens, jfloat temperature, jfloat topP, jint topK,
            jfloat repeatPenalty, jstring stopSequence);
    JNIEXPORT jstring JNICALL Java_com_neuralmind_llama_LlamaJNI_generateStream(
            JNIEnv* env, jobject thiz, jlong engineId, jstring prompt,
            jint maxTokens, jfloat temperature, jfloat topP, jint topK,
            jfloat repeatPenalty, jstring stopSequence);
    JNIEXPORT void JNICALL Java_com_neuralmind_llama_LlamaJNI_clearPromptCache(JNIEnv* env, jobject thiz, jlong engineId);
    JNIEXPORT jboolean JNICALL Java_com_neuralmind_llama_LlamaJNI_saveKvState(JNIEnv* env, jobject thiz, jlong engineId, jstring filePath);
    JNIEXPORT jboolean JNICALL Java_com_neuralmind_llama_LlamaJNI_loadKvState(JNIEnv* env, jobject thiz, jlong engineId, jstring filePath);
    JNIEXPORT jfloatArray JNICALL Java_com_neuralmind_llama_LlamaJNI_extractFingerprint(JNIEnv* env, jobject thiz, jlong engineId, jstring text);
    JNIEXPORT void JNICALL Java_com_neuralmind_llama_LlamaJNI_stopGeneration(JNIEnv* env, jobject thiz, jlong engineId);
    JNIEXPORT jboolean JNICALL Java_com_neuralmind_llama_LlamaJNI_isGenerating(JNIEnv* env, jobject thiz, jlong engineId);
    JNIEXPORT jstring JNICALL Java_com_neuralmind_llama_LlamaJNI_getModelInfo(JNIEnv* env, jobject thiz, jlong engineId);
    JNIEXPORT void JNICALL Java_com_neuralmind_llama_LlamaJNI_setParameter(JNIEnv* env, jobject thiz, jlong engineId, jstring key, jstring value);
    JNIEXPORT jstring JNICALL Java_com_neuralmind_llama_LlamaJNI_getParameter(JNIEnv* env, jobject thiz, jlong engineId, jstring key);
    JNIEXPORT jobjectArray JNICALL Java_com_neuralmind_llama_LlamaJNI_getSupportedModels(JNIEnv* env, jobject thiz);
}
