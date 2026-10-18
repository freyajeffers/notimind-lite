package com.jeffers.notimindlite.data.local

import androidx.room.Query

interface NotificationReadBatchQueries {
    @Query("UPDATE notifications SET isRead = 1 WHERE key IN (:keys)")
    suspend fun markAsReadBatchInternal(keys: List<String>)

    @Query("DELETE FROM notifications WHERE key IN (:keys)")
    suspend fun deleteByKeys(keys: List<String>)

    suspend fun markAsReadBatch(keys: List<String>, autoDeleteOnRead: Boolean = false) {
        if (keys.isEmpty()) return
        if (autoDeleteOnRead) deleteByKeys(keys) else markAsReadBatchInternal(keys)
    }
}
