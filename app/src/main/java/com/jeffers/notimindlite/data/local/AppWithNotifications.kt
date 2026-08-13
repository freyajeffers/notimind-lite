package com.jeffers.notimindlite.data.local

import androidx.room.Embedded
import androidx.room.Relation

data class AppWithNotifications(
    @Embedded val app: AppEntity,
    @Relation(
        parentColumn = "packageName",
        entityColumn = "packageName"
    )
    val notifications: List<NotificationEntity>
)
