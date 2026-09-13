package com.jeffers.notimindlite.data.local

import androidx.room.Embedded
import androidx.room.Relation

/**
 * Composite relation containing a notification group and all of its child notifications.
 */
data class NotificationGroupWithChildren(
    @Embedded val group: NotificationGroupEntity,
    @Relation(
        parentColumn = "groupKey",
        entityColumn = "groupKey"
    )
    val notifications: List<NotificationEntity>
)
