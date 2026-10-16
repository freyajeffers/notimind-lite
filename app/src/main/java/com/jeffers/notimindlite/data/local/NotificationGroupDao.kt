package com.jeffers.notimindlite.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
abstract class NotificationGroupDao {
    @Transaction
    @Query("SELECT * FROM notification_groups ORDER BY isPinned DESC, latestPostTime DESC")
    abstract fun getAllGroupsWithChildrenFlow(): Flow<List<NotificationGroupWithChildren>>

    @Transaction
    @Query("SELECT * FROM notification_groups WHERE isDismissed = 0 ORDER BY isPinned DESC, latestPostTime DESC")
    abstract fun getActiveGroupsWithChildrenFlow(): Flow<List<NotificationGroupWithChildren>>

    @Transaction
    @Query("SELECT * FROM notification_groups WHERE isDismissed = 1 ORDER BY isPinned DESC, latestPostTime DESC")
    abstract fun getDismissedGroupsWithChildrenFlow(): Flow<List<NotificationGroupWithChildren>>

    @Transaction
    @Query("SELECT * FROM apps WHERE packageName = :packageName LIMIT 1")
    abstract fun getAppWithGroupsAndNotifications(packageName: String): Flow<AppWithGroupsAndNotifications?>

    @Transaction
    @Query("SELECT * FROM notifications WHERE key = :key LIMIT 1")
    abstract fun getNotificationWithGroupAndApp(key: String): Flow<NotificationWithGroupAndApp?>

    @Query("SELECT * FROM notification_groups ORDER BY isPinned DESC, latestPostTime DESC")
    abstract fun getAllGroupsFlow(): Flow<List<NotificationGroupEntity>>

    @Query("SELECT * FROM notification_groups WHERE isDismissed = 0 ORDER BY isPinned DESC, latestPostTime DESC")
    abstract fun getActiveGroupsFlow(): Flow<List<NotificationGroupEntity>>

    @Query("SELECT * FROM notification_groups WHERE isDismissed = 1 ORDER BY isPinned DESC, latestPostTime DESC")
    abstract fun getDismissedGroupsFlow(): Flow<List<NotificationGroupEntity>>

}
