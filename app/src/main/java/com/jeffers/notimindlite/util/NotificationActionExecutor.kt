package com.jeffers.notimindlite.util

import android.content.Context
import com.jeffers.notimindlite.data.local.AutoExecuteRule
import com.jeffers.notimindlite.data.local.PreferencesRepository
import com.jeffers.notimindlite.service.NotificationLoggerService

/** Applies automation preferences to notification actions. Kept side-effect free except for the selected PendingIntent. */
class NotificationActionExecutor(
    private val preferences: PreferencesRepository
) {
    fun executeOnNotification(context: Context, key: String, packageName: String, title: String): Boolean {
        val rule = matchingRule(packageName, title)
        if (rule != null) return NotificationLauncher.triggerAction(context, key, rule.actionIndex)
        if (!preferences.autoActOnNotification.value) return false
        return NotificationLauncher.triggerAction(context, key, 0)
    }

    fun executeLongPress(context: Context, key: String, packageName: String, title: String): Boolean = when (preferences.longPressAction.value) {
        "reply" -> NotificationLauncher.triggerAction(context, key, 0)
        "dismiss" -> {
            NotificationLoggerService.dismissNotification(key)
            true
        }
        else -> {
            preferences // Keep the action decision tied to the same profile as the UI.
            NotificationLauncher.launchNotification(context, packageName, key)
            true
        }
    }

    fun matchingRule(packageName: String, title: String): AutoExecuteRule? =
        preferences.autoExecuteRules.value.firstOrNull { rule ->
            rule.enabled && (rule.packageName.isBlank() || rule.packageName.equals(packageName, ignoreCase = true)) &&
                (rule.titleContains.isBlank() || title.contains(rule.titleContains, ignoreCase = true))
        }
}
