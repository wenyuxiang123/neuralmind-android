#pragma once
#include <jni.h>
#include <string>
#include <memory>
#include <vector>
#include <functional>

class LlamaContext {
public:
    void* model = nullptr;
    void* ctx = nullptr;
    bool is_loaded = false;
};

class LlamaEngine {
private:
    std::shared_ptr<LlamaContext> context;
    std::function<void(const std::string&)> token_callback;
    std::atomic<bool> is_generating;

public:
    LlamaEngine();
    ~LlamaEngine();

    bool load_model(const std::string& model_path);
    void unload_model();
    bool is_model_loaded() const;

    void set_token_callback(std::function<void(const std::string&)> callback);
    std::string generate(const std::string& prompt, int max_tokens, float temperature);
    void stop_generation();

    std::string get_model_info() const;
};

extern "C" {
    JNIEXPORT jlong JNICALL Java_com_neuralmind_llama_LlamaJNI_createEngine(JNIEnv* env, jobject thiz);
    JNIEXPORT void JNICALL Java_com_neuralmind_llama_LlamaJNI_destroyEngine(JNIEnv* env, jobject thiz, jlong engine_ptr);
    JNIEXPORT jboolean JNICALL Java_com_neuralmind_llama_LlamaJNI_loadModel(JNIEnv* env, jobject thiz, jlong engine_ptr, jstring model_path);
    JNIEXPORT void JNICALL Java_com_neuralmind_llama_LlamaJNI_unloadModel(JNIEnv* env, jobject thiz, jlong engine_ptr);
    JNIEXPORT jboolean JNICALL Java_com_neuralmind_llama_LlamaJNI_isModelLoaded(JNIEnv* env, jobject thiz, jlong engine_ptr);
    JNIEXPORT jstring JNICALL Java_com_neuralmind_llama_LlamaJNI_generate(JNIEnv* env, jobject thiz, jlong engine_ptr, jstring prompt, jint max_tokens, jfloat temperature);
    JNIEXPORT void JNICALL Java_com_neuralmind_llama_LlamaJNI_stopGeneration(JNIEnv* env, jobject thiz, jlong engine_ptr);
    JNIEXPORT jstring JNICALL Java_com_neuralmind_llama_LlamaJNI_getModelInfo(JNIEnv* env, jobject thiz, jlong engine_ptr);
}
