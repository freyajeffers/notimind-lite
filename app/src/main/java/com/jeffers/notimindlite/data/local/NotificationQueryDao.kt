package com.jeffers.notimindlite.data.local

import androidx.room.Dao
import androidx.room.RawQuery
import androidx.room.Query
import androidx.sqlite.db.SupportSQLiteQuery
import kotlinx.coroutines.flow.Flow

private const val DISMISS_REASON_LIST = "1, 2, 3, 12, 19, 23"
private const val RECENT_DISMISSED_QUERY =
    "SELECT * FROM notifications WHERE isDismissed = 1 AND dismissReason IN ($DISMISS_REASON_LIST) " +
        "ORDER BY COALESCE(dismissTime, postTime) DESC"
private const val LOST_NOTIFICATIONS_QUERY =
    "SELECT * FROM notifications WHERE isDismissed = 1 AND " +
        "(dismissReason NOT IN ($DISMISS_REASON_LIST) OR dismissReason IS NULL) " +
        "ORDER BY COALESCE(dismissTime, postTime) DESC"

@Dao
interface NotificationQueryDao {
    @Query("SELECT * FROM notifications ORDER BY postTime DESC")
    abstract fun getAllNotifications(): Flow<List<NotificationEntity>>

    @Query("SELECT * FROM notifications ORDER BY postTime DESC")
    abstract fun getAllNotificationsFlow(): Flow<List<NotificationEntity>>

    @Query("SELECT * FROM notifications ORDER BY postTime DESC")
    abstract suspend fun getAllNotificationsList(): List<NotificationEntity>

    @Query("SELECT * FROM notifications ORDER BY postTime DESC LIMIT :limit")
    abstract suspend fun getRecentNotificationsList(limit: Int): List<NotificationEntity>

    @Query("SELECT * FROM notifications ORDER BY postTime DESC")
    abstract fun getAllNotificationsSortedByReceived(): Flow<List<NotificationEntity>>

    @Query("SELECT * FROM notifications ORDER BY COALESCE(dismissTime, postTime) DESC")
    abstract fun getAllNotificationsSortedByDismissed(): Flow<List<NotificationEntity>>

    @Query("SELECT * FROM notifications WHERE isDismissed = 1 ORDER BY COALESCE(dismissTime, postTime) DESC")
    abstract fun getDismissedNotificationsSortedByDismissed(): Flow<List<NotificationEntity>>

    @Query("SELECT * FROM notifications WHERE isDismissed = 1 ORDER BY postTime DESC")
    abstract fun getDismissedNotificationsSortedByReceived(): Flow<List<NotificationEntity>>

    @Query("SELECT * FROM notifications WHERE isDismissed = 0 ORDER BY isOngoing DESC, postTime DESC")
    abstract fun getActiveNotifications(): Flow<List<NotificationEntity>>

    @Query("SELECT * FROM notifications WHERE isDismissed = 0 ORDER BY isOngoing DESC, postTime DESC")
    abstract fun getActiveNotificationsFlow(): Flow<List<NotificationEntity>>

    @Query("SELECT * FROM notifications WHERE isDismissed = 0 ORDER BY isOngoing DESC, postTime DESC")
    abstract suspend fun getActiveNotificationsList(): List<NotificationEntity>

    @Query(RECENT_DISMISSED_QUERY)
    abstract fun getRecentlyDismissedFlow(): Flow<List<NotificationEntity>>

    @Query(LOST_NOTIFICATIONS_QUERY)
    abstract fun getLostNotificationsFlow(): Flow<List<NotificationEntity>>

    @Query("""
        SELECT * FROM notifications 
        WHERE (packageName IN ('android', 'com.android.systemui', 'com.google.android.googlequicksearchbox') 
           OR category IN ('sys', 'service', 'status', 'progress') 
           OR priority < 0)
        ORDER BY postTime DESC
    """)
    abstract fun getFilteredNotificationsFlow(): Flow<List<NotificationEntity>>

    @Query("SELECT * FROM notifications WHERE key = :key LIMIT 1")
    abstract suspend fun getNotificationByKey(key: String): NotificationEntity?

    @Query("SELECT * FROM notifications WHERE id = :id LIMIT 1")
    abstract suspend fun getNotificationById(id: Long): NotificationEntity?

    @Query("SELECT * FROM notifications ORDER BY postTime DESC")
    abstract suspend fun getAllNotificationsSync(): List<NotificationEntity>

    @Query("SELECT * FROM notifications WHERE embedding IS NULL")
    abstract suspend fun getNotificationsNeedingVectorization(): List<NotificationEntity>

    @Query("UPDATE notifications SET embedding = :embedding WHERE id = :id")
    abstract suspend fun updateEmbedding(id: Long, embedding: ByteArray)

    @Query("SELECT * FROM notifications WHERE isPinned = 1 ORDER BY postTime DESC")
    abstract fun getPinnedNotificationsFlow(): Flow<List<NotificationEntity>>

    // FTS
    @Query("""
        SELECT notifications.* FROM notifications
        JOIN notifications_fts ON notifications.rowid = notifications_fts.docid
        WHERE notifications_fts MATCH :searchQuery
        ORDER BY postTime DESC
    """)
    abstract fun searchNotificationsFts(searchQuery: String): Flow<List<NotificationEntity>>

    @Query("""
        SELECT notifications.* FROM notifications
        JOIN notifications_fts ON notifications.rowid = notifications_fts.docid
        WHERE notifications_fts MATCH :searchQuery
        ORDER BY postTime DESC
    """)
    abstract suspend fun searchNotificationsFtsSync(searchQuery: String): List<NotificationEntity>

    @RawQuery(observedEntities = [NotificationEntity::class])
    abstract fun searchNotificationsRaw(query: SupportSQLiteQuery): Flow<List<NotificationEntity>>

    @Query("INSERT INTO notifications_fts(notifications_fts) VALUES('rebuild')")
    abstract suspend fun rebuildFtsIndex()

    @Query("UPDATE notifications SET isPinned = :isPinned WHERE key = :key")
    abstract suspend fun updatePinnedStatus(key: String, isPinned: Boolean)

    @Query("UPDATE notifications SET isPinned = :isPinned WHERE key IN (:keys)")
    abstract suspend fun updatePinnedStatusBatch(keys: List<String>, isPinned: Boolean)

    @Query("UPDATE notifications SET isRead = 1 WHERE key = :key")
    suspend fun markAsReadInternal(key: String)

    @Query("DELETE FROM notifications WHERE key = :key")
    suspend fun deleteByKey(key: String)

    suspend fun markAsRead(key: String, autoDeleteOnRead: Boolean = false) {
        if (autoDeleteOnRead) deleteByKey(key) else markAsReadInternal(key)
    }

    @Query("UPDATE notifications SET isRead = 1 WHERE key IN (:keys)")
    suspend fun markAsReadBatchInternal(keys: List<String>)

    @Query("DELETE FROM notifications WHERE key IN (:keys)")
    suspend fun deleteByKeys(keys: List<String>)

    suspend fun markAsReadBatch(keys: List<String>, autoDeleteOnRead: Boolean = false) {
        if (keys.isEmpty()) return
        if (autoDeleteOnRead) deleteByKeys(keys) else markAsReadBatchInternal(keys)
    }

    @Query("UPDATE notifications SET isRead = 1 WHERE isRead = 0")
    suspend fun markAllAsReadInternal()

    @Query("DELETE FROM notifications WHERE isRead = 0")
    suspend fun deleteUnread()

    suspend fun markAllAsRead(autoDeleteOnRead: Boolean = false) {
        if (autoDeleteOnRead) deleteUnread() else markAllAsReadInternal()
    }

    @Query("SELECT COUNT(*) FROM notifications")
    abstract suspend fun getNotificationCount(): Int

    @Query("SELECT COUNT(*) FROM notifications WHERE isDismissed = 1")
    abstract fun getDismissedNotificationCountFlow(): Flow<Int>

    @Query("SELECT COUNT(*) FROM notifications")
    abstract fun getTotalNotificationCountFlow(): Flow<Int>

    @Query("SELECT COUNT(*) FROM notifications WHERE isRead = 0 AND isDismissed = 0")
    abstract fun getUnreadCountFlow(): Flow<Int>
}
