package com.jeffers.notimindlite.data.local

import androidx.room.Query

interface NotificationSyncOperations {
    @Query("SELECT * FROM notifications WHERE syncStatus != 'SYNCED'")
    suspend fun getUnsyncedNotifications(): List<NotificationEntity>

    @Query("UPDATE notifications SET syncStatus = :status, lastSyncedAt = :syncedAt WHERE key = :key")
    suspend fun updateSyncStatus(
        key: String,
        status: SyncStatus,
        syncedAt: Long = System.currentTimeMillis()
    )

    @Query("UPDATE notifications SET syncStatus = :status, lastSyncedAt = :syncedAt WHERE key IN (:keys)")
    suspend fun updateSyncStatusBatch(
        keys: List<String>,
        status: SyncStatus,
        syncedAt: Long = System.currentTimeMillis()
    )

    @Query("UPDATE notifications SET syncStatus = 'PENDING_DELETE' WHERE key = :key")
    suspend fun markPendingDelete(key: String)

    @Query("DELETE FROM notifications WHERE syncStatus = 'PENDING_DELETE'")
    suspend fun purgePendingDeletes()
}
