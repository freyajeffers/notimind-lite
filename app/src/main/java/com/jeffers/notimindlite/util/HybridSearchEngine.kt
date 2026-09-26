package com.jeffers.notimindlite.util

import android.content.Context
import com.jeffers.notimindlite.data.local.NotificationEntity
import kotlinx.coroutines.flow.Flow

/**
 * Compatibility facade for callers that historically imported the utility package.
 * The implementation lives in the domain search package.
 */
object HybridSearchEngine {
    fun search(context: Context, query: String): Flow<List<NotificationEntity>> =
        com.jeffers.notimindlite.domain.search.HybridSearchEngine.search(context, query)

    fun searchAndRankBlocking(
        notifications: List<NotificationEntity>,
        query: String
    ): List<NotificationEntity> =
        com.jeffers.notimindlite.domain.search.HybridSearchEngine.searchAndRankBlocking(notifications, query)

    suspend fun searchAndRank(
        notifications: List<NotificationEntity>,
        query: String
    ): List<NotificationEntity> =
        com.jeffers.notimindlite.domain.search.HybridSearchEngine.searchAndRank(notifications, query)

    suspend fun searchAndRank(
        context: Context,
        query: String
    ): List<NotificationEntity> =
        com.jeffers.notimindlite.domain.search.HybridSearchEngine.searchAndRank(context, query)
}
