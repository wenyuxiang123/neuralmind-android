package com.neuralmind.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.neuralmind.ui.components.DrawerContent
import com.neuralmind.ui.navigation.AppNavigation
import com.neuralmind.ui.navigation.Screen
import com.neuralmind.ui.theme.*
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NeuralMindTheme(darkTheme = true) {
                NeuralMindApp()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NeuralMindApp() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    val showDrawerRoutes = listOf(
        Screen.ChatList.route, Screen.ModelLibrary.route, Screen.Memory.route,
        Screen.Skills.route, Screen.DeviceControl.route, Screen.ToolkitStore.route
    )
    val shouldShowDrawer = currentRoute in showDrawerRoutes

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            if (drawerState.isOpen) {
                Box(modifier = Modifier.fillMaxHeight().width(280.dp)) {
                    DrawerContent(
                        currentRoute = currentRoute,
                        onNavigate = { route ->
                            navController.navigate(route) {
                                popUpTo(Screen.ChatList.route) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                            GlobalScope.launch(Dispatchers.Main) { drawerState.close() }
                        }
                    )
                }
            }
        },
        gesturesEnabled = shouldShowDrawer
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = BackgroundPrimary,
            topBar = {
                if (shouldShowDrawer) {
                    TopAppBar(
                        title = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Android, contentDescription = null, tint = GradientStart)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("NeuralMind AI", fontWeight = FontWeight.Bold, color = TextPrimary)
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(shape = MaterialTheme.shapes.small, color = StatusOnline.copy(alpha = 0.2f)) {
                                    Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Box(modifier = Modifier.size(8.dp).background(StatusOnline, MaterialTheme.shapes.small))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("在线", style = MaterialTheme.typography.labelSmall, color = StatusOnline)
                                    }
                                }
                            }
                        },
                        navigationIcon = {
                            IconButton(onClick = { GlobalScope.launch(Dispatchers.Main) { drawerState.open() } }) {
                                Icon(Icons.Default.Menu, contentDescription = "菜单", tint = TextPrimary)
                            }
                        },
                        actions = {
                            IconButton(onClick = { navController.navigate(Screen.Memory.route) }) {
                                Icon(Icons.Default.Psychology, contentDescription = "记忆", tint = GradientStart)
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = BackgroundPrimary)
                    )
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding)
                    .background(brush = Brush.verticalGradient(colors = listOf(BackgroundPrimary, Color(0xFF0A1628), BackgroundPrimary)))
            ) {
                AppNavigation(navController = navController)
            }
        }
    }
}
