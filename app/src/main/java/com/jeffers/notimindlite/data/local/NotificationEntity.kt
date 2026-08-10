package com.jeffers.notimindlite.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "notifications")
data class NotificationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val key: String,
    val packageName: String,
    val appName: String,
    val title: String,
    val content: String,
    val postTime: Long = System.currentTimeMillis(),
    val isDismissed: Boolean = false,
    val isPersistent: Boolean = false,
    val category: String? = null,
    val channelId: String? = null,
    val subText: String? = null,
    val bigText: String? = null,
    val priority: Int = 0,
    val groupKey: String? = null,
    val isOngoing: Boolean = false,
    val isClearable: Boolean = true,
    val actionsCount: Int = 0,
    val dismissReason: Int? = null,
    val dismissTime: Long? = null,
    val intentUri: String? = null,
    val isPinned: Boolean = false,
    val actionLabels: String? = null
)
