package com.neuralmind

import android.app.Application
import android.util.Log
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
    lateinit var skillRepository: SkillRepository

    @Inject
    lateinit var deviceRepository: DeviceRepository

    @Inject
    lateinit var toolkitRepository: ToolkitRepository

    override fun onCreate() {
        super.onCreate()
        Log.d("NeuralMindApp", "Application onCreate")
        
        // 初始化默认数据
        initializeDefaultData()
    }

    private fun initializeDefaultData() {
        applicationScope.launch {
            try {
                Log.d("NeuralMindApp", "Initializing default data...")
                
                // 初始化所有 Repository 的默认数据
                modelRepository.insertDefaultModels()
                memoryRepository.insertDefaultMemories()
                skillRepository.insertDefaultSkills()
                deviceRepository.insertDefaultRules()
                toolkitRepository.insertDefaultTools()
                chatRepository.insertDefaultData()
                
                Log.d("NeuralMindApp", "Default data initialized successfully")
            } catch (e: Exception) {
                Log.e("NeuralMindApp", "Error initializing default data", e)
            }
        }
    }
}
