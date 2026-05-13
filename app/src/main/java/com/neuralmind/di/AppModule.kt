package com.neuralmind.di

import android.content.Context
import com.neuralmind.data.repository.*
import com.neuralmind.llama.LlamaEngine
import com.neuralmind.llama.LlamaJNI
import com.neuralmind.device.DeviceController
import com.neuralmind.network.NetworkManager
import com.neuralmind.network.ModelDownloader
import com.neuralmind.skills.SkillExecutor
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.Lazy
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    
    @Provides
    @Singleton
    fun provideLlamaJNI(): LlamaJNI {
        return LlamaJNI()
    }
    
    @Provides
    @Singleton
    fun provideLlamaEngine(
        @ApplicationContext context: Context,
        modelRepository: ModelRepository,
        llamaJNI: LlamaJNI
    ): LlamaEngine {
        return LlamaEngine(context, modelRepository, llamaJNI)
    }
    
    @Provides
    @Singleton
    fun provideDeviceController(@ApplicationContext context: Context): DeviceController {
        return DeviceController(context)
    }
    
    @Provides
    @Singleton
    fun provideNetworkManager(): NetworkManager {
        return NetworkManager()
    }
    
    @Provides
    @Singleton
    fun provideModelDownloader(
        @ApplicationContext context: Context,
        networkManager: NetworkManager,
        modelRepository: Lazy<ModelRepository>
    ): ModelDownloader {
        return ModelDownloader(context, networkManager, modelRepository)
    }
    
    @Provides
    @Singleton
    fun provideSkillExecutor(@ApplicationContext context: Context): SkillExecutor {
        return SkillExecutor(context)
    }
    
    @Provides
    fun provideChatRepository(
        conversationDao: com.neuralmind.data.local.db.dao.ConversationDao,
        messageDao: com.neuralmind.data.local.db.dao.MessageDao
    ): ChatRepository {
        return ChatRepository(conversationDao, messageDao)
    }
    
    @Provides
    fun provideModelRepository(
        modelDao: com.neuralmind.data.local.db.dao.ModelDao,
        @ApplicationContext context: Context,
        modelDownloader: ModelDownloader
    ): ModelRepository {
        return ModelRepository(modelDao, context, modelDownloader)
    }
    
    @Provides
    fun provideMemoryRepository(
        memoryDao: com.neuralmind.data.local.db.dao.MemoryDao
    ): MemoryRepository {
        return MemoryRepository(memoryDao)
    }
    
    @Provides
    fun provideSkillRepository(
        skillDao: com.neuralmind.data.local.db.dao.SkillDao
    ): SkillRepository {
        return SkillRepository(skillDao)
    }
    
    @Provides
    fun provideDeviceRepository(
        deviceController: DeviceController,
        automationRuleDao: com.neuralmind.data.local.db.dao.AutomationRuleDao,
        @ApplicationContext context: android.content.Context
    ): DeviceRepository {
        return DeviceRepository(deviceController, automationRuleDao, context)
    }
    
    @Provides
    fun provideToolkitRepository(
        toolModuleDao: com.neuralmind.data.local.db.dao.ToolModuleDao,
        toolExecutor: com.neuralmind.tools.ToolExecutor,
        @ApplicationContext context: Context
    ): ToolkitRepository {
        return ToolkitRepository(toolModuleDao, toolExecutor, context)
    }
}
