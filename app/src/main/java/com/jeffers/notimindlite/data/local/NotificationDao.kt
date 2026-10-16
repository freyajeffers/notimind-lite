package com.jeffers.notimindlite.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RawQuery
import androidx.sqlite.db.SupportSQLiteQuery
import kotlinx.coroutines.flow.Flow

import androidx.room.Transaction

private const val DISMISS_REASON_LIST = "1, 2, 3, 12, 19, 23"
private const val RECENT_DISMISSED_QUERY =
    "SELECT * FROM notifications WHERE isDismissed = 1 AND dismissReason IN ($DISMISS_REASON_LIST) " +
        "ORDER BY COALESCE(dismissTime, postTime) DESC"
private const val LOST_NOTIFICATIONS_QUERY =
    "SELECT * FROM notifications WHERE isDismissed = 1 AND " +
        "(dismissReason NOT IN ($DISMISS_REASON_LIST) OR dismissReason IS NULL) " +
        "ORDER BY COALESCE(dismissTime, postTime) DESC"

@Dao
abstract class NotificationDao : NotificationSyncOperations, NotificationStorageOperations {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertNotificationDirect(entity: NotificationEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertNotificationsDirect(entities: List<NotificationEntity>): List<Long>

    @Transaction
    open suspend fun insert(entity: NotificationEntity): Long {
        val effectiveGroupKey = entity.groupKey?.ifBlank { null } ?: entity.packageName
        val normalizedEntity = if (entity.groupKey == null) entity.copy(groupKey = effectiveGroupKey) else entity

        insertAppInternal(
            AppEntity(
                packageName = normalizedEntity.packageName,
                appName = normalizedEntity.appName.ifBlank { normalizedEntity.packageName.ifBlank { "Unknown App" } },
                firstSeenTime = normalizedEntity.postTime,
                lastSeenTime = normalizedEntity.postTime,
                appIconUri = normalizedEntity.appIconUri
            )
        )
        insertGroupInternal(
            NotificationGroupEntity(
                groupKey = effectiveGroupKey,
                packageName = normalizedEntity.packageName,
                appName = normalizedEntity.appName.ifBlank { normalizedEntity.packageName.ifBlank { "Unknown App" } },
                appIconUri = normalizedEntity.appIconUri,
                latestPostTime = normalizedEntity.postTime,
                notificationCount = 1,
                activeCount = if (normalizedEntity.isDismissed) 0 else 1,
                isPinned = normalizedEntity.isPinned,
                isDismissed = normalizedEntity.isDismissed,
                summaryTitle = normalizedEntity.title,
                summaryText = normalizedEntity.content,
                lastUpdatedTime = normalizedEntity.lastUpdatedTime
            )
        )
        return insertNotificationDirect(normalizedEntity)
    }

    @Transaction
    open suspend fun insertNotification(notification: NotificationEntity): Long {
        return insert(notification)
    }

    @Transaction
    open suspend fun insertNotifications(notifications: List<NotificationEntity>): List<Long> {
        if (notifications.isEmpty()) return emptyList()
        val normalizedNotifications = notifications.map { entity ->
            val effectiveGroupKey = entity.groupKey?.ifBlank { null } ?: entity.packageName
            if (entity.groupKey == null) entity.copy(groupKey = effectiveGroupKey) else entity
        }
        val apps = normalizedNotifications.map { entity ->
            AppEntity(
                packageName = entity.packageName,
                appName = entity.appName.ifBlank { entity.packageName.ifBlank { "Unknown App" } },
                firstSeenTime = entity.postTime,
                lastSeenTime = entity.postTime,
                appIconUri = entity.appIconUri
            )
        }
        insertAppsInternal(apps)

        val groups = normalizedNotifications.groupBy { it.groupKey ?: it.packageName }.map { (groupKey, items) ->
            val sorted = items.sortedByDescending { it.postTime }
            val first = sorted.first()
            NotificationGroupEntity(
                groupKey = groupKey,
                packageName = first.packageName,
                appName = first.appName.ifBlank { first.packageName.ifBlank { "Unknown App" } },
                appIconUri = first.appIconUri,
                latestPostTime = sorted.maxOf { it.postTime },
                notificationCount = items.size,
                activeCount = items.count { !it.isDismissed },
                isPinned = items.any { it.isPinned },
                isDismissed = items.all { it.isDismissed },
                summaryTitle = first.title,
                summaryText = first.content,
                lastUpdatedTime = sorted.maxOf { it.lastUpdatedTime }
            )
        }
        insertGroupsInternal(groups)
        return insertNotificationsDirect(normalizedNotifications)
    }


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

    // SQLite Full-Text Search (FTS4)
    //
    // NOTE [F-B, 2026-09-02 audit]: These queries join on `notifications.rowid`, but
    // `NotificationEntity` declares `@PrimaryKey(autoGenerate = true) val id: Long`,
    // which makes SQLite treat `id` AS the implicit rowid (INTEGER PRIMARY KEY alias).
    // The join works today only because of that alias. If the PK is ever renamed or
    // changed to a non-INTEGER type, these joins will silently return empty result
    // sets. Prefer `notifications.id = notifications_fts.docid` for clarity; the
    // `docid` column in a contentless FTS4 table is the source rowid regardless.
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
    protected abstract suspend fun markAsReadInternal(key: String)

    @Query("DELETE FROM notifications WHERE key = :key")
    protected abstract suspend fun deleteByKey(key: String)

    open suspend fun markAsRead(key: String, autoDeleteOnRead: Boolean = false) {
        if (autoDeleteOnRead) deleteByKey(key) else markAsReadInternal(key)
    }

    @Query("UPDATE notifications SET isRead = 1 WHERE key IN (:keys)")
    protected abstract suspend fun markAsReadBatchInternal(keys: List<String>)

    @Query("DELETE FROM notifications WHERE key IN (:keys)")
    protected abstract suspend fun deleteByKeys(keys: List<String>)

    open suspend fun markAsReadBatch(keys: List<String>, autoDeleteOnRead: Boolean = false) {
        if (keys.isEmpty()) return
        if (autoDeleteOnRead) deleteByKeys(keys) else markAsReadBatchInternal(keys)
    }

    @Query("UPDATE notifications SET isRead = 1 WHERE isRead = 0")
    protected abstract suspend fun markAllAsReadInternal()

    @Query("DELETE FROM notifications WHERE isRead = 0")
    protected abstract suspend fun deleteUnread()

    open suspend fun markAllAsRead(autoDeleteOnRead: Boolean = false) {
        if (autoDeleteOnRead) deleteUnread() else markAllAsReadInternal()
    }

    @Query("SELECT COUNT(*) FROM notifications WHERE isRead = 0 AND isDismissed = 0")
    abstract fun getUnreadCountFlow(): Flow<Int>

    @Query("""
        UPDATE notifications 
        SET isDismissed = 1, dismissTime = :dismissTime 
        WHERE key = :key 
           OR (
               packageName = :packageName 
               AND title = :title 
               AND content = :content 
               AND title != '' 
               AND content != ''
           )
    """)
    abstract suspend fun markDismissedByMatching(
        key: String,
        packageName: String,
        title: String,
        content: String,
        dismissTime: Long = System.currentTimeMillis()
    )

    @Query("""
        UPDATE notifications 
        SET isDismissed = 1, dismissReason = :reason,
            dismissTime = CAST(strftime('%s', 'now') AS INTEGER) * 1000
        WHERE key = :key 
           OR (
               packageName = :packageName 
               AND title = :title 
               AND content = :content 
               AND title != '' 
               AND content != ''
           )
    """)
    abstract suspend fun markDismissedWithReasonByMatching(
        key: String,
        packageName: String,
        title: String,
        content: String,
        reason: Int
    )

    @Query("UPDATE notifications SET isDismissed = 1, dismissTime = :dismissTime WHERE key = :key")
    abstract suspend fun markDismissed(key: String, dismissTime: Long = System.currentTimeMillis())

    @Query("UPDATE notifications SET isDismissed = 1, dismissTime = :dismissTime WHERE key IN (:keys)")
    abstract suspend fun markDismissedBatch(keys: List<String>, dismissTime: Long = System.currentTimeMillis())

    @Query(
        "UPDATE notifications SET isDismissed = 1, dismissReason = :reason, " +
            "dismissTime = :dismissTime WHERE key = :key"
    )
    abstract suspend fun markDismissedWithReason(
        key: String,
        reason: Int,
        dismissTime: Long = System.currentTimeMillis()
    )

    @Query("""
        UPDATE notifications
        SET isDismissed = 1, dismissReason = :reason, dismissTime = :dismissTime
        WHERE key IN (:keys)
    """)
    abstract suspend fun markDismissedWithReasonBatch(
        keys: List<String>,
        reason: Int,
        dismissTime: Long = System.currentTimeMillis()
    )

    @Query("SELECT COUNT(*) FROM notifications")
    abstract suspend fun getNotificationCount(): Int

    @Query("SELECT COUNT(*) FROM notifications WHERE isDismissed = 1")
    abstract fun getDismissedNotificationCountFlow(): Flow<Int>

    @Query("SELECT COUNT(*) FROM notifications")
    abstract fun getTotalNotificationCountFlow(): Flow<Int>

    @Query("DELETE FROM notifications WHERE isDismissed = 1 AND postTime < :cutoffTimeMs AND isPinned = 0")
    abstract suspend fun pruneOldLogs(cutoffTimeMs: Long)

    @Transaction
    open suspend fun updateEmbeddingsBatch(items: List<Pair<Long, ByteArray>>) {
        for ((id, embedding) in items) {
            updateEmbedding(id, embedding)
        }
    }


    @Query("DELETE FROM notification_groups")
    abstract suspend fun clearGroups()

    @Query("DELETE FROM notifications")
    abstract suspend fun clearAll()
}
