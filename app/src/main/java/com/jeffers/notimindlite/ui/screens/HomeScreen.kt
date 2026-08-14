package com.jeffers.notimindlite.ui.screens

import androidx.compose.runtime.Composable
import com.jeffers.notimindlite.data.auth.AuthManager
import com.jeffers.notimindlite.data.local.AppDatabase
import com.jeffers.notimindlite.data.local.NotificationDao

/**
 * Home screen that currently shows the active notifications.
 * Future enhancements can include quick actions and summary widgets.
 */
@Composable
fun HomeScreen(
    dao: NotificationDao,
    authManager: AuthManager,
    db: AppDatabase
) {
    // Reuse the existing ActiveNotificationsScreen for now.
    ActiveNotificationsScreen(dao, authManager, db)
}
