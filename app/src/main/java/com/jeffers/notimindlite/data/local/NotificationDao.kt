package com.jeffers.notimindlite.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RawQuery
import androidx.sqlite.db.SupportSQLiteQuery
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

    @Query("SELECT * FROM notifications WHERE key = :key LIMIT 1")
    suspend fun getNotificationByKey(key: String): NotificationEntity?

    @Query("SELECT * FROM notifications WHERE isPinned = 1 ORDER BY postTime DESC")
    fun getPinnedNotificationsFlow(): Flow<List<NotificationEntity>>

    // SQLite Full-Text Search (FTS4)
    @Query("""
        SELECT notifications.* FROM notifications
        JOIN notifications_fts ON notifications.rowid = notifications_fts.docid
        WHERE notifications_fts MATCH :searchQuery
        ORDER BY postTime DESC
    """)
    fun searchNotificationsFts(searchQuery: String): Flow<List<NotificationEntity>>

    @RawQuery(observedEntities = [NotificationEntity::class])
    fun searchNotificationsRaw(query: SupportSQLiteQuery): Flow<List<NotificationEntity>>

    @Query("INSERT INTO notifications_fts(notifications_fts) VALUES('rebuild')")
    suspend fun rebuildFtsIndex()

    @Query("UPDATE notifications SET isPinned = :isPinned WHERE key = :key")
    suspend fun updatePinnedStatus(key: String, isPinned: Boolean)

    @Query("UPDATE notifications SET isRead = 1 WHERE key = :key")
    suspend fun markAsRead(key: String)

    @Query("UPDATE notifications SET isRead = 1 WHERE isRead = 0")
    suspend fun markAllAsRead()

    @Query("SELECT COUNT(*) FROM notifications WHERE isRead = 0 AND isDismissed = 0")
    fun getUnreadCountFlow(): Flow<Int>

    @Query("""
        UPDATE notifications 
        SET isDismissed = 1, dismissTime = :dismissTime 
        WHERE key = :key 
           OR (
               :key = '' 
               AND packageName = :packageName 
               AND title = :title 
               AND content = :content 
               AND title != '' 
               AND content != ''
           )
    """)
    suspend fun markDismissedByMatching(key: String, packageName: String, title: String, content: String, dismissTime: Long = System.currentTimeMillis())

    @Query("""
        UPDATE notifications 
        SET isDismissed = 1, dismissReason = :reason, dismissTime = :dismissTime 
        WHERE key = :key 
           OR (
               :key = '' 
               AND packageName = :packageName 
               AND title = :title 
               AND content = :content 
               AND title != '' 
               AND content != ''
           )
    """)
    suspend fun markDismissedWithReasonByMatching(key: String, packageName: String, title: String, content: String, reason: Int, dismissTime: Long = System.currentTimeMillis())

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

    @Query("DELETE FROM notifications WHERE isDismissed = 1 AND postTime < :cutoffTimeMs AND isPinned = 0")
    suspend fun pruneOldLogs(cutoffTimeMs: Long)

    @Query("DELETE FROM notifications")
    suspend fun clearAll()
}
