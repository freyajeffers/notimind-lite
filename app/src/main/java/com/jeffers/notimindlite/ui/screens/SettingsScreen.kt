package com.jeffers.notimindlite.ui.screens

import android.content.Context
import android.content.Intent
import android.provider.Settings

fun checkNotificationPermission(context: Context): Boolean {
    val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
    return flat != null && flat.contains(context.packageName)
}
