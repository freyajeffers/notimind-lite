package com.jeffers.notimindlite.receiver

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.UserManager
import android.service.notification.NotificationListenerService
import android.util.Log
import com.jeffers.notimindlite.data.local.AppDatabase
import com.jeffers.notimindlite.data.local.NotificationEntity
import com.jeffers.notimindlite.data.local.PreferencesRepository
import com.jeffers.notimindlite.service.BootConsolidationResult
import com.jeffers.notimindlite.service.BootRestoreManager
import com.jeffers.notimindlite.service.NotificationLoggerService
import com.jeffers.notimindlite.service.NotificationRestoreCoordinator
import com.jeffers.notimindlite.service.RestoredNotificationState
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
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        context?.let { receiverContext ->
            intent?.action?.let { action ->
                handleAction(receiverContext, action)
            }
        }
    }

    private fun handleAction(context: Context, action: String) {
        when {
            action == Intent.ACTION_MY_PACKAGE_REPLACED -> {
                com.jeffers.notimindlite.data.local
                    .PreferenceManager(context)
                    .setLastUpdateTime(System.currentTimeMillis())
            }

            isBootAction(action) -> {
                val userManager = context.getSystemService(Context.USER_SERVICE) as? UserManager
                scheduleBootRestore(context, userManager?.isUserUnlocked ?: true)
            }
        }
    }

    private fun isBootAction(action: String): Boolean = action == Intent.ACTION_LOCKED_BOOT_COMPLETED ||
        action == Intent.ACTION_BOOT_COMPLETED || action == QUICKBOOT_ACTION

    private fun scheduleBootRestore(context: Context, isUserUnlocked: Boolean) {
        val pendingResult = goAsync()
        receiverScope.launch {
            try {
                requestListenerRebind(context)
                val preferences = com.jeffers.notimindlite.data.local
                    .PreferenceManager(context)
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
        val dao = AppDatabase.getDatabase(context.applicationContext).notificationDao()
        dao.getAllNotificationsSync().forEach { item ->
            if (RestoredNotificationState.notificationId(item.restoreMetadataJson) != null) {
                val nextState = if (item.isDismissed) {
                    RestoredNotificationState.withQueuedState(item.restoreMetadataJson)
                } else {
                    RestoredNotificationState.withoutRestoreState(item.restoreMetadataJson)
                }
                dao.updateRestoreMetadataJson(item.key, nextState)
            }
        }
        val activeNotifications = dao.getActiveNotificationsList()
        val consolidatedNotifications = BootRestoreManager.consolidateForBoot(activeNotifications)
        val redactPii = PreferencesRepository(context).securityManager.redactPii.value
        consolidatedNotifications.forEach { item ->
            restoreNotificationGroup(context, activeNotifications, item, redactPii)
        }
        NotificationRestoreCoordinator.restoreQueuedNotifications(context, dao)
    }

    private suspend fun restoreNotificationGroup(
        context: Context,
        activeNotifications: List<NotificationEntity>,
        item: BootConsolidationResult,
        redactPii: Boolean
    ) {
        val originals = item.originalIds.mapNotNull { originalId ->
            activeNotifications.find { it.id == originalId }
        }
        NotificationRestoreCoordinator.restoreBootGroup(
            context = context,
            dao = AppDatabase.getDatabase(context.applicationContext).notificationDao(),
            item = item,
            originals = originals,
            redactPii = redactPii
        )
    }
}
