package com.jeffers.notimindlite.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
abstract class NotificationDao :
    NotificationSyncOperations,
    NotificationStorageOperations,
    NotificationMaintenanceOperations,
    NotificationInsertOperations,
    NotificationEntityQueries,
    NotificationReadQueries,
    NotificationStateQueries,
    NotificationSearchQueries,
    NotificationReadStateQueries,
    NotificationCountQueries {

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



    @Transaction
    open suspend fun updateEmbeddingsBatch(items: List<Pair<Long, ByteArray>>) {
        for ((id, embedding) in items) {
            updateEmbedding(id, embedding)
        }
    }


    // moved maintenance queries to NotificationMaintenanceOperations
}
