package com.jeffers.notimindlite.data.local

import androidx.room.Embedded
import androidx.room.Relation

/**
 * Composite model representing a notification entity alongside its parent
 * notification group and parent application record.
 */
data class NotificationWithGroupAndApp(
    @Embedded val notification: NotificationEntity,
    @Relation(
        parentColumn = "groupKey",
        entityColumn = "groupKey"
    )
    val group: NotificationGroupEntity?,
    @Relation(
        parentColumn = "packageName",
        entityColumn = "packageName"
    )
    val app: AppEntity?
)
