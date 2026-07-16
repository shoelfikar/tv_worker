package com.tvworker.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.tvworker.data.repository.ApprovalRepository
import com.tvworker.ui.screen.log.LogScreen
import com.tvworker.ui.screen.log.LogViewModel
import com.tvworker.ui.screen.main.MainScreen
import com.tvworker.ui.screen.main.MainViewModel

@Composable
fun AppNavigation(
    repository: ApprovalRepository,
    onOpenAccessibilitySettings: () -> Unit
) {
    val navController = rememberNavController()
    val mainViewModel = remember { MainViewModel(repository) }

    NavHost(navController = navController, startDestination = "main") {
        composable("main") {
            MainScreen(
                viewModel = mainViewModel,
                onNavigateToLogs = { navController.navigate("logs") },
                onOpenAccessibilitySettings = onOpenAccessibilitySettings
            )
        }
        composable("logs") {
            LogScreen(
                viewModel = LogViewModel(repository),
                onBack = { navController.popBackStack() }
            )
        }
    }
}
