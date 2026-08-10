package com.jeffers.notimindlite.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface NotificationDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: NotificationEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotification(notification: NotificationEntity): Long

    @Query("SELECT * FROM notifications ORDER BY postTime DESC")
    fun getAllNotifications(): Flow<List<NotificationEntity>>

    @Query("SELECT * FROM notifications ORDER BY postTime DESC")
    fun getAllNotificationsFlow(): Flow<List<NotificationEntity>>

    @Query("SELECT * FROM notifications ORDER BY postTime DESC")
    suspend fun getAllNotificationsList(): List<NotificationEntity>

    @Query("SELECT * FROM notifications ORDER BY postTime DESC")
    fun getAllNotificationsSortedByReceived(): Flow<List<NotificationEntity>>

    @Query("SELECT * FROM notifications ORDER BY COALESCE(dismissTime, postTime) DESC")
    fun getAllNotificationsSortedByDismissed(): Flow<List<NotificationEntity>>

    @Query("SELECT * FROM notifications WHERE isDismissed = 1 ORDER BY COALESCE(dismissTime, postTime) DESC")
    fun getDismissedNotificationsSortedByDismissed(): Flow<List<NotificationEntity>>

    @Query("SELECT * FROM notifications WHERE isDismissed = 1 ORDER BY postTime DESC")
    fun getDismissedNotificationsSortedByReceived(): Flow<List<NotificationEntity>>

    @Query("SELECT * FROM notifications WHERE isDismissed = 0 ORDER BY postTime DESC")
    fun getActiveNotifications(): Flow<List<NotificationEntity>>

    @Query("SELECT * FROM notifications WHERE isDismissed = 0 ORDER BY postTime DESC")
    fun getActiveNotificationsFlow(): Flow<List<NotificationEntity>>

    @Query("SELECT * FROM notifications WHERE isDismissed = 0 ORDER BY postTime DESC")
    suspend fun getActiveNotificationsList(): List<NotificationEntity>

    @Query("SELECT * FROM notifications WHERE isDismissed = 1 AND dismissReason IN (1, 2, 3) ORDER BY COALESCE(dismissTime, postTime) DESC")
    fun getRecentlyDismissedFlow(): Flow<List<NotificationEntity>>

    @Query("SELECT * FROM notifications WHERE isDismissed = 1 AND (dismissReason IN (5, 8) OR dismissReason NOT IN (1, 2, 3) OR dismissReason IS NULL) ORDER BY COALESCE(dismissTime, postTime) DESC")
    fun getLostNotificationsFlow(): Flow<List<NotificationEntity>>

    // New method to fetch notification by key for deduplication
    @Query("SELECT * FROM notifications WHERE key = :key LIMIT 1")
    suspend fun getNotificationByKey(key: String): NotificationEntity?

    @Query("SELECT * FROM notifications WHERE isPinned = 1 ORDER BY postTime DESC")
    fun getPinnedNotificationsFlow(): Flow<List<NotificationEntity>>

    @Query("UPDATE notifications SET isPinned = :isPinned WHERE key = :key")
    suspend fun updatePinnedStatus(key: String, isPinned: Boolean)

    @Query("UPDATE notifications SET isDismissed = 1, dismissTime = :dismissTime WHERE key = :key")
    suspend fun markDismissed(key: String, dismissTime: Long = System.currentTimeMillis())

    @Query("UPDATE notifications SET isDismissed = 1, dismissReason = :reason, dismissTime = :dismissTime WHERE key = :key")
    suspend fun markDismissedWithReason(key: String, reason: Int, dismissTime: Long = System.currentTimeMillis())

    @Query("SELECT COUNT(*) FROM notifications")
    suspend fun getNotificationCount(): Int

    @Query("SELECT COUNT(*) FROM notifications WHERE isDismissed = 1")
    fun getDismissedNotificationCountFlow(): Flow<Int>

    @Query("SELECT COUNT(*) FROM notifications")
    fun getTotalNotificationCountFlow(): Flow<Int>

    @Query("DELETE FROM notifications")
    suspend fun clearAll()
}
