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
        }
    }
}
