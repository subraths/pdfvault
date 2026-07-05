package com.pdfvault.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.pdfvault.ui.setup.SetupScreen
import com.pdfvault.ui.viewer.PdfViewerScreen

@Composable
fun AppNavigation(startConfigured: Boolean) {
    val navController = rememberNavController()
    val start = if (startConfigured) Routes.MAIN else Routes.SETUP

    NavHost(navController = navController, startDestination = start) {
        composable(Routes.SETUP) {
            SetupScreen(
                onConfigured = {
                    // Works both as the initial setup and when adding an account from within MAIN:
                    // popping SETUP and single-topping MAIN returns to the existing session UI.
                    navController.navigate(Routes.MAIN) {
                        popUpTo(Routes.SETUP) { inclusive = true }
                        launchSingleTop = true
                    }
                },
            )
        }

        composable(Routes.MAIN) {
            MainScreen(
                onOpenPdf = { key -> navController.navigate(Routes.viewer(key)) },
                onSignedOut = {
                    navController.navigate(Routes.SETUP) {
                        popUpTo(Routes.MAIN) { inclusive = true }
                    }
                },
                onAddAccount = { navController.navigate(Routes.SETUP) },
            )
        }

        composable(
            route = Routes.VIEWER,
            arguments = listOf(navArgument("keyB64") { type = NavType.StringType }),
        ) { entry ->
            val keyB64 = entry.arguments?.getString("keyB64").orEmpty()
            PdfViewerScreen(
                objectKey = NavArgs.decode(keyB64),
                onBack = { navController.popBackStack() },
            )
        }
    }
}
