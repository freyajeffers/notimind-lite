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
import com.jeffers.notimindlite.service.BootRestoreManager
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

        if (action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            val prefMgr = com.jeffers.notimindlite.data.local.PreferenceManager(context)
            prefMgr.setLastUpdateTime(System.currentTimeMillis())
            
            return
        } else if (action == Intent.ACTION_LOCKED_BOOT_COMPLETED ||
            action == Intent.ACTION_BOOT_COMPLETED ||
            action == "android.intent.action.QUICKBOOT_POWERON"
        ) {
            
            val pendingResult = goAsync()

            receiverScope.launch {
                try {
                    // Attempt notification listener rebind as early as Direct Boot
                    try {
                        NotificationListenerService.requestRebind(
                            ComponentName(context, NotificationLoggerService::class.java)
                        )
                        
                    } catch (e: Exception) {
                        
                    }

                    // Check preferences
                    val prefManager = com.jeffers.notimindlite.data.local.PreferenceManager(context)
                    if (!prefManager.isRestoreOnBootEnabled()) {
                        
                        return@launch
                    }

                    // F-L fix [2026-09-02 audit]: The previous comment said
                    // "Only query credential-encrypted Room database if user is unlocked."
                    // That was misleading — `AppDatabase.getDatabase()` already routes to
                    // the correct DE/CE database via `isUserUnlocked`. The skip here is
                    // NOT a technical limitation; it is an intentional security policy:
                    // pre-unlock restoration would surface captured notification content
                    // on the lockscreen before the user authenticates, defeating the
                    // device-encrypted privacy boundary.
                    //
                    // To change this behavior, also:
                    //   1. Verify the DE Room database has rows to restore at
                    //      LOCKED_BOOT_COMPLETED (listener service must be directBootAware).
                    //   2. Audit NotificationCompat.Builder for FLAG_SECURE if sensitive.
                    //   3. Update user-facing docs (RESTORE_ON_BOOT pref description).
                    // Reference: docs/audit/2026-09-02-notimind-lite-integration.md F-L.
                    if (!isUserUnlocked) {
                        Log.i(
                            "BootReceiverLite",
                            "Pre-unlock boot restoration skipped (security policy, F-L). " +
                                "Notification listener rebind already requested above."
                        )
                        return@launch
                    }

                    val db = AppDatabase.getDatabase(context.applicationContext)
                    val activeNotifs = db.notificationDao().getActiveNotificationsList()

                    // apply intelligent grouping if count > 45
                    val consolidatedNotifs = BootRestoreManager.consolidateForBoot(activeNotifs)

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
                    for (item in consolidatedNotifs) {
                        val firstId = item.originalIds.first()
                        val notifId = (firstId.hashCode() and 0x7FFFFFFF) + 1000

                        val builder = NotificationCompat.Builder(context, CHANNEL_ID_RESTORED)
                            .setSmallIcon(android.R.drawable.stat_notify_chat)
                            .setContentTitle("${item.appName}: ${item.summaryTitle}")
                            .setContentText(item.summaryContent)
                            .setSubText("Restored after Reboot")
                            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                            .setAutoCancel(true)

                        notificationManager.notify(notifId, builder.build())
                        for (origId in item.originalIds) {
                            val origNotif = activeNotifs.find { it.id == origId }
                            if (origNotif != null) {
                                com.jeffers.notimindlite.service.RestoredNotificationManager.markAsRestored(
                                    origNotif.key,
                                    origNotif.packageName,
                                    notifId
                                )
                            }
                        }
                        restoredCount++
                    }
                    
                } catch (e: Exception) {
                    Log.e("BootReceiverLite", "Failed to restore notifications on boot: ${e.message}")
                } finally {
                    pendingResult?.finish()
                }
            }
        }
    }
}
