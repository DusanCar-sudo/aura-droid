package dev.aura.auradroid.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import dev.aura.auradroid.ui.screens.chat.ChatScreen
import dev.aura.auradroid.ui.screens.sessions.SessionsScreen
import dev.aura.auradroid.ui.screens.settings.SettingsScreen

@Composable
fun AuraNavHost(
    navController: NavHostController = androidx.navigation.compose.rememberNavController(),
    startDestination: String = "chat"
) {
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable("chat") {
            ChatScreen(
                onNavigateToSessions = { navController.navigate("sessions") },
                onNavigateToSettings = { navController.navigate("settings") }
            )
        }
        composable("sessions") {
            SessionsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable("settings") {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
