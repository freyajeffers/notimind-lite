package com.jeffers.notimindlite.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "notifications",
    indices = [
        Index(value = ["key"], unique = true),
        Index(value = ["isDismissed", "postTime"]),
        Index(value = ["isDismissed", "dismissTime"]),
        Index(value = ["packageName", "isDismissed"]),
        Index(value = ["isPinned", "postTime"]),
        Index(value = ["isRead"])
    ]
)
data class NotificationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val key: String,
    val packageName: String,
    val appName: String,
    val appIconUri: String? = null,
    val title: String,
    val content: String,
    val postTime: Long = System.currentTimeMillis(),
    val lastUpdatedTime: Long = System.currentTimeMillis(),
    val updateCount: Int = 1,
    val isDismissed: Boolean = false,
    val isPersistent: Boolean = false,
    val isRead: Boolean = false,
    val isGroupSummary: Boolean = false,
    val category: String? = null,
    val channelId: String? = null,
    val subText: String? = null,
    val bigText: String? = null,
    val inboxLinesJson: String? = null,
    val priority: Int = 0,
    val groupKey: String? = null,
    val isOngoing: Boolean = false,
    val isClearable: Boolean = true,
    val actionsCount: Int = 0,
    val dismissReason: Int? = null,
    val dismissTime: Long? = null,
    val intentUri: String? = null,
    val isPinned: Boolean = false,
    val actionLabels: String? = null,
    val smallIconRes: Int = 0,
    val syncStatus: SyncStatus = SyncStatus.PENDING_UPLOAD,
    val lastSyncedAt: Long = 0,
    val embedding: FloatArray? = null
)
