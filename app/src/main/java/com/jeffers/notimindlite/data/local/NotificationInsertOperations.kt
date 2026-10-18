package com.jeffers.notimindlite.data.local

import androidx.room.Insert
import androidx.room.OnConflictStrategy

interface NotificationInsertOperations {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotificationDirect(entity: NotificationEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotificationsDirect(entities: List<NotificationEntity>): List<Long>
}
