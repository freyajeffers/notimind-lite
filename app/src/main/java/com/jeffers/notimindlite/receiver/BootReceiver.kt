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
import com.jeffers.notimindlite.R
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
        private const val QUICKBOOT_ACTION = "android.intent.action.QUICKBOOT_POWERON"
        private const val RESTORED_NOTIFICATION_OFFSET = 1000
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent?.action == null) return
        val action = intent.action ?: return
        if (action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            com.jeffers.notimindlite.data.local.PreferenceManager(context)
                .setLastUpdateTime(System.currentTimeMillis())
            return
        }
        if (!isBootAction(action)) return

        val userManager = context.getSystemService(Context.USER_SERVICE) as? UserManager
        scheduleBootRestore(context, userManager?.isUserUnlocked ?: true)
    }

    private fun isBootAction(action: String): Boolean = action == Intent.ACTION_LOCKED_BOOT_COMPLETED ||
        action == Intent.ACTION_BOOT_COMPLETED || action == QUICKBOOT_ACTION

    private fun scheduleBootRestore(context: Context, isUserUnlocked: Boolean) {
        val pendingResult = goAsync()
        receiverScope.launch {
            try {
                requestListenerRebind(context)
                val preferences = com.jeffers.notimindlite.data.local.PreferenceManager(context)
                if (!preferences.isRestoreOnBootEnabled() || !isUserUnlocked) {
                    logRestoreSkipped(isUserUnlocked)
                    return@launch
                }
                restoreActiveNotifications(context)
            } catch (e: SecurityException) {
                Log.w("BootReceiverLite", "Permission denied restoring notifications on boot", e)
            } catch (e: android.database.sqlite.SQLiteException) {
                Log.e("BootReceiverLite", "Database error during boot restoration", e)
            } catch (e: IllegalStateException) {
                Log.e("BootReceiverLite", "Illegal state during boot restoration", e)
            } catch (e: IllegalArgumentException) {
                Log.e("BootReceiverLite", "Invalid argument during boot restoration", e)
            } finally {
                pendingResult?.finish()
            }
        }
    }

    private fun requestListenerRebind(context: Context) {
        try {
            NotificationListenerService.requestRebind(
                ComponentName(context, NotificationLoggerService::class.java)
            )
        } catch (e: SecurityException) {
            Log.w("BootReceiverLite", "Notification listener rebind was denied", e)
        }
    }

    private fun logRestoreSkipped(isUserUnlocked: Boolean) {
        if (!isUserUnlocked) {
            Log.i(
                "BootReceiverLite",
                "Pre-unlock boot restoration skipped (security policy, F-L). " +
                    "Notification listener rebind already requested above."
            )
        }
    }

    private suspend fun restoreActiveNotifications(context: Context) {
        val db = AppDatabase.getDatabase(context.applicationContext)
        val activeNotifications = db.notificationDao().getActiveNotificationsList()
        val consolidatedNotifications = BootRestoreManager.consolidateForBoot(activeNotifications)
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID_RESTORED,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "Restored active status bar notifications on boot" }
        )
        consolidatedNotifications.forEach { item ->
            restoreNotificationGroup(context, notificationManager, activeNotifications, item)
        }
    }

    private fun restoreNotificationGroup(
        context: Context,
        notificationManager: NotificationManager,
        activeNotifications: List<com.jeffers.notimindlite.data.local.NotificationEntity>,
        item: com.jeffers.notimindlite.service.BootConsolidationResult,
    ) {
        val firstId = item.originalIds.first()
        val notificationId = (firstId.hashCode() and 0x7FFFFFFF) + RESTORED_NOTIFICATION_OFFSET
        val notification = NotificationCompat.Builder(context, CHANNEL_ID_RESTORED)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle("${item.appName}: ${item.summaryTitle}")
            .setContentText(item.summaryContent)
            .setSubText(context.getString(R.string.restored_after_reboot))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
        notificationManager.notify(notificationId, notification)
        item.originalIds.forEach { originalId ->
            activeNotifications.find { it.id == originalId }?.let { original ->
                com.jeffers.notimindlite.service.RestoredNotificationManager.markAsRestored(
                    original.key, original.packageName, notificationId
                )
            }
        }
    }
}
