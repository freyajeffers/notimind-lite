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

        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            action == "android.intent.action.QUICKBOOT_POWERON"
        ) {
            Log.d("BootReceiverLite", "Device boot/reboot detected. Restoring active status bar notifications...")

            val pendingResult = goAsync()

            receiverScope.launch {
                try {
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

                    val (activeStatusBarIds, activeTitleContents) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        try {
                            val activeNotifsArray = notificationManager.activeNotifications
                            val ids = activeNotifsArray.map { it.id }.toSet()
                            val titleContents = activeNotifsArray.mapNotNull { sbn ->
                                val extras = sbn.notification?.extras
                                val title = extras?.getCharSequence(android.app.Notification.EXTRA_TITLE)?.toString() ?: ""
                                val text = extras?.getCharSequence(android.app.Notification.EXTRA_TEXT)?.toString() ?: ""
                                if (title.isNotEmpty() || text.isNotEmpty()) Pair(title, text) else null
                            }.toSet()
                            Pair(ids, titleContents)
                        } catch (e: Exception) {
                            Pair(emptySet(), emptySet())
                        }
                    } else {
                        Pair(emptySet(), emptySet())
                    }

                    var restoredCount = 0
                    for (notif in activeNotifs) {
                        if (notif.isOngoing || notif.isPersistent) {
                            Log.d("BootReceiverLite", "Skipping ongoing notification from boot restoration: ${notif.title}")
                            continue
                        }
                        val notifId = (notif.id xor 0x7FFFFFFF).toInt()
                        val isIdMatch = activeStatusBarIds.contains(notifId)
                        val isContentMatch = activeTitleContents.any { (activeTitle, activeContent) ->
                            activeTitle.contains(notif.title, ignoreCase = true) || (notif.content.isNotEmpty() && activeContent.contains(notif.content, ignoreCase = true))
                        }

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
