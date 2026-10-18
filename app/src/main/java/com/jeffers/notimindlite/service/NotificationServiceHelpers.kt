package com.jeffers.notimindlite.service

import android.service.notification.StatusBarNotification
import com.jeffers.notimindlite.data.local.AppDatabase
import com.jeffers.notimindlite.data.local.PreferencesRepository
import com.jeffers.notimindlite.util.NotificationLauncher

internal fun NotificationLoggerService.database(): AppDatabase =
    AppDatabase.getDatabase(applicationContext)

internal fun NotificationLoggerService.isCaptureEnabled(): Boolean =
    PreferencesRepository(applicationContext).captureManager.enabled.value

internal fun parseNotificationPackageList(raw: String): Set<String> =
    raw.split(',', '\n', ' ', '\t')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .toSet()

internal fun registerNotificationActions(sbn: StatusBarNotification, key: String) {
    sbn.notification?.actions?.forEachIndexed { index, action ->
        NotificationLauncher.registerActionIntent(key, index, action.actionIntent)
    }
}
