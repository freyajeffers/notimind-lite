package com.jeffers.notimindlite.data.local

import androidx.room.Query

interface NotificationReadStateQueries : NotificationReadBatchQueries {
    @Query("UPDATE notifications SET isPinned = :isPinned WHERE key = :key")
    suspend fun updatePinnedStatus(key: String, isPinned: Boolean)

    @Query("UPDATE notifications SET isPinned = :isPinned WHERE key IN (:keys)")
    suspend fun updatePinnedStatusBatch(keys: List<String>, isPinned: Boolean)

    @Query("UPDATE notifications SET isRead = 1 WHERE key = :key")
    suspend fun markAsReadInternal(key: String)

    @Query("DELETE FROM notifications WHERE key = :key")
    suspend fun deleteByKey(key: String)

    suspend fun markAsRead(key: String, autoDeleteOnRead: Boolean = false) {
        if (autoDeleteOnRead) deleteByKey(key) else markAsReadInternal(key)
    }


    @Query("UPDATE notifications SET isRead = 1 WHERE isRead = 0")
    suspend fun markAllAsReadInternal()

    @Query("DELETE FROM notifications WHERE isRead = 0")
    suspend fun deleteUnread()

    suspend fun markAllAsRead(autoDeleteOnRead: Boolean = false) {
        if (autoDeleteOnRead) deleteUnread() else markAllAsReadInternal()
    }
}
