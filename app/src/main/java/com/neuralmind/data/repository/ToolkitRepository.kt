package com.neuralmind.data.repository

import android.content.Context
import com.neuralmind.data.local.db.dao.ToolModuleDao
import com.neuralmind.data.local.db.entity.ToolModuleEntity
import com.neuralmind.domain.model.ToolCategory
import com.neuralmind.domain.model.ToolModule
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ToolkitRepository @Inject constructor(
    private val toolModuleDao: ToolModuleDao,
    @ApplicationContext private val context: Context
) {
    fun getAllTools(): Flow<List<ToolModule>> {
        return toolModuleDao.getAllModules().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    fun getInstalledTools(): Flow<List<ToolModule>> {
        return toolModuleDao.getInstalledModules().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    suspend fun insertDefaultTools() {
        val defaultTools = listOf(
            ToolModuleEntity(
                id = "editor",
                name = "代码编辑器",
                description = "支持多种语言的代码编辑工具",
                version = "1.0",
                category = ToolCategory.EDITOR.name,
                downloadSize = 5L * 1024 * 1024,
                installedSize = 10L * 1024 * 1024,
                icon = "edit",
                downloadUrl = "",
                isInstalled = false,
                isDownloading = false,
                downloadProgress = 0f,
                localPath = null
            ),
            ToolModuleEntity(
                id = "terminal",
                name = "终端模拟器",
                description = "Android 终端仿真器",
                version = "1.0",
                category = ToolCategory.TERMINAL.name,
                downloadSize = 3L * 1024 * 1024,
                installedSize = 8L * 1024 * 1024,
                icon = "terminal",
                downloadUrl = "",
                isInstalled = false,
                isDownloading = false,
                downloadProgress = 0f,
                localPath = null
            ),
            ToolModuleEntity(
                id = "git",
                name = "Git 工具",
                description = "Git 版本控制工具",
                version = "1.0",
                category = ToolCategory.GIT.name,
                downloadSize = 4L * 1024 * 1024,
                installedSize = 12L * 1024 * 1024,
                icon = "git",
                downloadUrl = "",
                isInstalled = false,
                isDownloading = false,
                downloadProgress = 0f,
                localPath = null
            ),
            ToolModuleEntity(
                id = "database",
                name = "数据库管理",
                description = "SQLite 和 Room 数据库管理工具",
                version = "1.0",
                category = ToolCategory.DATABASE.name,
                downloadSize = 2L * 1024 * 1024,
                installedSize = 5L * 1024 * 1024,
                icon = "database",
                downloadUrl = "",
                isInstalled = false,
                isDownloading = false,
                downloadProgress = 0f,
                localPath = null
            ),
            ToolModuleEntity(
                id = "api_tester",
                name = "API 测试器",
                description = "REST API 测试工具",
                version = "1.0",
                category = ToolCategory.API_TESTER.name,
                downloadSize = 3L * 1024 * 1024,
                installedSize = 7L * 1024 * 1024,
                icon = "api",
                downloadUrl = "",
                isInstalled = false,
                isDownloading = false,
                downloadProgress = 0f,
                localPath = null
            ),
            ToolModuleEntity(
                id = "file_manager",
                name = "文件管理器",
                description = "强大的文件管理工具",
                version = "1.0",
                category = ToolCategory.FILE_MANAGER.name,
                downloadSize = 2L * 1024 * 1024,
                installedSize = 5L * 1024 * 1024,
                icon = "folder",
                downloadUrl = "",
                isInstalled = false,
                isDownloading = false,
                downloadProgress = 0f,
                localPath = null
            )
        )
        defaultTools.forEach { toolModuleDao.insert(it) }
    }

    private fun ToolModuleEntity.toDomain(): ToolModule {
        return ToolModule(
            id = id,
            name = name,
            description = description,
            version = version,
            category = ToolCategory.valueOf(category),
            downloadSize = downloadSize,
            installedSize = installedSize,
            icon = icon,
            downloadUrl = downloadUrl,
            isInstalled = isInstalled,
            isDownloading = isDownloading,
            downloadProgress = downloadProgress
        )
    }
}
