#include <jni.h>
#include <string>
#include <vector>
#include <map>
#include <mutex>
#include <sstream>
#include <android/log.h>

#define LOG_TAG "NeuralMindJNI"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// 模拟 llama.cpp 推理引擎的占位实现
// 在真实环境中，这里会集成真正的 llama.cpp 库

struct LlamaEngine {
    bool isLoaded = false;
    std::string modelPath;
    std::string modelName;
    bool isGenerating = false;
    std::vector<std::string> responseTokens;
    int currentTokenIndex = 0;
    
    // 模拟响应数据
    std::map<std::string, std::string> predefinedResponses;
};

static std::map<jlong, LlamaEngine*> engineMap;
static jlong nextEngineId = 1;
static std::mutex engineMutex;

// 初始化预定义响应
void initPredefinedResponses(LlamaEngine* engine) {
    engine->predefinedResponses["hello"] = "你好！我是NeuralMind AI助手，很高兴为你服务。我可以帮助你进行对话、提供建议、执行各种任务。";
    engine->predefinedResponses["你好"] = "你好！我是NeuralMind AI助手，很高兴为你服务。我可以帮助你进行对话、提供建议、执行各种任务。";
    engine->predefinedResponses["time"] = "当前时间是 " + std::string(__DATE__) + " " + std::string(__TIME__);
    engine->predefinedResponses["时间"] = "当前时间是 " + std::string(__DATE__) + " " + std::string(__TIME__);
    engine->predefinedResponses["model"] = "当前使用的是本地模型，所有推理都在设备上运行，完全保护您的隐私。";
    engine->predefinedResponses["模型"] = "当前使用的是本地模型，所有推理都在设备上运行，完全保护您的隐私。";
    engine->predefinedResponses["memory"] = "我有九层记忆系统：1.工作记忆 2.短期记忆 3.会话记忆 4.日程记忆 5.个人信息 6.偏好记忆 7.知识记忆 8.习惯记忆 9.深度记忆。";
    engine->predefinedResponses["记忆"] = "我有九层记忆系统：1.工作记忆 2.短期记忆 3.会话记忆 4.日程记忆 5.个人信息 6.偏好记忆 7.知识记忆 8.习惯记忆 9.深度记忆。";
    engine->predefinedResponses["skills"] = "我内置了多种技能：计算器、天气查询、翻译、计时器、备忘录、文件管理、网络搜索、闹钟、系统工具、生活助手等。";
    engine->predefinedResponses["技能"] = "我内置了多种技能：计算器、天气查询、翻译、计时器、备忘录、文件管理、网络搜索、闹钟、系统工具、生活助手等。";
}

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_neuralmind_llama_LlamaJNI_createEngine(JNIEnv* env, jobject thiz) {
    std::lock_guard<std::mutex> lock(engineMutex);
    
    jlong engineId = nextEngineId++;
    LlamaEngine* engine = new LlamaEngine();
    initPredefinedResponses(engine);
    engineMap[engineId] = engine;
    
    LOGI("Created new LlamaEngine with ID: %lld", engineId);
    return engineId;
}

JNIEXPORT void JNICALL
Java_com_neuralmind_llama_LlamaJNI_destroyEngine(JNIEnv* env, jobject thiz, jlong engineId) {
    std::lock_guard<std::mutex> lock(engineMutex);
    
    auto it = engineMap.find(engineId);
    if (it != engineMap.end()) {
        delete it->second;
        engineMap.erase(it);
        LOGI("Destroyed LlamaEngine with ID: %lld", engineId);
    }
}

JNIEXPORT jboolean JNICALL
Java_com_neuralmind_llama_LlamaJNI_loadModel(JNIEnv* env, jobject thiz, jlong engineId, jstring modelPath) {
    std::lock_guard<std::mutex> lock(engineMutex);
    
    auto it = engineMap.find(engineId);
    if (it == engineMap.end()) {
        LOGE("Engine not found: %lld", engineId);
        return JNI_FALSE;
    }
    
    LlamaEngine* engine = it->second;
    const char* pathStr = env->GetStringUTFChars(modelPath, nullptr);
    engine->modelPath = pathStr;
    engine->modelName = pathStr;
    engine->isLoaded = true;
    env->ReleaseStringUTFChars(modelPath, pathStr);
    
    LOGI("Model loaded: %s", engine->modelPath.c_str());
    return JNI_TRUE;
}

JNIEXPORT void JNICALL
Java_com_neuralmind_llama_LlamaJNI_unloadModel(JNIEnv* env, jobject thiz, jlong engineId) {
    std::lock_guard<std::mutex> lock(engineMutex);
    
    auto it = engineMap.find(engineId);
    if (it != engineMap.end()) {
        it->second->isLoaded = false;
        it->second->modelPath.clear();
        it->second->modelName.clear();
        LOGI("Model unloaded");
    }
}

JNIEXPORT jboolean JNICALL
Java_com_neuralmind_llama_LlamaJNI_isModelLoaded(JNIEnv* env, jobject thiz, jlong engineId) {
    std::lock_guard<std::mutex> lock(engineMutex);
    
    auto it = engineMap.find(engineId);
    if (it == engineMap.end()) {
        return JNI_FALSE;
    }
    
    return it->second->isLoaded ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jstring JNICALL
Java_com_neuralmind_llama_LlamaJNI_generate(JNIEnv* env, jobject thiz, jlong engineId, jstring prompt,
                                             jint maxTokens, jfloat temperature,
                                             jfloat topP, jint topK, jfloat repeatPenalty,
                                             jstring stopSequence) {
    std::lock_guard<std::mutex> lock(engineMutex);
    
    auto it = engineMap.find(engineId);
    if (it == engineMap.end() || !it->second->isLoaded) {
        return env->NewStringUTF("Error: Engine not found or model not loaded");
    }
    
    LlamaEngine* engine = it->second;
    const char* promptStr = env->GetStringUTFChars(prompt, nullptr);
    std::string inputPrompt(promptStr);
    env->ReleaseStringUTFChars(prompt, promptStr);
    
    // 查找预定义响应
    std::string response = "我收到了您的消息：\"" + inputPrompt + "\"\n\n作为本地运行的AI，我可以：\n\n1. 回答问题\n2. 提供建议\n3. 执行设备控制任务\n4. 管理您的对话记忆\n5. 使用各种技能工具\n6. 帮助您进行开发工作\n\n请告诉我您需要什么帮助？";
    
    for (const auto& pair : engine->predefinedResponses) {
        if (inputPrompt.find(pair.first) != std::string::npos) {
            response = pair.second;
            break;
        }
    }
    
    LOGD("Generated response for prompt: %s", inputPrompt.c_str());
    return env->NewStringUTF(response.c_str());
}

JNIEXPORT void JNICALL
Java_com_neuralmind_llama_LlamaJNI_stopGeneration(JNIEnv* env, jobject thiz, jlong engineId) {
    std::lock_guard<std::mutex> lock(engineMutex);
    
    auto it = engineMap.find(engineId);
    if (it != engineMap.end()) {
        it->second->isGenerating = false;
        LOGI("Generation stopped");
    }
}

JNIEXPORT jboolean JNICALL
Java_com_neuralmind_llama_LlamaJNI_isGenerating(JNIEnv* env, jobject thiz, jlong engineId) {
    std::lock_guard<std::mutex> lock(engineMutex);
    
    auto it = engineMap.find(engineId);
    if (it == engineMap.end()) {
        return JNI_FALSE;
    }
    
    return it->second->isGenerating ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jstring JNICALL
Java_com_neuralmind_llama_LlamaJNI_getModelInfo(JNIEnv* env, jobject thiz, jlong engineId) {
    std::lock_guard<std::mutex> lock(engineMutex);
    
    auto it = engineMap.find(engineId);
    if (it == engineMap.end() || !it->second->isLoaded) {
        return env->NewStringUTF("No model loaded");
    }
    
    LlamaEngine* engine = it->second;
    std::string info = "Model: " + engine->modelName + "\nStatus: Loaded";
    return env->NewStringUTF(info.c_str());
}

// 新增：设置推理参数
JNIEXPORT void JNICALL
Java_com_neuralmind_llama_LlamaJNI_setParameter(JNIEnv* env, jobject thiz, jlong engineId,
                                               jstring key, jstring value) {
    // 预留接口，用于设置各种推理参数
    LOGD("Set parameter called");
}

// 新增：获取推理参数
JNIEXPORT jstring JNICALL
Java_com_neuralmind_llama_LlamaJNI_getParameter(JNIEnv* env, jobject thiz, jlong engineId,
                                               jstring key) {
    // 预留接口，用于获取推理参数
    return env->NewStringUTF("");
}

// 新增：获取支持的模型列表
JNIEXPORT jobjectArray JNICALL
Java_com_neuralmind_llama_LlamaJNI_getSupportedModels(JNIEnv* env, jobject thiz) {
    // 在真实环境中，这里会扫描本地模型目录
    const char* models[] = {
        "llama-2-7b-chat",
        "llama-2-13b-chat",
        "mistral-7b",
        "phi-2",
        "gemma-2b"
    };
    
    jobjectArray result = env->NewObjectArray(5, env->FindClass("java/lang/String"), nullptr);
    for (int i = 0; i < 5; i++) {
        env->SetObjectArrayElement(result, i, env->NewStringUTF(models[i]));
    }
    
    return result;
}

}
