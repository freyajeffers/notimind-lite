package com.jeffers.notimindlite.data.local

import androidx.room.Query

interface NotificationMaintenanceOperations {
    @Query("DELETE FROM notifications WHERE isDismissed = 1 AND postTime < :cutoffTimeMs AND isPinned = 0")
    suspend fun pruneOldLogs(cutoffTimeMs: Long)

    @Query("DELETE FROM notification_groups")
    suspend fun clearGroups()

    @Query("DELETE FROM notifications")
    suspend fun clearAll()
}
