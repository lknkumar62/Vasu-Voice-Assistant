package com.vasu.assistant.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.vasu.assistant.core.navigation.Screen
import com.vasu.assistant.ui.automation.AutomationScreen
import com.vasu.assistant.ui.browser.BrowserScreen
import com.vasu.assistant.ui.chat.ChatScreen
import com.vasu.assistant.ui.diagnostics.DiagnosticsScreen
import com.vasu.assistant.ui.guardian.GuardianScreen
import com.vasu.assistant.ui.home.HomeScreen
import com.vasu.assistant.ui.location.LocationScreen
import com.vasu.assistant.ui.memory.MemoryScreen
import com.vasu.assistant.ui.missions.MissionsScreen
import com.vasu.assistant.ui.permissions.PermissionsScreen
import com.vasu.assistant.ui.privacy.PrivacyScreen
import com.vasu.assistant.ui.settings.SettingsScreen
import com.vasu.assistant.ui.tools.ToolsScreen
import com.vasu.assistant.ui.voice.VoiceScreen
import com.vasu.assistant.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    val navController = rememberNavController()

    // Top-level navigation with bounded backstack: single top instance,
    // state restored, and everything else popped down to the start destination.
    val navigateTop: (String) -> Unit = { route ->
        navController.navigate(route) {
            launchSingleTop = true
            restoreState = true
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
        }
    }

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
                val backStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = backStackEntry?.destination

                val items = listOf(
                    NavigationItem("Home", Screen.Home.route, Icons.Default.Home),
                    NavigationItem("Chat", Screen.Chat.route, Icons.Default.Chat),
                    NavigationItem("Tools", Screen.Tools.route, Icons.Default.Build),
                    NavigationItem("Settings", Screen.Settings.route, Icons.Default.Settings),
                    NavigationItem("Voice", Screen.Voice.route, Icons.Default.Mic),
                )

                items.forEach { item ->
                    NavigationBarItem(
                        icon = { Icon(item.icon, contentDescription = item.label) },
                        label = { Text(item.label, fontSize = 10.sp) },
                        selected = currentDestination?.hierarchy?.any { it.route == item.route } == true,
                        onClick = {
                            navController.navigate(item.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
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
                        onNavigateToChat = { navigateTop(Screen.Chat.route) },
                        onNavigateToVoice = { navigateTop(Screen.Voice.route) },
                        onNavigateToSettings = { navigateTop(Screen.Settings.route) },
                        onNavigateToGuardian = { navigateTop(Screen.Guardian.route) },
                        onNavigateToMissions = { navigateTop(Screen.Missions.route) },
                        onNavigateToAutomation = { navigateTop(Screen.Automation.route) },
                        onNavigateToMemory = { navigateTop(Screen.Memory.route) },
                        onNavigateToTools = { navigateTop(Screen.Tools.route) },
                        onNavigateToPermissions = { navigateTop(Screen.Permissions.route) },
                        onNavigateToPrivacy = { navigateTop(Screen.Privacy.route) }
                    )
                }
                composable(Screen.Chat.route) {
                    ChatScreen(onNavigateBack = { navController.popBackStack() })
                }
                composable(Screen.Voice.route) {
                    VoiceScreen(
                        onNavigateBack = { navController.popBackStack() },
                        onNavigateToSettings = { navigateTop(Screen.Settings.route) }
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
                    MemoryScreen(onNavigateBack = { navController.popBackStack() })
                }
                composable(Screen.Tools.route) {
                    ToolsScreen(
                        onNavigateBack = { navController.popBackStack() },
                        onToolSelect = { toolId ->
                            when (toolId) {
                                "settings" -> navigateTop(Screen.Settings.route)
                                "missions" -> navigateTop(Screen.Missions.route)
                                "location" -> navigateTop(Screen.Location.route)
                                "browser" -> navigateTop(Screen.Browser.route)
                                "permissions" -> navigateTop(Screen.Permissions.route)
                                "privacy" -> navigateTop(Screen.Privacy.route)
                                "guardian" -> navigateTop(Screen.Guardian.route)
                                "memory" -> navigateTop(Screen.Memory.route)
                                "automation" -> navigateTop(Screen.Automation.route)
                                "diagnostics" -> navigateTop(Screen.Diagnostics.route)
                                "voice" -> navigateTop(Screen.Voice.route)
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
