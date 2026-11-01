package com.jeffers.notimindlite.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavOptions
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navOptions
import com.jeffers.notimindlite.R
import com.jeffers.notimindlite.data.auth.AuthManager
import com.jeffers.notimindlite.data.local.AppDatabase
import com.jeffers.notimindlite.data.local.NotificationDao
import com.jeffers.notimindlite.data.local.PreferencesRepository
import com.jeffers.notimindlite.ui.screens.ActiveNotificationsScreen
import com.jeffers.notimindlite.ui.screens.LogHistoryScreen
import com.jeffers.notimindlite.ui.screens.SettingsScreen
import com.jeffers.notimindlite.ui.screens.SplashScreen

sealed class Screen(
    val route: String,
    val title: Int,
    val icon: @Composable () -> Unit
) {
    object Active : Screen(
        route = "active",
        title = R.string.nav_active_title,
        icon = {
            Icon(
                Icons.Default.NotificationsActive,
                contentDescription = stringResource(id = R.string.nav_active_title)
            )
        }
    )

    object History : Screen(
        route = "history",
        title = R.string.nav_history_title,
        icon = {
            Icon(
                Icons.Default.History,
                contentDescription = stringResource(id = R.string.nav_history_title)
            )
        }
    )

    object Settings : Screen(
        route = "settings",
        title = R.string.nav_settings_title,
        icon = {
            Icon(
                Icons.Default.Settings,
                contentDescription = stringResource(id = R.string.nav_settings_action_cd)
            )
        }
    )
}

internal fun bottomTabNavigationOptions(): NavOptions = navOptions {
    popUpTo(Screen.Active.route) { saveState = true }
    launchSingleTop = true
    restoreState = true
}

@Composable
fun MainNavigation(
    modifier: Modifier = Modifier,
    notificationDao: NotificationDao,
    authManager: AuthManager,
    preferencesRepository: com.jeffers.notimindlite.data.local.PreferencesRepository
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val items = listOf(Screen.Active, Screen.History, Screen.Settings)

    Scaffold(
        bottomBar = {
            if (currentRoute != "splash") {
                NavigationBar {
                    items.forEach { screen ->
                        NavigationBarItem(
                            icon = screen.icon,
                            label = { Text(stringResource(id = screen.title)) },
                            selected = currentRoute == screen.route,
                            onClick = {
                                navController.navigate(screen.route, bottomTabNavigationOptions())
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "splash",
            modifier = modifier.padding(innerPadding)
        ) {
            composable("splash") {
                SplashScreen(onTimeout = {
                    navController.navigate(Screen.Active.route) {
                        popUpTo("splash") { inclusive = true }
                    }
                })
            }
            composable(Screen.Active.route) {
                ActiveNotificationsScreen(notificationDao, preferencesRepository)
            }
            composable(Screen.History.route) {
                LogHistoryScreen(notificationDao, preferencesRepository)
            }
            composable(Screen.Settings.route) {
                SettingsScreen(authManager = authManager, preferencesRepository = preferencesRepository)
            }
        }
    }
}
