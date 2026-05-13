package com.neuralmind.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabase.Callback
import androidx.sqlite.db.SupportSQLiteDatabase
import com.neuralmind.core.Logger
import com.neuralmind.data.local.db.dao.*
import com.neuralmind.data.local.db.entity.*

@Database(
    entities = [
        ConversationEntity::class,
        MessageEntity::class,
        ModelEntity::class,
        MemoryEntity::class,
        SkillEntity::class,
        AutomationRuleEntity::class,
        ToolModuleEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    
    abstract fun conversationDao(): ConversationDao
    abstract fun messageDao(): MessageDao
    abstract fun modelDao(): ModelDao
    abstract fun memoryDao(): MemoryDao
    abstract fun skillDao(): SkillDao
    abstract fun automationRuleDao(): AutomationRuleDao
    abstract fun toolModuleDao(): ToolModuleDao
    
    companion object {
        private const val TAG = "NM-DB"
    }
    
    // RoomDatabase 回调用于日志
    val databaseCallback: Callback = object : Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            Logger.i(TAG, "AppDatabase onCreate: database created")
        }
        
        override fun onOpen(db: SupportSQLiteDatabase) {
            super.onOpen(db)
            Logger.i(TAG, "AppDatabase onOpen: database opened")
        }
        
        override fun onDestructiveMigration(db: SupportSQLiteDatabase) {
            super.onDestructiveMigration(db)
            Logger.w(TAG, "AppDatabase onDestructiveMigration: destructive migration occurred")
        }
    }
}
