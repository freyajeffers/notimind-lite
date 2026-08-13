package com.jeffers.notimindlite.receiver

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.UserManager
import android.service.notification.NotificationListenerService
import android.util.Log
import androidx.core.app.NotificationCompat
import com.jeffers.notimindlite.data.local.AppDatabase
import com.jeffers.notimindlite.service.NotificationLoggerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * BroadcastReceiver responsible for Direct Boot (LOCKED_BOOT_COMPLETED),
 * standard boot completion (BOOT_COMPLETED), and package update events.
 */
class BootReceiver : BroadcastReceiver() {

    private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        const val CHANNEL_ID_RESTORED = "restored_notifications_channel"
        const val CHANNEL_NAME = "Restored Active Notifications"
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        context ?: return
        val action = intent?.action ?: return

        val userManager = context.getSystemService(Context.USER_SERVICE) as? UserManager
        val isUserUnlocked = userManager?.isUserUnlocked ?: true

        Log.d("BootReceiverLite", "Received broadcast action: $action, isUserUnlocked: $isUserUnlocked")

        if (action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            val prefMgr = com.jeffers.notimindlite.data.local.PreferenceManager(context)
            prefMgr.setLastUpdateTime(System.currentTimeMillis())
            Log.d("BootReceiverLite", "Package replaced; recorded update timestamp.")
            return
        } else if (action == Intent.ACTION_LOCKED_BOOT_COMPLETED ||
            action == Intent.ACTION_BOOT_COMPLETED ||
            action == "android.intent.action.QUICKBOOT_POWERON"
        ) {
            Log.d("BootReceiverLite", "Device boot detected ($action). Restoring active status bar notifications...")

            val pendingResult = goAsync()

            receiverScope.launch {
                try {
                    // Attempt notification listener rebind as early as Direct Boot
                    try {
                        NotificationListenerService.requestRebind(
                            ComponentName(context, NotificationLoggerService::class.java)
                        )
                        Log.d("BootReceiverLite", "Requested NotificationLoggerService rebind.")
                    } catch (e: Exception) {
                        Log.d("BootReceiverLite", "NotificationListenerService rebind attempt: ${e.message}")
                    }

                    // Check preferences
                    val prefManager = com.jeffers.notimindlite.data.local.PreferenceManager(context)
                    if (!prefManager.isRestoreOnBootEnabled()) {
                        Log.d("BootReceiverLite", "Restore on boot is disabled by user preference. Skipping restoration.")
                        return@launch
                    }

                    // Only query credential-encrypted Room database if user is unlocked
                    if (!isUserUnlocked) {
                        Log.d("BootReceiverLite", "Direct boot active (User locked). Rebound service and deferred DB restore until unlock.")
                        return@launch
                    }

                    val db = AppDatabase.getDatabase(context.applicationContext)
                    val activeNotifs = db.notificationDao().getActiveNotificationsList()

                    val notificationManager =
                        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

                    val channel = NotificationChannel(
                        CHANNEL_ID_RESTORED,
                        CHANNEL_NAME,
                        NotificationManager.IMPORTANCE_DEFAULT
                    ).apply {
                        description = "Restored active status bar notifications on boot"
                    }
                    notificationManager.createNotificationChannel(channel)

                    var restoredCount = 0
                    for (notif in activeNotifs) {
                        if (notif.isOngoing || notif.isPersistent) {
                            continue
                        }

                        val notifId = (notif.id.hashCode() and 0x7FFFFFFF) + 1000

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
