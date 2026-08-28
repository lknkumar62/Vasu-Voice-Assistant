package com.vasu.assistant.core.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.vasu.assistant.ui.automation.AutomationScreen
import com.vasu.assistant.ui.chat.ChatScreen
import com.vasu.assistant.ui.guardian.GuardianScreen
import com.vasu.assistant.ui.home.HomeScreen
import com.vasu.assistant.ui.memory.MemoryScreen
import com.vasu.assistant.ui.missions.MissionsScreen
import com.vasu.assistant.ui.permissions.PermissionsScreen
import com.vasu.assistant.ui.privacy.PrivacyScreen
import com.vasu.assistant.ui.settings.SettingsScreen
import com.vasu.assistant.ui.tools.ToolsScreen
import com.vasu.assistant.ui.voice.VoiceScreen

@Composable
fun VasuNavGraph(
    navController: NavHostController = rememberNavController()
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Home.route
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
            VoiceScreen(onNavigateBack = { navController.popBackStack() })
        }

        composable(Screen.Settings.route) {
            SettingsScreen(onNavigateBack = { navController.popBackStack() })
        }

        composable(Screen.Guardian.route) {
            GuardianScreen(onNavigateBack = { navController.popBackStack() })
        }

        composable(Screen.Missions.route) {
            MissionsScreen()
        }

        composable(Screen.Automation.route) {
            AutomationScreen()
        }

        composable(Screen.Memory.route) {
            MemoryScreen()
        }

        composable(Screen.Tools.route) {
            ToolsScreen()
        }

        composable(Screen.Permissions.route) {
            PermissionsScreen()
        }

        composable(Screen.Privacy.route) {
            PrivacyScreen()
        }
    }
}
