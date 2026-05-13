// llama-jni.cpp - JNI interface for llama.cpp integration (llama.cpp b9128 API)
// This file bridges Java/Kotlin code with llama.cpp inference engine

#include <jni.h>
#include <string>
#include <vector>
#include <map>
#include <mutex>
#include <memory>
#include <atomic>
#include <thread>
#include <chrono>
#include <cstring>
#include <android/log.h>

// llama.h is in ${llama_cpp_SOURCE_DIR}/include, which is added via CMakeLists.txt
#include "llama.h"

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
            llama_model_free(model);
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

extern "C" {

// Create a new llama engine instance
JNIEXPORT jlong JNICALL
Java_com_neuralmind_llama_LlamaJNI_createEngine(JNIEnv* env, jobject thiz) {
    // Initialize llama backend (once per process is fine, idempotent)
    llama_backend_init();

    std::lock_guard<std::mutex> lock(engineMutex);

    LlamaEngineInstance* engine = new LlamaEngineInstance();
    jlong engineId = nextEngineId++;

    engineMap[engineId] = engine;

    LOGI("Created new LlamaEngine with ID: %ld", (long)engineId);

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
        LOGI("Destroyed LlamaEngine with ID: %ld", (long)engineId);
    }

    // Free llama backend on last engine destruction
    llama_backend_free();
}

// Load model from file
JNIEXPORT jboolean JNICALL
Java_com_neuralmind_llama_LlamaJNI_loadModel(JNIEnv* env, jobject thiz, jlong engineId, jstring modelPath) {
    std::lock_guard<std::mutex> lock(engineMutex);

    auto it = engineMap.find(engineId);
    if (it == engineMap.end()) {
        LOGE("Engine not found: %ld", (long)engineId);
        return JNI_FALSE;
    }

    LlamaEngineInstance* engine = it->second;

    // Free existing model if any
    if (engine->context) {
        llama_free(engine->context);
        engine->context = nullptr;
    }
    if (engine->model) {
        llama_model_free(engine->model);
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
    // NOTE: In llama.cpp b9128, llama_model_params no longer has n_ctx, n_batch,
    // n_threads, n_threads_batch, or numa. Those belong to llama_context_params.
    llama_model_params params = llama_model_default_params();
    params.n_gpu_layers = 0;  // CPU-only (no GPU offload)

    // Load the model
    engine->model = llama_model_load_from_file(path, params);

    if (!engine->model) {
        LOGE("Failed to load model: %s", path);
        releaseJString(env, modelPath, path);
        return JNI_FALSE;
    }

    // Initialize context (thread/context params are in context_params, not model_params)
    llama_context_params ctx_params = llama_context_default_params();
    ctx_params.n_ctx = 2048;
    ctx_params.n_batch = 512;
    ctx_params.n_threads = 4;
    ctx_params.n_threads_batch = 4;

    engine->context = llama_init_from_model(engine->model, ctx_params);

    if (!engine->context) {
        LOGE("Failed to create context");
        llama_model_free(engine->model);
        engine->model = nullptr;
        releaseJString(env, modelPath, path);
        return JNI_FALSE;
    }

    engine->modelPath = path;

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
        llama_model_free(engine->model);
        engine->model = nullptr;
    }

    engine->modelPath.clear();

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

    // Get engine pointer (copy it so we can release mutex during generation)
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

    // Get vocab from model
    const llama_vocab* vocab = llama_model_get_vocab(engine->model);

    // Tokenize prompt using new vocab-based API
    std::vector<llama_token> promptTokens(llama_vocab_n_tokens(vocab) * 2 + 1);
    int nPromptTokens = llama_tokenize(
        vocab,
        promptStr,
        (int)strlen(promptStr),
        promptTokens.data(),
        (int)promptTokens.size(),
        true,   // add_bos
        false   // parse_special
    );

    releaseJString(env, prompt, promptStr);

    if (nPromptTokens < 0) {
        engine->isGenerating = false;
        return cstringToJString(env, "Error: Failed to tokenize prompt");
    }

    promptTokens.resize(nPromptTokens);

    // Create batch for prompt processing
    llama_batch batch = llama_batch_init((int)promptTokens.size(), 0, 1);
    batch.n_tokens = nPromptTokens;

    for (int i = 0; i < nPromptTokens; i++) {
        batch.token[i] = promptTokens[i];
        batch.pos[i] = i;
        batch.n_seq_id[i] = 1;
        batch.seq_id[i][0] = 0;
        batch.logits[i] = (i == nPromptTokens - 1) ? 1 : 0;
    }

    // Decode prompt
    if (llama_decode(engine->context, batch)) {
        llama_batch_free(batch);
        engine->isGenerating = false;
        return cstringToJString(env, "Error: Failed to decode prompt");
    }

    // Build sampler chain using new sampler API
    llama_sampler_chain_params chainParams = llama_sampler_chain_default_params();
    chainParams.no_perf = true;
    struct llama_sampler* smpl = llama_sampler_chain_init(chainParams);

    // Order matters: temp -> top_k -> top_p -> penalties -> distribution
    llama_sampler_chain_add(smpl, llama_sampler_init_temp(temperature));
    llama_sampler_chain_add(smpl, llama_sampler_init_top_k(topK));
    // llama_sampler_init_top_p takes (float p, size_t min_keep)
    llama_sampler_chain_add(smpl, llama_sampler_init_top_p(topP, 1));
    llama_sampler_chain_add(smpl, llama_sampler_init_penalties(
        topK,             // penalty_last_n
        repeatPenalty,    // penalty_repeat
        0.0f,             // penalty_freq
        0.0f              // penalty_present
    ));
    llama_sampler_chain_add(smpl, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));

    // Generate tokens
    std::string generatedText;
    int nGenerated = 0;
    llama_token newToken = 0;

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

        // Sample next token using sampler chain
        newToken = llama_sampler_sample(smpl, engine->context, -1);

        // Check for EOS using new vocab API
        if (llama_vocab_is_eog(vocab, newToken)) {
            LOGI("Generated EOS token");
            break;
        }

        // Convert token to piece using new vocab API
        char tokenBuf[128] = {0};
        int nWritten = llama_token_to_piece(
            vocab, newToken, tokenBuf, (int)sizeof(tokenBuf), 0, false);
        if (nWritten > 0) {
            generatedText += tokenBuf;
        }

        // Accept token in sampler to update repetition penalty state
        llama_sampler_accept(smpl, newToken);

        // Create batch for single new token and decode
        llama_batch singleBatch = llama_batch_get_one(&newToken, 1);
        if (llama_decode(engine->context, singleBatch)) {
            llama_batch_free(singleBatch);
            break;
        }

        nGenerated++;

        // Small yield to prevent UI blocking
        if (nGenerated % 10 == 0) {
            std::this_thread::sleep_for(std::chrono::milliseconds(1));
        }
    }

    // Cleanup
    llama_sampler_free(smpl);

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

    const llama_vocab* vocab = llama_model_get_vocab(engine->model);

    std::string info = "Model: " + engine->modelPath + "\n";
    info += "Vocab size: " + std::to_string(llama_vocab_n_tokens(vocab)) + "\n";
    info += "Training context size: " + std::to_string(llama_model_n_ctx_train(engine->model)) + "\n";
    info += "Embedding size: " + std::to_string(llama_model_n_embd(engine->model)) + "\n";
    info += "Layers: " + std::to_string(llama_model_n_layer(engine->model)) + "\n";
    info += "Context size: " + std::to_string(llama_n_ctx(engine->context)) + "\n";
    info += "Status: ";
    info += (engine->context ? "Loaded" : "Not loaded");

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
    const char* models[] = {
        "llama-2-7b-chat",
        "llama-2-13b-chat",
        "llama-3-8b",
        "llama-3.1-8b",
        "mistral-7b",
        "phi-2",
        "gemma-2b"
    };

    int numModels = (int)(sizeof(models) / sizeof(models[0]));

    jclass stringClass = env->FindClass("java/lang/String");
    jobjectArray result = env->NewObjectArray(numModels, stringClass, nullptr);

    for (int i = 0; i < numModels; i++) {
        env->SetObjectArrayElement(result, i, env->NewStringUTF(models[i]));
    }

    return result;
}

} // extern "C"
