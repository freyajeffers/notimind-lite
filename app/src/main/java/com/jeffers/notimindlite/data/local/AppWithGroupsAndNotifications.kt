package com.jeffers.notimindlite.data.local

import androidx.room.Embedded
import androidx.room.Relation

/**
 * Composite model representing an application entity along with its associated
 * notification groups and individual child notifications.
 */
data class AppWithGroupsAndNotifications(
    @Embedded val app: AppEntity,
    @Relation(
        parentColumn = "packageName",
        entityColumn = "packageName"
    )
    val groups: List<NotificationGroupEntity>,
    @Relation(
        parentColumn = "packageName",
        entityColumn = "packageName"
    )
    val notifications: List<NotificationEntity>
)
