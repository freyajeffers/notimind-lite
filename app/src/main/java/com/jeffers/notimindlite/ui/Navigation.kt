package com.jeffers.notimindlite.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.jeffers.notimindlite.data.auth.AuthManager
import com.jeffers.notimindlite.data.local.AppDatabase
import com.jeffers.notimindlite.data.local.NotificationDao
import com.jeffers.notimindlite.ui.screens.HomeScreen
import com.jeffers.notimindlite.ui.screens.LogHistoryScreen
import com.jeffers.notimindlite.ui.screens.OnboardingScreen

sealed class Screen(val route: String, val title: String, val icon: @Composable () -> Unit) {
    object Home : Screen(
        route = "home",
        title = "Active",
        icon = { Icon(Icons.Default.NotificationsActive, contentDescription = "Active") }
    )
    object History : Screen(
        route = "history",
        title = "History",
        icon = { Icon(Icons.Default.History, contentDescription = "History") }
    )
}

@Composable
fun MainNavigation(
    modifier: Modifier = Modifier,
    notificationDao: NotificationDao,
    authManager: AuthManager,
    db: AppDatabase
) {
    val navController = rememberNavController()
    val items = listOf(Screen.Home, Screen.History)

    Scaffold(
        topBar = {
            // Placeholder for future top bar actions (e.g., Settings)
        },
        bottomBar = {
            NavigationBar {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route
                items.forEach { screen ->
                    NavigationBarItem(
                        icon = screen.icon,
                        label = { Text(screen.title) },
                        selected = currentRoute == screen.route,
                        onClick = {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "onboarding",
            modifier = modifier.padding(innerPadding)
        ) {
            composable("onboarding") {
                OnboardingScreen(onFinish = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo("onboarding") { inclusive = true }
                    }
                })
            }
            composable(Screen.Home.route) {
                HomeScreen(notificationDao, authManager, db)
            }
            composable(Screen.History.route) {
                LogHistoryScreen(notificationDao, authManager, db)
            }
        }
    }
}
