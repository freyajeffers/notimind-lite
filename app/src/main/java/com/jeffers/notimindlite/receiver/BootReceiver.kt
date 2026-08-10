package com.jeffers.notimindlite.receiver

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.notification.NotificationListenerService
import android.util.Log
import androidx.core.app.NotificationCompat
import com.jeffers.notimindlite.data.local.AppDatabase
import com.jeffers.notimindlite.service.NotificationLoggerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {

    private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        const val CHANNEL_ID_RESTORED = "restored_notifications_channel"
        const val CHANNEL_NAME = "Restored Active Notifications"
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        context ?: return
        val action = intent?.action ?: return

        if (action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            val prefMgr = com.jeffers.notimindlite.data.local.PreferenceManager(context)
            prefMgr.setLastUpdateTime(System.currentTimeMillis())
            Log.d("BootReceiverLite", "Package replaced; recorded update timestamp.")
            return
        } else if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == "android.intent.action.QUICKBOOT_POWERON"
        ) {
            Log.d("BootReceiverLite", "Device boot/reboot detected. Restoring active status bar notifications...")

            val pendingResult = goAsync()

            receiverScope.launch {
                try {
                    // Check if a recent package update occurred; if so, skip restoration
                    val prefManager = com.jeffers.notimindlite.data.local.PreferenceManager(context)
                    val lastUpdate = prefManager.getLastUpdateTime()
                    val now = System.currentTimeMillis()
                    val updateWindowMs = 5 * 60 * 1000L // 5 minutes
                    if (now - lastUpdate < updateWindowMs) {
                        Log.d("BootReceiverLite", "App was recently updated; skipping notification restoration.")
                        return@launch
                    }

                    if (!prefManager.isRestoreOnBootEnabled()) {
                        Log.d("BootReceiverLite", "Restore on boot is disabled by user preference. Skipping restoration.")
                        return@launch
                    }

                    val db = AppDatabase.getDatabase(context.applicationContext)
                    val activeNotifs = db.notificationDao().getActiveNotificationsList()

                    val notificationManager =
                        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        val channel = NotificationChannel(
                            CHANNEL_ID_RESTORED,
                            CHANNEL_NAME,
                            NotificationManager.IMPORTANCE_DEFAULT
                        ).apply {
                            description = "Restored active status bar notifications on boot"
                        }
                        notificationManager.createNotificationChannel(channel)
                    }

                    // Collect active notification keys for deduplication
                    val activeKeys = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        try {
                            val activeNotifsArray = notificationManager.activeNotifications
                            activeNotifsArray.map { it.key }.toSet()
                        } catch (e: Exception) {
                            emptySet<String>()
                        }
                    } else {
                        emptySet<String>()
                    }

                    var restoredCount = 0
                    for (notif in activeNotifs) {
                        if (notif.isOngoing || notif.isPersistent) {
                            Log.d("BootReceiverLite", "Skipping ongoing notification from boot restoration: ${notif.title}")
                            continue
                        }
                        val notifId = (notif.id xor 0x7FFFFFFF).toInt()
                        // Skip if notification key already present in active status bar
                        if (activeKeys.contains(notif.key)) {
                            Log.d("BootReceiverLite", "Deduplication: Notification with key ${notif.key} already active. Skipping restoration.")
                            continue
                        }
                        // Fallback deduplication using title/content hash if needed (optional)
                        // Existing logic retained for safety
                        val isIdMatch = false // not used
                        val isContentMatch = false // not used

                        if (isIdMatch || isContentMatch) {
                            Log.d("BootReceiverLite", "Deduplication: Notification (ID: $notifId, Title: '${notif.title}') is already active in status bar. Skipping restoration.")
                            continue
                        }

                        val builder = NotificationCompat.Builder(context, CHANNEL_ID_RESTORED)
                            .setSmallIcon(android.R.drawable.stat_notify_chat)
                            .setContentTitle("${notif.appName}: ${notif.title}")
                            .setContentText(notif.content)
                            .setSubText("Restored after Reboot")
                            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                            .setAutoCancel(true)

                        notificationManager.notify(notifId, builder.build())
                        restoredCount++
                    }

                    // Rebind NotificationListenerService
                    try {
                        NotificationListenerService.requestRebind(
                            ComponentName(context, NotificationLoggerService::class.java)
                        )
                        Log.d("BootReceiverLite", "Rebound NotificationLoggerService on boot.")
                    } catch (e: Exception) {
                        Log.d("BootReceiverLite", "NotificationListenerService rebind attempt: ${e.message}")
                    }

                    Log.d("BootReceiverLite", "Restored $restoredCount notifications on boot.")
                } catch (e: Exception) {
                    Log.e("BootReceiverLite", "Failed to restore notifications on boot: ${e.message}")
                } finally {
                    pendingResult?.finish()
                }
            }
        }
    }
}
