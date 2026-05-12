package com.neuralmind.domain.usecase.device

import com.neuralmind.data.repository.DeviceRepository
import com.neuralmind.domain.model.DeviceAction
import com.neuralmind.domain.model.DeviceType
import com.neuralmind.domain.model.AutomationRule
import javax.inject.Inject

class GetDeviceStatusUseCase @Inject constructor(
    private val repository: DeviceRepository
) {
    operator fun invoke() = repository.getDeviceStatus()
}

class ExecuteDeviceActionUseCase @Inject constructor(
    private val repository: DeviceRepository
) {
    suspend operator fun invoke(action: DeviceAction) = repository.executeAction(action)
}

class GetAutomationRulesUseCase @Inject constructor(
    private val repository: DeviceRepository
) {
    operator fun invoke() = repository.getAutomationRules()
}

class CreateAutomationRuleUseCase @Inject constructor(
    private val repository: DeviceRepository
) {
    suspend operator fun invoke(rule: AutomationRule) = repository.createAutomationRule(rule)
}
