package com.jeffers.notimindlite.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "notifications",
    foreignKeys = [
        ForeignKey(
            entity = AppEntity::class,
            parentColumns = ["packageName"],
            childColumns = ["packageName"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = NotificationGroupEntity::class,
            parentColumns = ["groupKey"],
            childColumns = ["groupKey"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["key"], unique = true),
        Index(value = ["packageName"]),
        Index(value = ["isDismissed", "postTime"]),
        Index(value = ["isDismissed", "dismissTime"]),
        Index(value = ["isDismissed", "isOngoing", "postTime"]),
        Index(value = ["isDismissed", "dismissReason", "dismissTime"]),
        Index(value = ["packageName", "isDismissed", "postTime"]),
        Index(value = ["isPinned", "postTime"]),
        Index(value = ["isRead", "isDismissed"]),
        Index(value = ["groupKey", "postTime"]),
        Index(value = ["syncStatus"])
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
    @ColumnInfo(defaultValue = "0") val lastUpdatedTime: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "1") val updateCount: Int = 1,
    val isDismissed: Boolean = false,
    val isPersistent: Boolean = false,
    @ColumnInfo(defaultValue = "0") val isRead: Boolean = false,
    @ColumnInfo(defaultValue = "0") val isGroupSummary: Boolean = false,
    val category: String? = null,
    val channelId: String? = null,
    val subText: String? = null,
    val bigText: String? = null,
    val inboxLinesJson: String? = null,
    val priority: Int = 0,
    val groupKey: String? = null,
    @ColumnInfo(defaultValue = "0") val isOngoing: Boolean = false,
    @ColumnInfo(defaultValue = "1") val isClearable: Boolean = true,
    @ColumnInfo(defaultValue = "0") val actionsCount: Int = 0,
    val dismissReason: Int? = null,
    val dismissTime: Long? = null,
    val intentUri: String? = null,
    @ColumnInfo(defaultValue = "0") val isPinned: Boolean = false,
    val actionLabels: String? = null,
    @ColumnInfo(defaultValue = "0") val smallIconRes: Int = 0,
    /** Local-only Android presentation metadata; excluded from cloud sync payloads. */
    val restoreMetadataJson: String? = null,
    @ColumnInfo(defaultValue = "'PENDING_UPLOAD'") val syncStatus: SyncStatus = SyncStatus.PENDING_UPLOAD,
    @ColumnInfo(defaultValue = "0") val lastSyncedAt: Long = 0,
    val embedding: FloatArray? = null
)

data class NotificationRestoreMetadataRow(
    val key: String,
    val restoreMetadataJson: String?
)
