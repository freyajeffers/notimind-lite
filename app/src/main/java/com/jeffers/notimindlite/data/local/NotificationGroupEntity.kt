package com.jeffers.notimindlite.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Entity representing an aggregated group of notifications.
 * Automatically maintained via SQLite triggers and DAO transactions.
 */
@Entity(
    tableName = "notification_groups",
    foreignKeys = [
        ForeignKey(
            entity = AppEntity::class,
            parentColumns = ["packageName"],
            childColumns = ["packageName"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["latestPostTime"]),
        Index(value = ["packageName"]),
        Index(value = ["isDismissed", "latestPostTime"]),
        Index(value = ["isPinned", "latestPostTime"])
    ]
)
data class NotificationGroupEntity(
    @PrimaryKey val groupKey: String,
    val packageName: String,
    val appName: String,
    val appIconUri: String? = null,
    val latestPostTime: Long = System.currentTimeMillis(),
    val notificationCount: Int = 1,
    val activeCount: Int = 1,
    val isPinned: Boolean = false,
    val isDismissed: Boolean = false,
    val summaryTitle: String? = null,
    val summaryText: String? = null,
    val lastUpdatedTime: Long = System.currentTimeMillis()
)
