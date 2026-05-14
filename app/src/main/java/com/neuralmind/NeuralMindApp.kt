package com.neuralmind

import android.app.Application
import android.content.ComponentCallbacks2
import com.neuralmind.core.Logger
import com.neuralmind.data.repository.ChatRepository
import com.neuralmind.data.repository.ModelRepository
import com.neuralmind.data.repository.MemoryRepository
import com.neuralmind.data.repository.SkillRepository
import com.neuralmind.data.repository.DeviceRepository
import com.neuralmind.data.repository.ToolkitRepository
import com.neuralmind.llama.LlamaEngine
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class NeuralMindApp : Application() {
    
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    @Inject
    lateinit var chatRepository: ChatRepository
    
    @Inject
    lateinit var modelRepository: ModelRepository
    
    @Inject
    lateinit var memoryRepository: MemoryRepository
    
    @Inject
    lateinit var deviceRepository: DeviceRepository
    
    @Inject
    lateinit var toolkitRepository: ToolkitRepository
    
    @Inject
    lateinit var skillRepository: SkillRepository
    
    @Inject
    lateinit var llamaEngine: LlamaEngine
    
    override fun onCreate() {
        super.onCreate()
        
        // 初始化 Logger
        Logger.init(this)
        Logger.i(Logger.Tags.ENGINE, "Application onCreate")
        Logger.i(Logger.Tags.ENGINE, "Log directory: ${Logger.getLogDirPath() ?: "not available"}")
        
        // 初始化默认数据
        initializeDefaultData()
    }
    
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) {
            Logger.flush()
        }
    }
    
    private fun initializeDefaultData() {
        applicationScope.launch {
            Logger.d(Logger.Tags.ENGINE, "Initializing default data...")
            
            // 初始化所有 Repository 的默认数据，每个独立 try-catch
            try {
                modelRepository.insertDefaultModels()
                Logger.d(Logger.Tags.REPO, "ModelRepository initialized")
            } catch (e: Exception) {
                Logger.e(Logger.Tags.REPO, "Error initializing ModelRepository", e)
            }
            
            try {
                memoryRepository.insertDefaultMemories()
                Logger.d(Logger.Tags.REPO, "MemoryRepository initialized")
            } catch (e: Exception) {
                Logger.e(Logger.Tags.REPO, "Error initializing MemoryRepository", e)
            }
            
            try {
                skillRepository.insertDefaultSkills()
                Logger.d(Logger.Tags.REPO, "SkillRepository initialized")
            } catch (e: Exception) {
                Logger.e(Logger.Tags.REPO, "Error initializing SkillRepository", e)
            }
            
            try {
                deviceRepository.insertDefaultRules()
                Logger.d(Logger.Tags.REPO, "DeviceRepository initialized")
            } catch (e: Exception) {
                Logger.e(Logger.Tags.REPO, "Error initializing DeviceRepository", e)
            }
            
            try {
                toolkitRepository.insertDefaultTools()
                Logger.d(Logger.Tags.REPO, "ToolkitRepository initialized")
            } catch (e: Exception) {
                Logger.e(Logger.Tags.REPO, "Error initializing ToolkitRepository", e)
            }
            
            try {
                chatRepository.insertDefaultData()
                Logger.d(Logger.Tags.REPO, "ChatRepository initialized")
            } catch (e: Exception) {
                Logger.e(Logger.Tags.REPO, "Error initializing ChatRepository", e)
            }
            
            Logger.d(Logger.Tags.ENGINE, "Default data initialization completed")
            
            // 自动加载上次的模型
            try {
                val currentModel = modelRepository.currentModel.value
                if (currentModel != null && currentModel.isInstalled) {
                    Logger.d(Logger.Tags.ENGINE, "Auto-loading model: ${currentModel.id}")
                    val loaded = llamaEngine.loadModel(currentModel.id)
                    if (loaded) {
                        Logger.i(Logger.Tags.ENGINE, "Auto-load model success: ${currentModel.name}")
                    } else {
                        Logger.w(Logger.Tags.ENGINE, "Auto-load model failed: ${currentModel.id}")
                    }
                } else {
                    // 尝试加载第一个已安装的模型
                    val installedModels = modelRepository.getInstalledModelsSync()
                    if (installedModels.isNotEmpty()) {
                        val firstModel = installedModels.first()
                        Logger.d(Logger.Tags.ENGINE, "Auto-loading first installed model: ${firstModel.id}")
                        modelRepository.switchModel(firstModel.id)
                        val loaded = llamaEngine.loadModel(firstModel.id)
                        if (loaded) {
                            Logger.i(Logger.Tags.ENGINE, "Auto-load first model success: ${firstModel.name}")
                        }
                    } else {
                        Logger.i(Logger.Tags.ENGINE, "No installed model found, skip auto-load")
                    }
                }
            } catch (e: Exception) {
                Logger.e(Logger.Tags.ENGINE, "Auto-load model failed", e)
            }
        }
    }
}
