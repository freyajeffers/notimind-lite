package com.jeffers.notimindlite.data.local

import androidx.room.Query
import kotlinx.coroutines.flow.Flow

interface NotificationCountQueries {
    @Query("SELECT COUNT(*) FROM notifications")
    suspend fun getNotificationCount(): Int

    @Query("SELECT COUNT(*) FROM notifications WHERE isDismissed = 1")
    fun getDismissedNotificationCountFlow(): Flow<Int>

    @Query("SELECT COUNT(*) FROM notifications")
    fun getTotalNotificationCountFlow(): Flow<Int>

    @Query("SELECT COUNT(*) FROM notifications WHERE isRead = 0 AND isDismissed = 0")
    fun getUnreadCountFlow(): Flow<Int>
}
