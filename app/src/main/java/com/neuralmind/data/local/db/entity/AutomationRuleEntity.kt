package com.neuralmind.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "automation_rules")
data class AutomationRuleEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val description: String,
    val triggersJson: String,
    val conditionsJson: String,
    val actionsJson: String,
    val isEnabled: Boolean = true,
    val lastTriggered: Long? = null,
    val triggerCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
