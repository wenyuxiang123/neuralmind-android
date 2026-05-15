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
#include <unistd.h>
#include <fstream>
#include <sstream>
#include <algorithm>
#include <errno.h>
// llama.h is in ${llama_cpp_SOURCE_DIR}/include, which is added via CMakeLists.txt
#include "llama.h"
// ggml.h provides ggml_threadpool API for CPU affinity
#include "ggml.h"
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
    ggml_threadpool_t threadpool = nullptr;
    ggml_threadpool_t threadpool_batch = nullptr;
    std::atomic<bool> isGenerating{false};
    std::atomic<bool> stopRequested{false};
    std::string modelPath;
    // KV cache prefix matching fields
    std::vector<llama_token> cached_prompt_tokens;
    bool has_cached_prompt = false;
    // Inference parameters
    int maxTokens = 512;
    float temperature = 0.7f;
    float topP = 0.9f;
    int topK = 40;
    float repeatPenalty = 1.1f;
    std::string stopSequence;
    ~LlamaEngineInstance() {
        if (context) {
            llama_detach_threadpool(context);
            llama_free(context);
            context = nullptr;
        }
        if (threadpool_batch) {
            ggml_threadpool_free(threadpool_batch);
            threadpool_batch = nullptr;
        }
        if (threadpool) {
            ggml_threadpool_free(threadpool);
            threadpool = nullptr;
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
// Create a ggml_threadpool with cpumask set to big (performance) CPU cores
static ggml_threadpool_t create_big_core_threadpool(int n_threads) {
    int n_cpus = sysconf(_SC_NPROCESSORS_CONF);
    if (n_cpus <= 0) {
        LOGW("Cannot determine CPU count for threadpool cpumask");
        return nullptr;
    }
    // Read max frequency for each CPU from sysfs
    struct CpuFreqInfo {
        int id;
        long max_freq_khz;
    };
    std::vector<CpuFreqInfo> cpus;
    long highest_freq = 0;
    for (int i = 0; i < n_cpus; i++) {
        CpuFreqInfo info;
        info.id = i;
        info.max_freq_khz = 0;
        std::string freq_path = "/sys/devices/system/cpu/cpu" + std::to_string(i) + "/cpufreq/cpuinfo_max_freq";
        std::ifstream freq_file(freq_path);
        if (freq_file.is_open()) {
            freq_file >> info.max_freq_khz;
        }
        if (info.max_freq_khz > highest_freq) {
            highest_freq = info.max_freq_khz;
        }
        cpus.push_back(info);
    }
    // Build cpumask: only big cores (freq >= 80% of maximum)
    struct ggml_threadpool_params tpp = ggml_threadpool_params_default(n_threads);
    int n_big = 0;
    if (highest_freq > 0) {
        long threshold = highest_freq * 80 / 100;
        for (const auto& cpu : cpus) {
            if (cpu.max_freq_khz >= threshold) {
                tpp.cpumask[cpu.id] = true;
                n_big++;
                LOGI("Threadpool big core: CPU %d (freq=%ld kHz)", cpu.id, cpu.max_freq_khz);
            }
        }
    }
    if (n_big == 0) {
        // Fallback: assume upper half of CPUs are big cores
        LOGW("Cannot read CPU frequencies, using fallback (upper half CPUs)");
        int start = n_cpus / 2;
        for (int i = start; i < n_cpus && i < GGML_MAX_N_THREADS; i++) {
            tpp.cpumask[i] = true;
            n_big++;
        }
    }
    if (n_big == 0) {
        LOGW("No big cores detected, skipping custom threadpool");
        return nullptr;
    }
    // Use strict CPU placement to ensure each thread is pinned to a specific big core
    tpp.strict_cpu = true;
    // Set polling for better throughput during inference
    tpp.poll = 50;
    LOGI("Creating threadpool with %d threads on %d big cores (strict=%d, poll=%d)",
         n_threads, n_big, tpp.strict_cpu, tpp.poll);
    ggml_threadpool_t threadpool = ggml_threadpool_new(&tpp);
    if (!threadpool) {
        LOGE("Failed to create threadpool");
        return nullptr;
    }
    LOGI("Threadpool created successfully");
    return threadpool;
}
// Helper: Calculate dynamic n_ctx based on model parameters
static int calculate_dynamic_n_ctx(const llama_model* model) {
    if (!model) return 1024;
    // llama_model_n_params returns number of parameters (e.g., 5600000000 for 5.6B)
    const int64_t n_params = llama_model_n_params(model);
    const double params_billions = n_params / 1e9;
    LOGI("Model parameters: %.1fB, calculating n_ctx", params_billions);
    if (params_billions <= 1.0) {
        return 4096;  // 1B and smaller: larger context
    } else if (params_billions <= 3.0) {
        return 2048;  // 1B-3B: medium context
    } else {
        return 1024;  // >3B: standard context
    }
}
// Helper: Find common prefix length between two token vectors
static int find_common_prefix_len(const std::vector<llama_token>& a, const std::vector<llama_token>& b) {
    int min_len = std::min(a.size(), b.size());
    int common_len = 0;
    for (int i = 0; i < min_len; i++) {
        if (a[i] == b[i]) {
            common_len++;
        } else {
            break;
        }
    }
    return common_len;
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
        llama_detach_threadpool(engine->context);
        llama_free(engine->context);
        engine->context = nullptr;
    }
    if (engine->threadpool_batch) {
        ggml_threadpool_free(engine->threadpool_batch);
        engine->threadpool_batch = nullptr;
    }
    if (engine->threadpool) {
        ggml_threadpool_free(engine->threadpool);
        engine->threadpool = nullptr;
    }
    if (engine->model) {
        llama_model_free(engine->model);
        engine->model = nullptr;
    }
    // Reset KV cache state
    engine->cached_prompt_tokens.clear();
    engine->has_cached_prompt = false;
    // Get model path
    const char* path = jstringToCString(env, modelPath);
    if (!path) {
        LOGE("Invalid model path");
        return JNI_FALSE;
    }
    LOGI("Loading model from: %s", path);
    // Set model parameters
    llama_model_params mparams = llama_model_default_params();
    mparams.n_gpu_layers = 32;  // offload all layers to GPU
    mparams.use_mmap = true;     // use mmap for memory efficiency
    mparams.use_mlock = false;
    // Load model
    engine->model = llama_model_load_from_file(path, mparams);
    if (!engine->model) {
        LOGE("Failed to load model from: %s", path);
        releaseJString(env, modelPath, path);
        return JNI_FALSE;
    }
    engine->modelPath = path;
    // Calculate dynamic n_ctx based on model parameters
    const int n_ctx = calculate_dynamic_n_ctx(engine->model);
    LOGI("Using dynamic n_ctx: %d", n_ctx);
    // Create context with KV cache quantization and RoPE scaling
    llama_context_params cparams = llama_context_default_params();
    cparams.n_ctx = n_ctx;
    cparams.n_batch = 512;
    cparams.n_ubatch = 512;
    // RoPE scaling for extended context (equivalent to doubling)
    cparams.rope_freq_scale = 0.5f;
    // Threadpool for inference
    int n_threads = std::thread::hardware_concurrency() / 2;
    if (n_threads < 1) n_threads = 1;
    if (n_threads > 8) n_threads = 8;
    engine->threadpool = create_big_core_threadpool(n_threads);
    engine->threadpool_batch = create_big_core_threadpool(n_threads);
    cparams.n_threads = n_threads;
    cparams.n_threads_batch = n_threads;
    engine->context = llama_init_from_model(engine->model, cparams);
    if (!engine->context) {
        LOGE("Failed to create context for model: %s", path);
        llama_model_free(engine->model);
        engine->model = nullptr;
        releaseJString(env, modelPath, path);
        return JNI_FALSE;
    }
    // Attach threadpool to context
    if (engine->threadpool) {
        llama_attach_threadpool(engine->context, engine->threadpool, engine->threadpool_batch);
    }
    releaseJString(env, modelPath, path);
    LOGI("Model loaded successfully: %s", engine->modelPath.c_str());
    LOGI("Context: n_ctx=%d, n_batch=%d, n_ubatch=%d", 
         llama_n_ctx(engine->context), cparams.n_batch, cparams.n_ubatch);
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
        llama_detach_threadpool(engine->context);
        llama_free(engine->context);
        engine->context = nullptr;
    }
    if (engine->threadpool_batch) {
        ggml_threadpool_free(engine->threadpool_batch);
        engine->threadpool_batch = nullptr;
    }
    if (engine->threadpool) {
        ggml_threadpool_free(engine->threadpool);
        engine->threadpool = nullptr;
    }
    if (engine->model) {
        llama_model_free(engine->model);
        engine->model = nullptr;
    }
    // Reset KV cache state
    engine->cached_prompt_tokens.clear();
    engine->has_cached_prompt = false;
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
// Non-streaming generation
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
    // Get engine pointer
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
        engineMutex.unlock();
        return cstringToJString(env, "Error: Invalid prompt");
    }
    engineMutex.unlock();
    LOGI("Generation for prompt (len=%d), maxTokens=%d, temp=%.2f",
         (int)strlen(promptStr), maxTokens, temperature);
    // Get vocab from model
    const llama_vocab* vocab = llama_model_get_vocab(engine->model);
    // Tokenize prompt
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
    // Clear KV cache before decode to ensure fresh inference
    llama_memory_clear(llama_get_memory(engine->context), true);
    // Decode prompt
    if (llama_decode(engine->context, batch)) {
        llama_batch_free(batch);
        return cstringToJString(env, "Error: Failed to decode prompt");
    }
    // Build sampler chain
    llama_sampler_chain_params chainParams = llama_sampler_chain_default_params();
    chainParams.no_perf = true;
    struct llama_sampler* smpl = llama_sampler_chain_init(chainParams);
    llama_sampler_chain_add(smpl, llama_sampler_init_temp(temperature));
    llama_sampler_chain_add(smpl, llama_sampler_init_top_k(topK));
    llama_sampler_chain_add(smpl, llama_sampler_init_top_p(topP, 1));
    llama_sampler_chain_add(smpl, llama_sampler_init_penalties(
        topK, repeatPenalty, 0.0f, 0.0f));
    llama_sampler_chain_add(smpl, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));
    // Generate tokens
    std::string generatedText;
    int nGenerated = 0;
    llama_token newToken = 0;
    // Start timing for performance measurement
    auto startTime = std::chrono::high_resolution_clock::now();
    while (nGenerated < engine->maxTokens) {
        // Check stop condition
        if (engine->stopRequested) {
            LOGI("Generation stopped by request at token %d", nGenerated);
            break;
        }
        // Check for stop sequence
        if (!engine->stopSequence.empty()) {
            std::string currentOutput = generatedText;
            size_t stopPos = currentOutput.find(engine->stopSequence);
            if (stopPos != std::string::npos) {
                generatedText = currentOutput.substr(0, stopPos);
                LOGI("Generation stopped by sequence at token %d", nGenerated);
                break;
            }
        }
        // Sample next token
        newToken = llama_sampler_sample(smpl, engine->context, -1);
        // Check for EOS
        if (llama_vocab_is_eog(vocab, newToken)) {
            LOGI("Generated EOS at token %d", nGenerated);
            break;
        }
        // Convert token to piece
        char tokenBuf[128] = {0};
        int nWritten = llama_token_to_piece(
            vocab, newToken, tokenBuf, (int)sizeof(tokenBuf), 0, false);
        if (nWritten > 0) {
            generatedText += tokenBuf;
        }
        // Accept token in sampler
        llama_sampler_accept(smpl, newToken);
        // Decode single token
        llama_batch singleBatch = llama_batch_get_one(&newToken, 1);
        if (llama_decode(engine->context, singleBatch)) {
            llama_batch_free(singleBatch);
            break;
        }
        nGenerated++;
    }
    // End timing and log performance
    auto endTime = std::chrono::high_resolution_clock::now();
    double elapsed = std::chrono::duration<double>(endTime - startTime).count();
    if (nGenerated > 0 && elapsed > 0) {
        LOGI("Performance: %d tokens in %.2f seconds = %.2f tok/s", nGenerated, elapsed, nGenerated / elapsed);
    }
    // Cleanup
    llama_sampler_free(smpl);
    // Append generated tokens to cached_prompt_tokens for correct KV prefix matching
    engine->isGenerating = false;
    llama_batch_free(batch);
    LOGI("Generated %d tokens", nGenerated);
    return cstringToJString(env, generatedText);
}
// Streaming generation: generates tokens and calls onToken callback for each token
// KV cache prefix matching for efficient reuse
JNIEXPORT jstring JNICALL
Java_com_neuralmind_llama_LlamaJNI_generateStream(
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
    // Get engine pointer
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
        engineMutex.unlock();
        return cstringToJString(env, "Error: Invalid prompt");
    }
    engineMutex.unlock();
    LOGI("Streaming generation for prompt (len=%d), maxTokens=%d, temp=%.2f",
         (int)strlen(promptStr), maxTokens, temperature);
    // Get vocab from model
    const llama_vocab* vocab = llama_model_get_vocab(engine->model);
    // Tokenize prompt
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
        return cstringToJString(env, "Error: Failed to tokenize prompt");
    }
    promptTokens.resize(nPromptTokens);
    // KV cache prefix matching logic
    int decode_start_pos = 0;
    if (engine->has_cached_prompt && !engine->cached_prompt_tokens.empty()) {
        int common_prefix_len = find_common_prefix_len(engine->cached_prompt_tokens, promptTokens);
        LOGI("KV cache prefix match: %d/%d tokens reused (cached=%d, new=%d)",
             common_prefix_len, nPromptTokens, 
             (int)engine->cached_prompt_tokens.size(), nPromptTokens);
        if (common_prefix_len > 0) {
            // We can reuse KV cache from cached_prompt_tokens[0..common_prefix_len-1]
            // We need to decode promptTokens[common_prefix_len..nPromptTokens-1]
            decode_start_pos = common_prefix_len;
            // Remove old KV cache entries after common_prefix_len to avoid position conflicts
            llama_memory_seq_rm(llama_get_memory(engine->context), 0, common_prefix_len, -1);
            LOGI("KV cache: cleared entries from pos %d onwards", common_prefix_len);
            // Find the position where the cached context ends
            llama_batch cache_batch = llama_batch_init(nPromptTokens - decode_start_pos, 0, 1);
            int cache_batch_size = nPromptTokens - decode_start_pos;
            cache_batch.n_tokens = cache_batch_size;
            for (int i = 0; i < cache_batch_size; i++) {
                cache_batch.token[i] = promptTokens[decode_start_pos + i];
                cache_batch.pos[i] = decode_start_pos + i;
                cache_batch.n_seq_id[i] = 1;
                cache_batch.seq_id[i][0] = 0;
                cache_batch.logits[i] = (i == cache_batch_size - 1) ? 1 : 0;
            }
            // Decode from common_prefix_len onwards (reuse existing KV cache)
            if (llama_decode(engine->context, cache_batch)) {
                llama_batch_free(cache_batch);
                return cstringToJString(env, "Error: Failed to decode cached prompt");
            }
            llama_batch_free(cache_batch);
        } else {
            // No common prefix, clear cache and decode from beginning
            LOGI("KV cache prefix mismatch, clearing cache");
            llama_memory_clear(llama_get_memory(engine->context), true);
        }
    } else {
        // No cached prompt, decode from beginning
        llama_batch batch = llama_batch_init(nPromptTokens, 0, 1);
        batch.n_tokens = nPromptTokens;
        for (int i = 0; i < nPromptTokens; i++) {
            batch.token[i] = promptTokens[i];
            batch.pos[i] = i;
            batch.n_seq_id[i] = 1;
            batch.seq_id[i][0] = 0;
            batch.logits[i] = (i == nPromptTokens - 1) ? 1 : 0;
        }
        // Clear KV cache before decode to ensure fresh inference
        llama_memory_clear(llama_get_memory(engine->context), true);
        if (llama_decode(engine->context, batch)) {
            llama_batch_free(batch);
            return cstringToJString(env, "Error: Failed to decode prompt");
        }
        llama_batch_free(batch);
    }
    // Update cached prompt tokens: prefill portion first, generated tokens appended after loop
    engine->cached_prompt_tokens = promptTokens;
    engine->has_cached_prompt = true;
    std::vector<llama_token> generatedTokens;
    // Build sampler chain
    llama_sampler_chain_params chainParams = llama_sampler_chain_default_params();
    chainParams.no_perf = true;
    struct llama_sampler* smpl = llama_sampler_chain_init(chainParams);
    llama_sampler_chain_add(smpl, llama_sampler_init_temp(temperature));
    llama_sampler_chain_add(smpl, llama_sampler_init_top_k(topK));
    llama_sampler_chain_add(smpl, llama_sampler_init_top_p(topP, 1));
    llama_sampler_chain_add(smpl, llama_sampler_init_penalties(
        topK, repeatPenalty, 0.0f, 0.0f));
    llama_sampler_chain_add(smpl, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));
    // Get onToken method ID for callbacks
    jclass jniClass = env->GetObjectClass(thiz);
    jmethodID onTokenMethod = env->GetMethodID(jniClass, "onToken", "(Ljava/lang/String;)V");
    // Generate tokens with streaming callback
    std::string generatedText;
    int nGenerated = 0;
    llama_token newToken = 0;
    // Start timing for performance measurement
    auto startTime = std::chrono::high_resolution_clock::now();
    while (nGenerated < engine->maxTokens) {
        // Check stop condition
        if (engine->stopRequested) {
            LOGI("Streaming: stopped by request at token %d", nGenerated);
            break;
        }
        // Check for stop sequence
        if (!engine->stopSequence.empty()) {
            std::string currentOutput = generatedText;
            size_t stopPos = currentOutput.find(engine->stopSequence);
            if (stopPos != std::string::npos) {
                generatedText = currentOutput.substr(0, stopPos);
                LOGI("Streaming: stopped by sequence at token %d", nGenerated);
                break;
            }
        }
        // Sample next token
        newToken = llama_sampler_sample(smpl, engine->context, -1);
        // Check for EOS (handles qwen/llama3/phi-3.5 etc.)
        if (llama_vocab_is_eog(vocab, newToken)) {
            LOGI("Streaming: generated EOS at token %d", nGenerated);
            break;
        }
        // Check for ChatML special tokens (<|im_end|>, <|im_start|>, etc.)
        // These might not be caught by llama_vocab_is_eog if generated as text
        {
            char specialBuf[64];
            int specialLen = llama_token_to_piece(vocab, newToken, specialBuf, sizeof(specialBuf), 0, true);
            if (specialLen > 0) {
                specialBuf[specialLen < 63 ? specialLen : 63] = '\0';
                if (strstr(specialBuf, "<|") != nullptr || strstr(specialBuf, "|>") != nullptr) {
                    LOGI("Streaming: stopping on ChatML special token at token %d", nGenerated);
                    break;
                }
            }
        }
        // Convert token to piece
        char tokenBuf[128] = {0};
        int nWritten = llama_token_to_piece(
            vocab, newToken, tokenBuf, (int)sizeof(tokenBuf), 0, false);
        if (nWritten > 0) {
            generatedText += tokenBuf;
            // Stream callback: call Kotlin onToken method
            jstring jtoken = env->NewStringUTF(tokenBuf);
            if (jtoken) {
                env->CallVoidMethod(thiz, onTokenMethod, jtoken);
                env->DeleteLocalRef(jtoken);
            }
        }
        // Accept token in sampler
        generatedTokens.push_back(newToken);
        llama_sampler_accept(smpl, newToken);
        // Decode single token
        llama_batch singleBatch = llama_batch_get_one(&newToken, 1);
        if (llama_decode(engine->context, singleBatch)) {
            llama_batch_free(singleBatch);
            break;
        }
        nGenerated++;
    }
    // End timing and log performance
    auto endTime = std::chrono::high_resolution_clock::now();
    double elapsed = std::chrono::duration<double>(endTime - startTime).count();
    if (nGenerated > 0 && elapsed > 0) {
        LOGI("Streaming Performance: %d tokens in %.2f seconds = %.2f tok/s", nGenerated, elapsed, nGenerated / elapsed);
    }
    // Cleanup
    llama_sampler_free(smpl);
    // Append generated tokens to cached_prompt_tokens for correct KV prefix matching
    engine->cached_prompt_tokens.insert(engine->cached_prompt_tokens.end(), generatedTokens.begin(), generatedTokens.end());
    engine->isGenerating = false;
    LOGI("Streaming: generated %d tokens total", nGenerated);
    return cstringToJString(env, generatedText);
}
// Clear prompt cache - clears KV cache and resets cached tokens
JNIEXPORT void JNICALL
Java_com_neuralmind_llama_LlamaJNI_clearPromptCache(JNIEnv* env, jobject thiz, jlong engineId) {
    std::lock_guard<std::mutex> lock(engineMutex);
    auto it = engineMap.find(engineId);
    if (it == engineMap.end()) {
        return;
    }
    LlamaEngineInstance* engine = it->second;
    if (engine->context) {
        llama_memory_clear(llama_get_memory(engine->context), true);
    }
    engine->cached_prompt_tokens.clear();
    engine->has_cached_prompt = false;
    LOGI("Prompt cache cleared");
}
// Save KV state to file
JNIEXPORT jboolean JNICALL
Java_com_neuralmind_llama_LlamaJNI_saveKvState(JNIEnv* env, jobject thiz, jlong engineId, jstring filePath) {
    std::lock_guard<std::mutex> lock(engineMutex);
    auto it = engineMap.find(engineId);
    if (it == engineMap.end()) {
        LOGE("Engine not found for saveKvState: %ld", (long)engineId);
        return JNI_FALSE;
    }
    LlamaEngineInstance* engine = it->second;
    if (!engine->context) {
        LOGE("Context not loaded for saveKvState");
        return JNI_FALSE;
    }
    const char* path = jstringToCString(env, filePath);
    if (!path) {
        LOGE("Invalid file path for saveKvState");
        return JNI_FALSE;
    }
    // Get state size
    size_t stateSize = llama_state_get_size(engine->context);
    if (stateSize == 0) {
        LOGE("Failed to get state size");
        releaseJString(env, filePath, path);
        return JNI_FALSE;
    }
    // Allocate buffer and get state data
    std::vector<uint8_t> stateBuf(stateSize);
    size_t written = llama_state_get_data(engine->context, stateBuf.data(), stateBuf.size());
    if (written == 0) {
        LOGE("Failed to get state data");
        releaseJString(env, filePath, path);
        return JNI_FALSE;
    }
    // Write KV state to file
    std::ofstream outFile(path, std::ios::binary);
    if (!outFile.is_open()) {
        LOGE("Failed to open file for writing: %s", path);
        releaseJString(env, filePath, path);
        return JNI_FALSE;
    }
    outFile.write(reinterpret_cast<const char*>(stateBuf.data()), written);
    outFile.close();
    // Save cached prompt tokens to companion file
    std::string tokensPath = std::string(path) + ".tokens";
    std::ofstream tokensFile(tokensPath, std::ios::binary);
    if (tokensFile.is_open()) {
        uint32_t tokenCount = (uint32_t)engine->cached_prompt_tokens.size();
        tokensFile.write(reinterpret_cast<const char*>(&tokenCount), sizeof(tokenCount));
        if (tokenCount > 0) {
            tokensFile.write(reinterpret_cast<const char*>(engine->cached_prompt_tokens.data()),
                           tokenCount * sizeof(llama_token));
        }
        tokensFile.close();
    }
    releaseJString(env, filePath, path);
    LOGI("KV state saved: %zu bytes to %s", written, path);
    return JNI_TRUE;
}
// Load KV state from file
JNIEXPORT jboolean JNICALL
Java_com_neuralmind_llama_LlamaJNI_loadKvState(JNIEnv* env, jobject thiz, jlong engineId, jstring filePath) {
    std::lock_guard<std::mutex> lock(engineMutex);
    auto it = engineMap.find(engineId);
    if (it == engineMap.end()) {
        LOGE("Engine not found for loadKvState: %ld", (long)engineId);
        return JNI_FALSE;
    }
    LlamaEngineInstance* engine = it->second;
    if (!engine->context) {
        LOGE("Context not loaded for loadKvState");
        return JNI_FALSE;
    }
    const char* path = jstringToCString(env, filePath);
    if (!path) {
        LOGE("Invalid file path for loadKvState");
        return JNI_FALSE;
    }
    // Read KV state from file
    std::ifstream inFile(path, std::ios::binary);
    if (!inFile.is_open()) {
        LOGE("Failed to open file for reading: %s", path);
        releaseJString(env, filePath, path);
        return JNI_FALSE;
    }
    // Get file size
    inFile.seekg(0, std::ios::end);
    size_t fileSize = inFile.tellg();
    inFile.seekg(0, std::ios::beg);
    // Read file content
    std::vector<uint8_t> stateBuf(fileSize);
    inFile.read(reinterpret_cast<char*>(stateBuf.data()), fileSize);
    inFile.close();
    // Load state into context
    size_t loaded = llama_state_set_data(engine->context, stateBuf.data(), fileSize);
    if (loaded == 0) {
        LOGE("Failed to load state data");
        releaseJString(env, filePath, path);
        return JNI_FALSE;
    }
    // Load cached prompt tokens from companion file
    std::string tokensPath = std::string(path) + ".tokens";
    std::ifstream tokensFile(tokensPath, std::ios::binary);
    if (tokensFile.is_open()) {
        uint32_t tokenCount = 0;
        tokensFile.read(reinterpret_cast<char*>(&tokenCount), sizeof(tokenCount));
        if (tokenCount > 0) {
            engine->cached_prompt_tokens.resize(tokenCount);
            tokensFile.read(reinterpret_cast<char*>(engine->cached_prompt_tokens.data()),
                          tokenCount * sizeof(llama_token));
        }
        tokensFile.close();
        engine->has_cached_prompt = !engine->cached_prompt_tokens.empty();
    }
    releaseJString(env, filePath, path);
    LOGI("KV state loaded: %zu bytes from %s", loaded, path);
    return JNI_TRUE;
}
// Extract fingerprint (embedding vector) from given text
JNIEXPORT jfloatArray JNICALL
Java_com_neuralmind_llama_LlamaJNI_extractFingerprint(JNIEnv* env, jobject thiz, jlong engineId, jstring text) {
    std::lock_guard<std::mutex> lock(engineMutex);
    auto it = engineMap.find(engineId);
    if (it == engineMap.end() || !it->second->context || !it->second->model) {
        return nullptr;
    }
    LlamaEngineInstance* engine = it->second;
    const char* textStr = jstringToCString(env, text);
    if (!textStr) {
        return nullptr;
    }
    const llama_vocab* vocab = llama_model_get_vocab(engine->model);
    const int n_embd = llama_model_n_embd(engine->model);
    // Tokenize text
    std::vector<llama_token> tokens(llama_vocab_n_tokens(vocab) * 2 + 1);
    int nTokens = llama_tokenize(vocab, textStr, (int)strlen(textStr),
                                  tokens.data(), (int)tokens.size(), true, false);
    releaseJString(env, text, textStr);
    if (nTokens < 0) {
        return nullptr;
    }
    tokens.resize(nTokens);
    // Create batch for encoding
    llama_batch batch = llama_batch_init(nTokens, 0, 1);
    batch.n_tokens = nTokens;
    for (int i = 0; i < nTokens; i++) {
        batch.token[i] = tokens[i];
        batch.pos[i] = i;
        batch.n_seq_id[i] = 1;
        batch.seq_id[i][0] = 0;
        batch.logits[i] = 0;
    }
    // Decode to get embeddings
    if (llama_decode(engine->context, batch)) {
        llama_batch_free(batch);
        return nullptr;
    }
    llama_batch_free(batch);
    // Get embeddings (average pooling over all tokens)
    const float* embeddings = llama_get_embeddings(engine->context);
    if (!embeddings) {
        return nullptr;
    }
    // Copy embeddings to Java float array
    jfloatArray result = env->NewFloatArray(n_embd);
    if (result) {
        env->SetFloatArrayRegion(result, 0, n_embd, embeddings);
    }
    LOGI("Extracted fingerprint: dim=%d", n_embd);
    return result;
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
    int64_t n_params = llama_model_n_params(engine->model);
    std::string info = "Model: " + engine->modelPath + "\n";
    info += "Parameters: " + std::to_string(n_params) + " (" + 
            std::to_string(n_params / 1000000000) + "." + 
            std::to_string((n_params / 10000000) % 100) + "B)\n";
    info += "Vocab size: " + std::to_string(llama_vocab_n_tokens(vocab)) + "\n";
    info += "Training context size: " + std::to_string(llama_model_n_ctx_train(engine->model)) + "\n";
    info += "Embedding size: " + std::to_string(llama_model_n_embd(engine->model)) + "\n";
    info += "Layers: " + std::to_string(llama_model_n_layer(engine->model)) + "\n";
    info += "Context size: " + std::to_string(llama_n_ctx(engine->context)) + "\n";
    info += "KV cache prefix: " + std::string(engine->has_cached_prompt ? 
            std::to_string(engine->cached_prompt_tokens.size()) + " tokens" : "none") + "\n";
    info += "Threadpool: " + std::string(engine->threadpool ? "big-core" : "default") + "\n";
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


