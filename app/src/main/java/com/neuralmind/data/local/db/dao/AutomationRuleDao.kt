package com.neuralmind.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.neuralmind.data.local.db.entity.AutomationRuleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AutomationRuleDao {
    @Query("SELECT * FROM automation_rules ORDER BY updatedAt DESC")
    fun getAllRules(): Flow<List<AutomationRuleEntity>>

    @Query("SELECT * FROM automation_rules WHERE isEnabled = 1")
    fun getEnabledRules(): Flow<List<AutomationRuleEntity>>

    @Query("SELECT * FROM automation_rules WHERE id = :id")
    suspend fun getRuleById(id: String): AutomationRuleEntity?

    @Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    suspend fun insert(rule: AutomationRuleEntity)

    @androidx.room.Update
    suspend fun update(rule: AutomationRuleEntity)

    @androidx.room.Delete
    suspend fun delete(rule: AutomationRuleEntity)

    @Query("UPDATE automation_rules SET isEnabled = :isEnabled WHERE id = :id")
    suspend fun setEnabled(id: String, isEnabled: Boolean)

    @Query("UPDATE automation_rules SET lastTriggered = :timestamp, triggerCount = triggerCount + 1 WHERE id = :id")
    suspend fun recordTrigger(id: String, timestamp: Long)
}
