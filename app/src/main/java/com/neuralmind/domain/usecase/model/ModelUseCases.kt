package com.neuralmind.domain.usecase.model

import com.neuralmind.data.repository.ModelRepository
import com.neuralmind.domain.model.AIModel
import com.neuralmind.domain.model.ModelCategory
import javax.inject.Inject

class GetAllModelsUseCase @Inject constructor(
    private val repository: ModelRepository
) {
    operator fun invoke() = repository.getAllModels()
}

class GetModelsByCategoryUseCase @Inject constructor(
    private val repository: ModelRepository
) {
    operator fun invoke(category: ModelCategory) = repository.getModelsByCategory(category)
}

class GetInstalledModelsUseCase @Inject constructor(
    private val repository: ModelRepository
) {
    operator fun invoke() = repository.getInstalledModels()
}

class SwitchModelUseCase @Inject constructor(
    private val repository: ModelRepository
) {
    suspend operator fun invoke(modelId: String) = repository.switchModel(modelId)
}

class GetCurrentModelUseCase @Inject constructor(
    private val repository: ModelRepository
) {
    operator fun invoke() = repository.getCurrentModel()
}

class DeleteModelUseCase @Inject constructor(
    private val repository: ModelRepository
) {
    suspend operator fun invoke(modelId: String) = repository.deleteModel(modelId)
}

class ToggleDownloadUseCase @Inject constructor(
    private val repository: ModelRepository
) {
    suspend operator fun invoke(modelId: String) = repository.toggleDownload(modelId)
}
