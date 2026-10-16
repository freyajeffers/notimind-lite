package com.jeffers.notimindlite.data.local

import androidx.room.Query
import androidx.room.RawQuery
import androidx.sqlite.db.SupportSQLiteQuery
import kotlinx.coroutines.flow.Flow

interface NotificationSearchQueries {
    @Query("""
        SELECT notifications.* FROM notifications
        JOIN notifications_fts ON notifications.rowid = notifications_fts.docid
        WHERE notifications_fts MATCH :searchQuery
        ORDER BY postTime DESC
    """)
    fun searchNotificationsFts(searchQuery: String): Flow<List<NotificationEntity>>

    @Query("""
        SELECT notifications.* FROM notifications
        JOIN notifications_fts ON notifications.rowid = notifications_fts.docid
        WHERE notifications_fts MATCH :searchQuery
        ORDER BY postTime DESC
    """)
    suspend fun searchNotificationsFtsSync(searchQuery: String): List<NotificationEntity>

    @RawQuery(observedEntities = [NotificationEntity::class])
    fun searchNotificationsRaw(query: SupportSQLiteQuery): Flow<List<NotificationEntity>>

    @Query("INSERT INTO notifications_fts(notifications_fts) VALUES('rebuild')")
    suspend fun rebuildFtsIndex()
}
