package com.neuralmind.di

import android.content.Context
import androidx.room.Room
import com.neuralmind.data.local.db.AppDatabase
import com.neuralmind.data.local.db.dao.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "neuralmind_database"
        )
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    fun provideConversationDao(database: AppDatabase): ConversationDao {
        return database.conversationDao()
    }

    @Provides
    fun provideMessageDao(database: AppDatabase): MessageDao {
        return database.messageDao()
    }

    @Provides
    fun provideModelDao(database: AppDatabase): ModelDao {
        return database.modelDao()
    }

    @Provides
    fun provideMemoryDao(database: AppDatabase): MemoryDao {
        return database.memoryDao()
    }

    @Provides
    fun provideSkillDao(database: AppDatabase): SkillDao {
        return database.skillDao()
    }

    @Provides
    fun provideAutomationRuleDao(database: AppDatabase): AutomationRuleDao {
        return database.automationRuleDao()
    }

    @Provides
    fun provideToolModuleDao(database: AppDatabase): ToolModuleDao {
        return database.toolModuleDao()
    }
}
