// llama-jni.cpp - JNI interface for llama.cpp integration
// This file bridges Java/Kotlin code with llama.cpp inference engine

#include <jni.h>
#include <string>
#include <vector>
#include <map>
#include <mutex>
#include <memory>
#include <atomic>
#include <android/log.h>

// Include llama.cpp headers
#include "llama/llama.h"

// Logging macros
#define LOG_TAG "LlamaJNI"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// Engine instance structure
struct LlamaEngineInstance {
    llama_model * model = nullptr;
    llama_context * context = nullptr;
    std::atomic<bool> isGenerating{false};
    std::atomic<bool> stopRequested{false};
    std::string modelPath;
    std::string modelName;
    
    // Inference parameters
    int maxTokens = 512;
    float temperature = 0.7f;
    float topP = 0.9f;
    int topK = 40;
    float repeatPenalty = 1.1f;
    std::string stopSequence;
    
    ~LlamaEngineInstance() {
        if (context) {
            llama_free(context);
            context = nullptr;
        }
        if (model) {
            llama_free_model(model);
            model = nullptr;
        }
    }
};

// Global engine map and mutex
static std::map<jlong, LlamaEngineInstance*> engineMap;
static std::mutex engineMutex;
static jlong nextEngineId = 1;

// Helper: Convert Java string to C string
static const char* jstringToCString(JNIEnv* env, jstring jstr) {
    if (!jstr) return nullptr;
    return env->GetStringUTFChars(jstr, nullptr);
}

// Helper: Release Java string
static void releaseJString(JNIEnv* env, jstring jstr, const char* cstr) {
    if (jstr && cstr) {
        env->ReleaseStringUTFChars(jstr, cstr);
    }
}

// Helper: Create Java string from C string
static jstring cstringToJString(JNIEnv* env, const std::string& str) {
    return env->NewStringUTF(str.c_str());
}

// JNI Functions
extern "C" {

// Create a new llama engine instance
JNIEXPORT jlong JNICALL
Java_com_neuralmind_llama_LlamaJNI_createEngine(JNIEnv* env, jobject thiz) {
    std::lock_guard<std::mutex> lock(engineMutex);
    
    LlamaEngineInstance* engine = new LlamaEngineInstance();
    jlong engineId = nextEngineId++;
    
    engineMap[engineId] = engine;
    
    LOGI("Created new LlamaEngine with ID: %lld", (long long)engineId);
    
    return engineId;
}

// Destroy engine instance
JNIEXPORT void JNICALL
Java_com_neuralmind_llama_LlamaJNI_destroyEngine(JNIEnv* env, jobject thiz, jlong engineId) {
    std::lock_guard<std::mutex> lock(engineMutex);
    
    auto it = engineMap.find(engineId);
    if (it != engineMap.end()) {
        delete it->second;
        engineMap.erase(it);
        LOGI("Destroyed LlamaEngine with ID: %lld", (long long)engineId);
    }
}

// Load model from file
JNIEXPORT jboolean JNICALL
Java_com_neuralmind_llama_LlamaJNI_loadModel(JNIEnv* env, jobject thiz, jlong engineId, jstring modelPath) {
    std::lock_guard<std::mutex> lock(engineMutex);
    
    auto it = engineMap.find(engineId);
    if (it == engineMap.end()) {
        LOGE("Engine not found: %lld", (long long)engineId);
        return JNI_FALSE;
    }
    
    LlamaEngineInstance* engine = it->second;
    
    // Free existing model if any
    if (engine->context) {
        llama_free(engine->context);
        engine->context = nullptr;
    }
    if (engine->model) {
        llama_free_model(engine->model);
        engine->model = nullptr;
    }
    
    // Get model path
    const char* path = jstringToCString(env, modelPath);
    if (!path) {
        LOGE("Invalid model path");
        return JNI_FALSE;
    }
    
    LOGI("Loading model from: %s", path);
    
    // Set model parameters
    llama_model_params params = llama_model_default_params();
    params.n_ctx = 2048;  // Context size
    params.n_batch = 512;  // Batch size for prompt processing
    params.n_threads = 4;   // CPU threads
    params.n_threads_batch = 4;
    params.numa = false;
    
    // Load the model
    engine->model = llama_load_model_from_file(path, params);
    
    if (!engine->model) {
        LOGE("Failed to load model: %s", path);
        releaseJString(env, modelPath, path);
        return JNI_FALSE;
    }
    
    // Initialize context
    llama_context_params ctx_params = llama_context_default_params();
    ctx_params.n_ctx = 2048;
    ctx_params.n_batch = 512;
    ctx_params.n_threads = 4;
    ctx_params.n_threads_batch = 4;
    
    engine->context = llama_init_from_model(engine->model, ctx_params);
    
    if (!engine->context) {
        LOGE("Failed to create context");
        llama_free_model(engine->model);
        engine->model = nullptr;
        releaseJString(env, modelPath, path);
        return JNI_FALSE;
    }
    
    engine->modelPath = path;
    engine->modelName = path;
    
    releaseJString(env, modelPath, path);
    
    LOGI("Model loaded successfully: %s", engine->modelPath.c_str());
    
    return JNI_TRUE;
}

// Unload model
JNIEXPORT void JNICALL
Java_com_neuralmind_llama_LlamaJNI_unloadModel(JNIEnv* env, jobject thiz, jlong engineId) {
    std::lock_guard<std::mutex> lock(engineMutex);
    
    auto it = engineMap.find(engineId);
    if (it == engineMap.end()) {
        return;
    }
    
    LlamaEngineInstance* engine = it->second;
    
    if (engine->context) {
        llama_free(engine->context);
        engine->context = nullptr;
    }
    if (engine->model) {
        llama_free_model(engine->model);
        engine->model = nullptr;
    }
    
    engine->modelPath.clear();
    engine->modelName.clear();
    
    LOGI("Model unloaded");
}

// Check if model is loaded
JNIEXPORT jboolean JNICALL
Java_com_neuralmind_llama_LlamaJNI_isModelLoaded(JNIEnv* env, jobject thiz, jlong engineId) {
    std::lock_guard<std::mutex> lock(engineMutex);
    
    auto it = engineMap.find(engineId);
    if (it == engineMap.end()) {
        return JNI_FALSE;
    }
    
    return (it->second->model != nullptr && it->second->context != nullptr) ? JNI_TRUE : JNI_FALSE;
}

// Generate text
JNIEXPORT jstring JNICALL
Java_com_neuralmind_llama_LlamaJNI_generate(
        JNIEnv* env, 
        jobject thiz, 
        jlong engineId, 
        jstring prompt,
        jint maxTokens, 
        jfloat temperature,
        jfloat topP, 
        jint topK, 
        jfloat repeatPenalty,
        jstring stopSequence) {
    
    // Get engine
    engineMutex.lock();
    auto it = engineMap.find(engineId);
    if (it == engineMap.end()) {
        engineMutex.unlock();
        return cstringToJString(env, "Error: Engine not found");
    }
    
    LlamaEngineInstance* engine = it->second;
    
    if (!engine->model || !engine->context) {
        engineMutex.unlock();
        return cstringToJString(env, "Error: Model not loaded");
    }
    
    // Mark as generating
    engine->isGenerating = true;
    engine->stopRequested = false;
    
    // Update parameters
    engine->temperature = temperature;
    engine->topP = topP;
    engine->topK = topK;
    engine->repeatPenalty = repeatPenalty;
    engine->maxTokens = maxTokens;
    
    const char* stopSeq = jstringToCString(env, stopSequence);
    if (stopSeq) {
        engine->stopSequence = stopSeq;
        releaseJString(env, stopSequence, stopSeq);
    } else {
        engine->stopSequence.clear();
    }
    
    const char* promptStr = jstringToCString(env, prompt);
    if (!promptStr) {
        engine->isGenerating = false;
        engineMutex.unlock();
        return cstringToJString(env, "Error: Invalid prompt");
    }
    
    engineMutex.unlock();
    
    LOGI("Generating for prompt (len=%d), maxTokens=%d, temp=%.2f", 
         (int)strlen(promptStr), maxTokens, temperature);
    
    // Tokenize prompt
    std::vector<llama_token> promptTokens(2048);
    int nPromptTokens = llama_tokenize(
        engine->model, 
        promptStr, 
        promptTokens.data(), 
        promptTokens.size(),
        false,  // add_special
        false   // parse_special
    );
    
    releaseJString(env, prompt, promptStr);
    
    if (nPromptTokens < 0) {
        engine->isGenerating = false;
        return cstringToJString(env, "Error: Failed to tokenize prompt");
    }
    
    promptTokens.resize(nPromptTokens);
    
    // Process prompt tokens
    for (llama_token token : promptTokens) {
        llama_decode(engine->context, token);
    }
    
    // Generate tokens
    std::string generatedText;
    llama_token lastToken = 0;
    int nGenerated = 0;
    
    while (nGenerated < engine->maxTokens) {
        // Check stop condition
        if (engine->stopRequested) {
            LOGI("Generation stopped by request");
            break;
        }
        
        // Check for stop sequence
        if (!engine->stopSequence.empty()) {
            std::string currentOutput = generatedText;
            size_t stopPos = currentOutput.find(engine->stopSequence);
            if (stopPos != std::string::npos) {
                generatedText = currentOutput.substr(0, stopPos);
                LOGI("Generation stopped by sequence");
                break;
            }
        }
        
        // Sample next token
        llama_token newToken = llama_sample_token(engine->context);
        
        // Check for EOS
        if (newToken == llama_token_eos(engine->model)) {
            LOGI("Generated EOS token");
            break;
        }
        
        // Convert token to text
        char* tokenStr = llama_token_to_piece(engine->model, newToken);
        if (tokenStr) {
            generatedText += tokenStr;
        }
        
        // Add to context for next iteration
        llama_decode(engine->context, newToken);
        
        lastToken = newToken;
        nGenerated++;
        
        // Small delay to prevent UI blocking (adjust as needed)
        if (nGenerated % 10 == 0) {
            std::this_thread::sleep_for(std::chrono::milliseconds(1));
        }
    }
    
    engine->isGenerating = false;
    
    LOGI("Generated %d tokens", nGenerated);
    
    return cstringToJString(env, generatedText);
}

// Stop generation
JNIEXPORT void JNICALL
Java_com_neuralmind_llama_LlamaJNI_stopGeneration(JNIEnv* env, jobject thiz, jlong engineId) {
    std::lock_guard<std::mutex> lock(engineMutex);
    
    auto it = engineMap.find(engineId);
    if (it != engineMap.end()) {
        it->second->stopRequested = true;
        LOGI("Stop generation requested");
    }
}

// Check if generating
JNIEXPORT jboolean JNICALL
Java_com_neuralmind_llama_LlamaJNI_isGenerating(JNIEnv* env, jobject thiz, jlong engineId) {
    std::lock_guard<std::mutex> lock(engineMutex);
    
    auto it = engineMap.find(engineId);
    if (it == engineMap.end()) {
        return JNI_FALSE;
    }
    
    return it->second->isGenerating ? JNI_TRUE : JNI_FALSE;
}

// Get model info
JNIEXPORT jstring JNICALL
Java_com_neuralmind_llama_LlamaJNI_getModelInfo(JNIEnv* env, jobject thiz, jlong engineId) {
    std::lock_guard<std::mutex> lock(engineMutex);
    
    auto it = engineMap.find(engineId);
    if (it == engineMap.end()) {
        return cstringToJString(env, "Error: Engine not found");
    }
    
    LlamaEngineInstance* engine = it->second;
    
    if (!engine->model) {
        return cstringToJString(env, "No model loaded");
    }
    
    std::string info = "Model: " + engine->modelPath + "\n";
    info += "Vocab size: " + std::to_string(llama_model_n_vocab(engine->model)) + "\n";
    info += "Context size: " + std::to_string(llama_model_n_ctx(engine->model)) + "\n";
    info += "Embedding size: " + std::to_string(llama_model_n_embd(engine->model)) + "\n";
    info += "Layers: " + std::to_string(llama_model_n_layer(engine->model)) + "\n";
    info += "Status: " + (engine->context ? "Loaded" : "Not loaded");
    
    return cstringToJString(env, info);
}

// Set parameter
JNIEXPORT void JNICALL
Java_com_neuralmind_llama_LlamaJNI_setParameter(
        JNIEnv* env, 
        jobject thiz, 
        jlong engineId, 
        jstring key, 
        jstring value) {
    
    std::lock_guard<std::mutex> lock(engineMutex);
    
    auto it = engineMap.find(engineId);
    if (it == engineMap.end()) {
        return;
    }
    
    LlamaEngineInstance* engine = it->second;
    
    const char* keyStr = jstringToCString(env, key);
    const char* valueStr = jstringToCString(env, value);
    
    if (keyStr && valueStr) {
        // Update inference parameters
        if (strcmp(keyStr, "maxTokens") == 0) {
            engine->maxTokens = atoi(valueStr);
        } else if (strcmp(keyStr, "temperature") == 0) {
            engine->temperature = atof(valueStr);
        } else if (strcmp(keyStr, "topP") == 0) {
            engine->topP = atof(valueStr);
        } else if (strcmp(keyStr, "topK") == 0) {
            engine->topK = atoi(valueStr);
        } else if (strcmp(keyStr, "repeatPenalty") == 0) {
            engine->repeatPenalty = atof(valueStr);
        }
        
        LOGI("Set parameter: %s = %s", keyStr, valueStr);
    }
    
    releaseJString(env, key, keyStr);
    releaseJString(env, value, valueStr);
}

// Get parameter
JNIEXPORT jstring JNICALL
Java_com_neuralmind_llama_LlamaJNI_getParameter(
        JNIEnv* env, 
        jobject thiz, 
        jlong engineId, 
        jstring key) {
    
    std::lock_guard<std::mutex> lock(engineMutex);
    
    auto it = engineMap.find(engineId);
    if (it == engineMap.end()) {
        return cstringToJString(env, "");
    }
    
    LlamaEngineInstance* engine = it->second;
    
    const char* keyStr = jstringToCString(env, key);
    std::string value;
    
    if (keyStr) {
        if (strcmp(keyStr, "maxTokens") == 0) {
            value = std::to_string(engine->maxTokens);
        } else if (strcmp(keyStr, "temperature") == 0) {
            value = std::to_string(engine->temperature);
        } else if (strcmp(keyStr, "topP") == 0) {
            value = std::to_string(engine->topP);
        } else if (strcmp(keyStr, "topK") == 0) {
            value = std::to_string(engine->topK);
        } else if (strcmp(keyStr, "repeatPenalty") == 0) {
            value = std::to_string(engine->repeatPenalty);
        }
    }
    
    releaseJString(env, key, keyStr);
    
    return cstringToJString(env, value);
}

// Get supported models
JNIEXPORT jobjectArray JNICALL
Java_com_neuralmind_llama_LlamaJNI_getSupportedModels(JNIEnv* env, jobject thiz) {
    // Supported model types/architectures
    const char* models[] = {
        "llama-2-7b-chat",
        "llama-2-13b-chat", 
        "llama-3-8b",
        "llama-3.1-8b",
        "mistral-7b",
        "phi-2",
        "gemma-2b"
    };
    
    int numModels = sizeof(models) / sizeof(models[0]);
    
    // Create Java String array
    jclass stringClass = env->FindClass("java/lang/String");
    jobjectArray result = env->NewObjectArray(numModels, stringClass, nullptr);
    
    for (int i = 0; i < numModels; i++) {
        env->SetObjectArrayElement(result, i, env->NewStringUTF(models[i]));
    }
    
    return result;
}

} // extern "C"
