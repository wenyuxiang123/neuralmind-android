package com.neuralmind.data.repository

import com.neuralmind.data.local.db.dao.AutomationRuleDao
import com.neuralmind.device.DeviceController
import com.neuralmind.domain.model.AutomationRule
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeviceRepository @Inject constructor(
    private val deviceController: DeviceController,
    private val automationRuleDao: AutomationRuleDao,
    @ApplicationContext private val context: android.content.Context
) {
    suspend fun getAllRules(): List<AutomationRule> {
        return emptyList()
    }

    suspend fun addRule(rule: AutomationRule) {
    }

    suspend fun deleteRule(ruleId: String) {
    }

    suspend fun executeRule(ruleId: String) {
    }
}
