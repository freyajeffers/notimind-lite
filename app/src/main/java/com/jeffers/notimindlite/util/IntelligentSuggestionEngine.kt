package com.jeffers.notimindlite.util

import com.jeffers.notimindlite.data.local.NotificationEntity

/**
 * Compatibility facade for callers that historically imported the utility package.
 * The implementation lives in the domain suggestion package.
 */
object IntelligentSuggestionEngine {
    data class SearchEvent(
        val query: String,
        val timestamp: Long,
        val matchedNotificationIds: List<Long>
    )

    fun recordSearch(query: String, ids: List<Long>) =
        com.jeffers.notimindlite.domain.suggestion.IntelligentSuggestionEngine
            .recordSearch(query, ids)

    internal fun clearHistoryForTesting() =
        com.jeffers.notimindlite.domain.suggestion.IntelligentSuggestionEngine
            .clearHistoryForTesting()

    suspend fun predictSuggestions(
        dismissedNotifications: List<NotificationEntity>
    ): List<NotificationEntity> = com.jeffers.notimindlite.domain.suggestion
        .IntelligentSuggestionEngine
        .predictSuggestions(dismissedNotifications)
}
