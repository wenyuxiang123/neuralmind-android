package com.neuralmind.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.neuralmind.ui.screens.chat.ChatScreen
import com.neuralmind.ui.screens.chat.ChatListScreen
import com.neuralmind.ui.screens.models.ModelLibraryScreen
import com.neuralmind.ui.screens.memory.MemoryScreen
import com.neuralmind.ui.screens.skills.SkillsScreen
import com.neuralmind.ui.screens.device.DeviceControlScreen
import com.neuralmind.ui.screens.toolkit.ToolkitStoreScreen
import com.neuralmind.ui.screens.settings.AccelerationSettingsScreen

sealed class Screen(val route: String) {
    object ChatList : Screen("chat_list")
    object Chat : Screen("chat/{conversationId}") {
        fun createRoute(conversationId: Long) = "chat/$conversationId"
    }
    object ModelLibrary : Screen("model_library")
    object Memory : Screen("memory")
    object Skills : Screen("skills")
    object DeviceControl : Screen("device_control")
    object ToolkitStore : Screen("toolkit_store")
    object AccelerationSettings : Screen("acceleration_settings")
}

@Composable
fun AppNavigation(navController: NavHostController) {
    NavHost(
        navController = navController,
        startDestination = Screen.ChatList.route
    ) {
        composable(Screen.ChatList.route) {
            ChatListScreen(
                onConversationClick = { conversationId ->
                    navController.navigate(Screen.Chat.createRoute(conversationId))
                },
                onNewConversation = { conversationId ->
                    navController.navigate(Screen.Chat.createRoute(conversationId))
                }
            )
        }

        composable(
            route = Screen.Chat.route,
            arguments = listOf(navArgument("conversationId") { type = NavType.LongType })
        ) { backStackEntry ->
            val conversationId = backStackEntry.arguments?.getLong("conversationId") ?: 0L
            ChatScreen(
                conversationId = conversationId,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToModels = { navController.navigate(Screen.ModelLibrary.route) },
                onNavigateToMemory = { navController.navigate(Screen.Memory.route) }
            )
        }

        composable(Screen.ModelLibrary.route) {
            ModelLibraryScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Memory.route) {
            MemoryScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Skills.route) {
            SkillsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.DeviceControl.route) {
            DeviceControlScreen()
        }

        composable(Screen.ToolkitStore.route) {
            ToolkitStoreScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.AccelerationSettings.route) {
            AccelerationSettingsScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }
}
