package com.jeffers.notimindlite.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.jeffers.notimindlite.data.local.NotificationDao
import com.jeffers.notimindlite.ui.screens.ActiveNotificationsScreen
import com.jeffers.notimindlite.ui.screens.LogHistoryScreen

sealed class Screen(val route: String, val title: String, val icon: @Composable () -> Unit) {
    object Active : Screen(
        route = "active",
        title = "Active",
        icon = { Icon(Icons.Default.NotificationsActive, contentDescription = "Active Notifications") }
    )

    object History : Screen(
        route = "history",
        title = "History",
        icon = { Icon(Icons.Default.History, contentDescription = "Log History") }
    )
}

@Composable
fun MainNavigationGraph(dao: NotificationDao) {
    val navController = rememberNavController()
    val items = listOf(Screen.Active, Screen.History)

    Scaffold(
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
                            if (currentRoute != screen.route) {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Active.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Active.route) {
                ActiveNotificationsScreen(dao = dao)
            }
            composable(Screen.History.route) {
                LogHistoryScreen(dao = dao)
            }
        }
    }
}
