package com.jeffers.notimindlite.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "apps")
data class AppEntity(
    @PrimaryKey val packageName: String,
    val appName: String,
    val appIconUri: String? = null,
    val firstSeenTime: Long = System.currentTimeMillis(),
    val lastSeenTime: Long = System.currentTimeMillis()
)
