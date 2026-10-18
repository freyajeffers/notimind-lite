package com.jeffers.notimindlite.data.local

import androidx.room.Insert
import androidx.room.OnConflictStrategy

interface NotificationStorageOperations {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAppInternal(app: AppEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAppsInternal(apps: List<AppEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertGroupInternal(group: NotificationGroupEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertGroupsInternal(groups: List<NotificationGroupEntity>): List<Long>
}
