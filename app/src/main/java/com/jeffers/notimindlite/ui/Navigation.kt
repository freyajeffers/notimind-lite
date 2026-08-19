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
import com.jeffers.notimindlite.ui.screens.ActiveNotificationsScreen
import com.jeffers.notimindlite.ui.screens.LogHistoryScreen
import com.jeffers.notimindlite.ui.screens.SplashScreen

sealed class Screen(val route: String, val title: Int, val icon: @Composable () -> Unit) {
    object Active : Screen(
        route = "active",
        title = R.string.nav_active_title,
        icon = { Icon(Icons.Default.NotificationsActive, contentDescription = stringResource(id = R.string.nav_active_title)) }
    )
    object History : Screen(
        route = "history",
        title = R.string.nav_history_title,
        icon = { Icon(Icons.Default.History, contentDescription = stringResource(id = R.string.nav_history_title)) }
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
    val items = listOf(Screen.Active, Screen.History)
    val context = androidx.compose.ui.platform.LocalContext.current
    val preferenceManager = remember { com.jeffers.notimindlite.data.local.PreferenceManager(context) }

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
                        label = { Text(stringResource(id = screen.title)) },
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
            startDestination = if (preferenceManager.hasCompletedOnboarding()) "splash" else "onboarding",
            modifier = modifier.padding(innerPadding)
        ) {
            composable("onboarding") {
                com.jeffers.notimindlite.ui.screens.OnboardingScreen(
                    preferenceManager = preferenceManager,
                    onCompleted = {
                        navController.navigate("splash") {
                            popUpTo("onboarding") { inclusive = true }
                        }
                    }
                )
            }
            composable("splash") {
                SplashScreen(onTimeout = {
                    navController.navigate(Screen.Active.route) {
                        popUpTo("splash") { inclusive = true }
                    }
                })
            }
            composable(Screen.Active.route) {
                ActiveNotificationsScreen(notificationDao, authManager, db)
            }
            composable(Screen.History.route) {
                LogHistoryScreen(notificationDao, authManager, db)
            }
        }
    }
}
