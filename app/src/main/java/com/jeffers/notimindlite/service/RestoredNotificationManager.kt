package com.jeffers.notimindlite.service

import android.app.NotificationManager
import android.content.Context
import android.util.Log
import java.util.concurrent.ConcurrentHashMap

/**
 * RestoredNotificationManager handles the state and tracking for notifications
 * that were captured and are being restored after a device reboot.
 */
object RestoredNotificationManager {
    private const val TAG = "RestoredNotifMgr"

    private val restoredNotificationKeys = ConcurrentHashMap.newKeySet<String>()
    private val restoredAppNotifIds = ConcurrentHashMap<String, MutableSet<Int>>()
    private val restoredAppKeys = ConcurrentHashMap<String, MutableSet<String>>()

    fun markAsRestored(notificationKey: String, packageName: String? = null, notifId: Int? = null) {
        restoredNotificationKeys.add(notificationKey)
        if (packageName != null) {
            restoredAppKeys.computeIfAbsent(packageName) { ConcurrentHashMap.newKeySet() }.add(notificationKey)
            if (notifId != null) {
                restoredAppNotifIds.computeIfAbsent(packageName) { ConcurrentHashMap.newKeySet() }.add(notifId)
            }
        }
        Log.d(TAG, "Marked as restored: $notificationKey (pkg: $packageName, notifId: $notifId)")
    }

    fun isRestored(notificationKey: String): Boolean {
        return restoredNotificationKeys.contains(notificationKey)
    }

    fun getRestoredNotificationIdsForApp(packageName: String): Set<Int> {
        return restoredAppNotifIds[packageName] ?: emptySet()
    }

    fun getRestoredNotifIdForPackage(packageName: String): Int? {
        return restoredAppNotifIds[packageName]?.firstOrNull()
    }

    private const val AUTO_DISMISS_LOG_MAX_LEN = 80

    /**
     * Auto-dismisses NotiMind's restored notification when the original app restores its own.
     */
    fun onOriginalAppNotificationPosted(context: Context, packageName: String) {
        val keys = restoredAppKeys.remove(packageName)
        if (!keys.isNullOrEmpty()) {
            restoredNotificationKeys.removeAll(keys)
        }
        val notifIds = restoredAppNotifIds.remove(packageName)
        if (!notifIds.isNullOrEmpty()) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            for (id in notifIds) {
                try {
                    notificationManager?.cancel(id)
                    Log.i(
                        TAG,
                        "Auto-dismissed restored notification #$id because original app " +
                            "($packageName) posted a notification".take(AUTO_DISMISS_LOG_MAX_LEN)
                    )
                } catch (e: SecurityException) {
                    // POST_NOTIFICATIONS not granted or notification channel removed.
                    Log.w(TAG, "Cannot auto-dismiss restored notification #$id: ${e.message}")
                } catch (e: IllegalStateException) {
                    // NotificationManager backing service unavailable.
                    Log.w(TAG, "NotificationManager unavailable for #$id: ${e.message}")
                }
            }
        }
    }

    fun clearRestoredState() {
        restoredNotificationKeys.clear()
        restoredAppNotifIds.clear()
        restoredAppKeys.clear()
    }
}
