package com.neuralmind.di

import com.neuralmind.domain.usecase.chat.*
import com.neuralmind.domain.usecase.model.*
import com.neuralmind.domain.usecase.memory.*
import com.neuralmind.domain.usecase.skill.*
import com.neuralmind.domain.usecase.device.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import dagger.hilt.android.scopes.ViewModelScoped

@Module
@InstallIn(ViewModelComponent::class)
object UseCaseModule {
    // Chat Use Cases
    @Provides
    @ViewModelScoped
    fun provideGetConversationsUseCase(repo: ChatRepository): GetConversationsUseCase =
        GetConversationsUseCase(repo)

    @Provides
    @ViewModelScoped
    fun provideGetMessagesUseCase(repo: ChatRepository): GetMessagesUseCase =
        GetMessagesUseCase(repo)

    @Provides
    @ViewModelScoped
    fun provideCreateConversationUseCase(repo: ChatRepository): CreateConversationUseCase =
        CreateConversationUseCase(repo)

    @Provides
    @ViewModelScoped
    fun provideSendMessageUseCase(repo: ChatRepository): SendMessageUseCase =
        SendMessageUseCase(repo)

    @Provides
    @ViewModelScoped
    fun provideUpdateConversationUseCase(repo: ChatRepository): UpdateConversationUseCase =
        UpdateConversationUseCase(repo)

    @Provides
    @ViewModelScoped
    fun provideDeleteConversationUseCase(repo: ChatRepository): DeleteConversationUseCase =
        DeleteConversationUseCase(repo)

    // Model Use Cases
    @Provides
    @ViewModelScoped
    fun provideGetAllModelsUseCase(repo: ModelRepository): GetAllModelsUseCase =
        GetAllModelsUseCase(repo)

    @Provides
    @ViewModelScoped
    fun provideGetModelsByCategoryUseCase(repo: ModelRepository): GetModelsByCategoryUseCase =
        GetModelsByCategoryUseCase(repo)

    @Provides
    @ViewModelScoped
    fun provideGetInstalledModelsUseCase(repo: ModelRepository): GetInstalledModelsUseCase =
        GetInstalledModelsUseCase(repo)

    @Provides
    @ViewModelScoped
    fun provideSwitchModelUseCase(repo: ModelRepository): SwitchModelUseCase =
        SwitchModelUseCase(repo)

    @Provides
    @ViewModelScoped
    fun provideGetCurrentModelUseCase(repo: ModelRepository): GetCurrentModelUseCase =
        GetCurrentModelUseCase(repo)

    @Provides
    @ViewModelScoped
    fun provideDeleteModelUseCase(repo: ModelRepository): DeleteModelUseCase =
        DeleteModelUseCase(repo)

    @Provides
    @ViewModelScoped
    fun provideToggleDownloadUseCase(repo: ModelRepository): ToggleDownloadUseCase =
        ToggleDownloadUseCase(repo)

    // Memory Use Cases
    @Provides
    @ViewModelScoped
    fun provideGetAllMemoriesUseCase(repo: MemoryRepository): GetAllMemoriesUseCase =
        GetAllMemoriesUseCase(repo)

    @Provides
    @ViewModelScoped
    fun provideGetMemoriesByLayerUseCase(repo: MemoryRepository): GetMemoriesByLayerUseCase =
        GetMemoriesByLayerUseCase(repo)

    @Provides
    @ViewModelScoped
    fun provideAddMemoryUseCase(repo: MemoryRepository): AddMemoryUseCase =
        AddMemoryUseCase(repo)

    @Provides
    @ViewModelScoped
    fun provideActivateMemoryLayerUseCase(repo: MemoryRepository): ActivateMemoryLayerUseCase =
        ActivateMemoryLayerUseCase(repo)

    // Skill Use Cases
    @Provides
    @ViewModelScoped
    fun provideGetAllSkillsUseCase(repo: SkillRepository): GetAllSkillsUseCase =
        GetAllSkillsUseCase(repo)

    @Provides
    @ViewModelScoped
    fun provideGetSkillsByCategoryUseCase(repo: SkillRepository): GetSkillsByCategoryUseCase =
        GetSkillsByCategoryUseCase(repo)

    @Provides
    @ViewModelScoped
    fun provideInstallSkillUseCase(repo: SkillRepository): InstallSkillUseCase =
        InstallSkillUseCase(repo)

    @Provides
    @ViewModelScoped
    fun provideExecuteSkillUseCase(repo: SkillRepository): ExecuteSkillUseCase =
        ExecuteSkillUseCase(repo)

    // Device Use Cases
    @Provides
    @ViewModelScoped
    fun provideGetDeviceStatusUseCase(repo: DeviceRepository): GetDeviceStatusUseCase =
        GetDeviceStatusUseCase(repo)

    @Provides
    @ViewModelScoped
    fun provideExecuteDeviceActionUseCase(repo: DeviceRepository): ExecuteDeviceActionUseCase =
        ExecuteDeviceActionUseCase(repo)

    @Provides
    @ViewModelScoped
    fun provideGetAutomationRulesUseCase(repo: DeviceRepository): GetAutomationRulesUseCase =
        GetAutomationRulesUseCase(repo)

    @Provides
    @ViewModelScoped
    fun provideCreateAutomationRuleUseCase(repo: DeviceRepository): CreateAutomationRuleUseCase =
        CreateAutomationRuleUseCase(repo)
}
