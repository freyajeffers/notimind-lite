package com.jeffers.notimindlite.data.local

import androidx.room.Dao
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
abstract class NotificationGroupDetailDao {
    @Query("SELECT * FROM notification_groups WHERE groupKey = :groupKey LIMIT 1")
    abstract suspend fun getGroupByKey(groupKey: String): NotificationGroupEntity?

    @Query("SELECT * FROM notifications WHERE COALESCE(groupKey, packageName) = :groupKey ORDER BY postTime DESC")
    abstract fun getNotificationsForGroupFlow(groupKey: String): Flow<List<NotificationEntity>>

    @Query("SELECT * FROM notifications WHERE COALESCE(groupKey, packageName) = :groupKey ORDER BY postTime DESC")
    abstract suspend fun getNotificationsForGroup(groupKey: String): List<NotificationEntity>
}