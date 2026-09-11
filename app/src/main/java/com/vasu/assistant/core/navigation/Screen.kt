package com.vasu.assistant.core.navigation

sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object Chat : Screen("chat")
    data object Voice : Screen("voice")
    data object Settings : Screen("settings")
    data object Guardian : Screen("guardian")
    data object Missions : Screen("missions")
    data object Automation : Screen("automation")
    data object Memory : Screen("memory")
    data object Tools : Screen("tools")
    data object Permissions : Screen("permissions")
    data object Privacy : Screen("privacy")
    data object Location : Screen("location")
    data object Browser : Screen("browser")
}
