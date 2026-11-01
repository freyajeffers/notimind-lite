package com.jeffers.notimindlite.service

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
    private val restoredIdsByKey = ConcurrentHashMap<String, Int>()

    fun markAsRestored(notificationKey: String, packageName: String? = null, notifId: Int? = null) {
        restoredNotificationKeys.add(notificationKey)
        if (notifId != null) restoredIdsByKey[notificationKey] = notifId
        if (packageName != null) {
            restoredAppKeys.computeIfAbsent(packageName) { ConcurrentHashMap.newKeySet() }.add(notificationKey)
            if (notifId != null) {
                restoredAppNotifIds.computeIfAbsent(packageName) { ConcurrentHashMap.newKeySet() }.add(notifId)
            }
        }
        Log.d(TAG, "Marked as restored: $notificationKey (pkg: $packageName, notifId: $notifId)")
    }

    fun isRestored(notificationKey: String): Boolean = restoredNotificationKeys.contains(notificationKey)

    fun getRestoredNotificationIdsForApp(packageName: String): Set<Int> = restoredAppNotifIds[packageName] ?: emptySet()

    fun getRestoredNotifIdForPackage(packageName: String): Int? = restoredAppNotifIds[packageName]?.firstOrNull()

    /** Original-app posts no longer dismiss restored notifications; app UI owns dismissal. */
    @Suppress("UNUSED_PARAMETER")
    fun onOriginalAppNotificationPosted(context: Context, packageName: String) = Unit

    fun forgetNotificationId(notificationId: Int) {
        val keys = restoredIdsByKey.entries
            .filter { it.value == notificationId }
            .map { it.key }
            .toSet()
        keys.forEach {
            restoredIdsByKey.remove(it)
            restoredNotificationKeys.remove(it)
        }
        restoredAppKeys.entries.forEach { entry ->
            entry.value.removeAll(keys)
            if (entry.value.isEmpty()) restoredAppKeys.remove(entry.key, entry.value)
        }
        restoredAppNotifIds.entries.forEach { entry ->
            entry.value.remove(notificationId)
            if (entry.value.isEmpty()) restoredAppNotifIds.remove(entry.key, entry.value)
        }
    }

    fun clearRestoredState() {
        restoredNotificationKeys.clear()
        restoredAppNotifIds.clear()
        restoredAppKeys.clear()
        restoredIdsByKey.clear()
    }
}
