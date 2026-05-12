package com.neuralmind.di

import android.content.Context
import com.neuralmind.data.repository.*
import com.neuralmind.llama.LlamaEngine
import com.neuralmind.device.DeviceController
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideLlamaEngine(): LlamaEngine {
        return LlamaEngine()
    }

    @Provides
    @Singleton
    fun provideDeviceController(@ApplicationContext context: Context): DeviceController {
        return DeviceController(context)
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
        @ApplicationContext context: Context
    ): ModelRepository {
        return ModelRepository(modelDao, context)
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
        automationRuleDao: com.neuralmind.data.local.db.dao.AutomationRuleDao
    ): DeviceRepository {
        return DeviceRepository(deviceController, automationRuleDao)
    }

    @Provides
    fun provideToolkitRepository(
        toolModuleDao: com.neuralmind.data.local.db.dao.ToolModuleDao,
        @ApplicationContext context: Context
    ): ToolkitRepository {
        return ToolkitRepository(toolModuleDao, context)
    }
}
