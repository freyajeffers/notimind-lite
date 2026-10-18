package com.jeffers.notimindlite.data.local

import androidx.room.Query
import kotlinx.coroutines.flow.Flow

private const val DISMISS_REASON_LIST = "1, 2, 3, 12, 19, 23"
private const val RECENT_DISMISSED_QUERY =
    "SELECT * FROM notifications WHERE isDismissed = 1 AND dismissReason IN ($DISMISS_REASON_LIST) " +
        "ORDER BY COALESCE(dismissTime, postTime) DESC"
private const val LOST_NOTIFICATIONS_QUERY =
    "SELECT * FROM notifications WHERE isDismissed = 1 AND " +
        "(dismissReason NOT IN ($DISMISS_REASON_LIST) OR dismissReason IS NULL) " +
        "ORDER BY COALESCE(dismissTime, postTime) DESC"

interface NotificationStateQueries {
    @Query("SELECT * FROM notifications WHERE isDismissed = 0 ORDER BY isOngoing DESC, postTime DESC")
    fun getActiveNotifications(): Flow<List<NotificationEntity>>

    @Query("SELECT * FROM notifications WHERE isDismissed = 0 ORDER BY isOngoing DESC, postTime DESC")
    fun getActiveNotificationsFlow(): Flow<List<NotificationEntity>>

    @Query("SELECT * FROM notifications WHERE isDismissed = 0 ORDER BY isOngoing DESC, postTime DESC")
    suspend fun getActiveNotificationsList(): List<NotificationEntity>

    @Query(RECENT_DISMISSED_QUERY)
    fun getRecentlyDismissedFlow(): Flow<List<NotificationEntity>>

    @Query(LOST_NOTIFICATIONS_QUERY)
    fun getLostNotificationsFlow(): Flow<List<NotificationEntity>>

    @Query("""
        SELECT * FROM notifications
        WHERE (packageName IN ('android', 'com.android.systemui', 'com.google.android.googlequicksearchbox')
           OR category IN ('sys', 'service', 'status', 'progress')
           OR priority < 0)
        ORDER BY postTime DESC
    """)
    fun getFilteredNotificationsFlow(): Flow<List<NotificationEntity>>

    @Query("SELECT * FROM notifications WHERE isPinned = 1 ORDER BY postTime DESC")
    fun getPinnedNotificationsFlow(): Flow<List<NotificationEntity>>
}
