package com.vasu.assistant.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Build
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.vasu.assistant.core.navigation.Screen
import com.vasu.assistant.ui.home.HomeScreen
import com.vasu.assistant.ui.chat.ChatScreen
import com.vasu.assistant.ui.tools.ToolsScreen
import com.vasu.assistant.ui.settings.SettingsScreen
import com.vasu.assistant.ui.voice.VoiceScreen
import com.vasu.assistant.ui.guardian.GuardianScreen
import com.vasu.assistant.ui.missions.MissionsScreen
import com.vasu.assistant.ui.automation.AutomationScreen
import com.vasu.assistant.ui.memory.MemoryScreen
import com.vasu.assistant.ui.permissions.PermissionsScreen
import com.vasu.assistant.ui.privacy.PrivacyScreen
import com.vasu.assistant.ui.location.LocationScreen
import com.vasu.assistant.ui.browser.BrowserScreen
import com.vasu.assistant.ui.diagnostics.DiagnosticsScreen
import com.vasu.assistant.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    val navController = rememberNavController()
    
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "VASU",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 20.sp,
                            color = VasuCyan
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Voice Assistant",
                            fontSize = 12.sp,
                            color = VasuTextMuted,
                            fontWeight = FontWeight.Normal
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = VasuDarkBg
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = VasuDarkBg,
                contentColor = VasuCyan
            ) {
                val navGraph = navController.graph
                val currentDestination = navController.currentBackStackEntry?.destination
                
                val items = listOf(
                    NavigationItem("Home", Screen.Home.route, Icons.Default.Home),
                    NavigationItem("Chat", Screen.Chat.route, Icons.Default.Chat),
                    NavigationItem("Tools", Screen.Tools.route, Icons.Default.Build),
                    NavigationItem("Settings", Screen.Settings.route, Icons.Default.Settings),
                )
                
                items.forEach { item ->
                    NavigationBarItem(
                        icon = { Icon(item.icon, contentDescription = item.label) },
                        label = { Text(item.label, fontSize = 10.sp) },
                        selected = currentDestination?.hierarchy?.any { it.route == item.route } == true,
                        onClick = {
                            navController.navigate(item.route) {
                                popUpTo(navGraph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = VasuCyan.copy(alpha = 0.2f),
                            selectedIconColor = VasuCyan,
                            selectedTextColor = VasuCyan,
                            unselectedIconColor = VasuTextMuted,
                            unselectedTextColor = VasuTextMuted
                        )
                    )
                }
            }
        },
        containerColor = VasuDarkBg
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            NavHost(
                navController = navController,
                startDestination = Screen.Home.route,
                modifier = Modifier.fillMaxSize()
            ) {
                composable(Screen.Home.route) {
                    HomeScreen(
                        onNavigateToChat = { navController.navigate(Screen.Chat.route) },
                        onNavigateToVoice = { navController.navigate(Screen.Voice.route) },
                        onNavigateToSettings = { navController.navigate(Screen.Settings.route) },
                        onNavigateToGuardian = { navController.navigate(Screen.Guardian.route) },
                        onNavigateToMissions = { navController.navigate(Screen.Missions.route) },
                        onNavigateToAutomation = { navController.navigate(Screen.Automation.route) },
                        onNavigateToMemory = { navController.navigate(Screen.Memory.route) },
                        onNavigateToTools = { navController.navigate(Screen.Tools.route) },
                        onNavigateToPermissions = { navController.navigate(Screen.Permissions.route) },
                        onNavigateToPrivacy = { navController.navigate(Screen.Privacy.route) }
                    )
                }
                composable(Screen.Chat.route) {
                    ChatScreen(onNavigateBack = { navController.popBackStack() })
                }
                composable(Screen.Voice.route) {
                    VoiceScreen(
                        onNavigateBack = { navController.popBackStack() },
                        onNavigateToSettings = { navController.navigate(Screen.Settings.route) }
                    )
                }
                composable(Screen.Settings.route) {
                    SettingsScreen(onNavigateBack = { navController.popBackStack() })
                }
                composable(Screen.Guardian.route) {
                    GuardianScreen(onNavigateBack = { navController.popBackStack() })
                }
                composable(Screen.Missions.route) {
                    MissionsScreen(onNavigateBack = { navController.popBackStack() })
                }
                composable(Screen.Automation.route) {
                    AutomationScreen()
                }
                composable(Screen.Memory.route) {
                    MemoryScreen()
                }
                composable(Screen.Tools.route) {
                    ToolsScreen(
                        onNavigateBack = { navController.popBackStack() },
                        onToolSelect = { toolId ->
                            when (toolId) {
                                "settings" -> navController.navigate(Screen.Settings.route)
                                "missions" -> navController.navigate(Screen.Missions.route)
                                "location" -> navController.navigate(Screen.Location.route)
                                "browser" -> navController.navigate(Screen.Browser.route)
                                "permissions" -> navController.navigate(Screen.Permissions.route)
                                "privacy" -> navController.navigate(Screen.Privacy.route)
                                "guardian" -> navController.navigate(Screen.Guardian.route)
                                "memory" -> navController.navigate(Screen.Memory.route)
                                else -> {}
                            }
                        }
                    )
                }
                composable(Screen.Permissions.route) {
                    PermissionsScreen(onNavigateBack = { navController.popBackStack() })
                }
                composable(Screen.Privacy.route) {
                    PrivacyScreen(onNavigateBack = { navController.popBackStack() })
                }
                composable(Screen.Location.route) {
                    LocationScreen(onNavigateBack = { navController.popBackStack() })
                }
                composable(Screen.Browser.route) {
                    BrowserScreen(onNavigateBack = { navController.popBackStack() })
                }
                composable(Screen.Diagnostics.route) {
                    DiagnosticsScreen(onNavigateBack = { navController.popBackStack() })
                }
            }
        }
    }
}

data class NavigationItem(val label: String, val route: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)
