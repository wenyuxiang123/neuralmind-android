package com.neuralmind.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
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
}
