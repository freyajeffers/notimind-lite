package com.jeffers.notimindlite.util

import com.jeffers.notimindlite.data.local.NotificationEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.*

/**
 * Prototype for an 'Intelligent Suggestion' engine.
 * Predicts which dismissed notifications the user may need based on 
 * temporal patterns (time-of-day) and historical search behavior.
 */
object IntelligentSuggestionEngine {

    data class SearchEvent(
        val query: String,
        val timestamp: Long,
        val matchedNotificationIds: List<Long>
    )

    // Mocked history for prototype purposes. 
    // In production, this would be persisted in a 'search_history' SQLite table.
    private val searchHistory = mutableListOf<SearchEvent>()

    /**
     * Records a search event to build the behavioral model.
     */
    fun recordSearch(query: String, ids: List<Long>) {
        searchHistory.add(SearchEvent(query, System.currentTimeMillis(), ids))
    }

    /**
     * Predicts the most relevant dismissed notifications for the current context.
     * 
     * @param dismissedNotifications The set of all dismissed notifications to consider.
     * @return A list of suggested notifications ranked by predicted relevance.
     */
    suspend fun predictSuggestions(dismissedNotifications: List<NotificationEntity>): List<NotificationEntity> = withContext(Dispatchers.Default) {
        if (dismissedNotifications.isEmpty()) return@withContext emptyList<NotificationEntity>()

        val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val timeWindow = 2 // +/- 2 hours
        
        // 1. Identify queries typically used around this time of day
        val temporalQueries = searchHistory.filter { event ->
            val eventHour = Calendar.getInstance().apply { timeInMillis = event.timestamp }.get(Calendar.HOUR_OF_DAY)
            Math.abs(eventHour - currentHour) <= timeWindow
        }.groupBy { it.query }
         .mapValues { it.value.size }
         .toList()
         .sortedByDescending { it.second }

        if (temporalQueries.isEmpty()) {
            // Fallback: Suggest based on overall most frequent search patterns regardless of time
            return@withContext suggestByGlobalPatterns(dismissedNotifications)
        }

        // 2. Score notifications based on temporal query matches
        val scores = mutableMapOf<Long, Double>()
        
        temporalQueries.forEachIndexed { index, (query, frequency) ->
            val weight = (1.0 / (index + 1)) * frequency
            
            // Find notifications that match this query (simplified keyword match for prototype)
            dismissedNotifications.forEach { entity ->
                if (entity.content.contains(query, ignoreCase = true) || entity.appName.contains(query, ignoreCase = true)) {
                    scores[entity.id] = scores.getOrDefault(entity.id, 0.0) + weight
                }
            }
        }

        // 3. Return notifications sorted by score
        return@withContext dismissedNotifications
            .filter { scores.containsKey(it.id) }
            .sortedByDescending { scores[it.id] }
            .take(10)
    }

    private fun suggestByGlobalPatterns(notifications: List<NotificationEntity>): List<NotificationEntity> {
        val globalQueries = searchHistory.groupBy { it.query }
            .mapValues { it.value.size }
            .toList()
            .sortedByDescending { it.second }

        val scores = mutableMapOf<Long, Double>()
        globalQueries.forEachIndexed { index, (query, frequency) ->
            val weight = (1.0 / (index + 1)) * frequency
            notifications.forEach { entity ->
                if (entity.content.contains(query, ignoreCase = true) || entity.appName.contains(query, ignoreCase = true)) {
                    scores[entity.id] = scores.getOrDefault(entity.id, 0.0) + weight
                }
            }
        }
        
        return notifications
            .filter { scores.containsKey(it.id) }
            .sortedByDescending { scores[it.id] }
            .take(10)
    }
}
