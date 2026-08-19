package com.jeffers.notimindlite.service

import android.util.Log

/**
 * RestoredNotificationManager handles the state and logic for notifications 
 * that were captured and are being restored after a device reboot.
 */
object RestoredNotificationManager {
    private const val TAG = "RestoredNotifMgr"
    
    private val restoredNotifications = mutableSetOf<String>()

    fun markAsRestored(notificationKey: String) {
        restoredNotifications.add(notificationKey)
        
    }

    fun isRestored(notificationKey: String): Boolean {
        return restoredNotifications.contains(notificationKey)
    }

    fun clearRestoredState() {
        restoredNotifications.clear()
        
    }
}
