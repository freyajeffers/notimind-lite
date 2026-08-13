package com.jeffers.notimindlite.data.local

import androidx.room.Entity
import androidx.room.Fts4

@Fts4(contentEntity = NotificationEntity::class)
@Entity(tableName = "notifications_fts")
data class NotificationFtsEntity(
    val title: String,
    val content: String,
    val appName: String,
    val packageName: String
)
