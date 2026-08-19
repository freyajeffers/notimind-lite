package com.jeffers.notimindlite.util

import android.content.Context
import android.util.Log
import com.jeffers.notimindlite.data.local.NotificationEntity
import com.jeffers.notimindlite.data.local.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext

object HybridSearchEngine {

    private const val RRF_K = 60.0

    /**
     * Performs Hybrid Search by combining SQLite FTS and Semantic Vector Space Projection.
     * Results are merged using Reciprocal Rank Fusion (RRF).
     */
    fun search(
        context: Context,
        query: String
    ): Flow<List<NotificationEntity>> = flow {
        emit(searchAndRank(context, query))
    }

    /**
     * Search a provided list of notifications.
     * Used when the list is already filtered by other criteria (e.g., app filter).
     */
    suspend fun searchAndRank(
        notifications: List<NotificationEntity>,
        query: String
    ): List<NotificationEntity> = withContext(Dispatchers.IO) {
        val trimmedQuery = query.trim()
        if (trimmedQuery.isEmpty()) return@withContext notifications

        // 1. Keyword Pass (Manual filter for provided list)
        val keywordResults = notifications.filter {
            it.title.contains(trimmedQuery, ignoreCase = true) ||
            it.content.contains(trimmedQuery, ignoreCase = true) ||
            it.appName.contains(trimmedQuery, ignoreCase = true) ||
            it.packageName.contains(trimmedQuery, ignoreCase = true)
        }

        // 2. Vector Pass (Semantic Search)
        val queryVector = VectorEmbeddingHelper.computeEmbedding(trimmedQuery)
        val semanticResults = notifications.mapNotNull { entity ->
            val entityVector = entity.embedding ?: return@mapNotNull null
            val similarity = VectorEmbeddingHelper.cosineSimilarity(queryVector, entityVector)
            SemanticSearchResult(entity, similarity)
        }.sortedByDescending { it.similarityScore }

        // 3. Fusion (RRF)
        val fusedResults = ReciprocalRankFusion.merge(
            ftsResults = keywordResults,
            semanticResults = semanticResults,
            k = RRF_K
        )

        fusedResults.map { it.notification }
    }

    /**
     * Search the entire database using FTS and Vector search.
     */
    suspend fun searchAndRank(
        context: Context,
        query: String
    ): List<NotificationEntity> = withContext(Dispatchers.IO) {
        val trimmedQuery = query.trim()
        if (trimmedQuery.isEmpty()) return@withContext emptyList<NotificationEntity>()

        val db = AppDatabase.getDatabase(context)
        val dao = db.notificationDao()

        // 1. FTS Pass (Keyword Search)
        val ftsResults = dao.searchNotificationsFtsSync(trimmedQuery)

        // 2. Vector Pass (Semantic Search)
        val queryVector = VectorEmbeddingHelper.computeEmbedding(trimmedQuery)
        val allNotifications = dao.getAllNotificationsList()
        
        val semanticResults = allNotifications.mapNotNull { entity ->
            val entityVector = entity.embedding ?: return@mapNotNull null
            val similarity = VectorEmbeddingHelper.cosineSimilarity(queryVector, entityVector)
            SemanticSearchResult(entity, similarity)
        }.sortedByDescending { it.similarityScore }

        // 3. Fusion (RRF)
        val fusedResults = ReciprocalRankFusion.merge(
            ftsResults = ftsResults,
            semanticResults = semanticResults,
            k = RRF_K
        )

        fusedResults.map { it.notification }
    }
}
