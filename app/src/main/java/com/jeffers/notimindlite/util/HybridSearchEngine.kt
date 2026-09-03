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
     * Synchronous overload of searchAndRank(notifications, query), intended
     * for callers that need an in-memory hit list on the current thread (e.g.
     * unit tests, debug utilities). Production code paths should use the
     * `suspend fun searchAndRank` overload via a coroutine scope instead, to
     * avoid blocking the caller thread.
     *
     * Implementation note: re-implements the same scoring logic as the
     * suspend overload, but without the `withContext(Dispatchers.IO)`
     * dispatcher hop, so it can run inline on the calling thread.
     */
    fun searchAndRankBlocking(
        notifications: List<NotificationEntity>,
        query: String
    ): List<NotificationEntity> {
        val trimmedQuery = query.trim()
        if (trimmedQuery.isEmpty()) return notifications

        // 1. Keyword Pass (matches the suspend overload)
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

        // 3. Fusion (RRF) — reuse the canonical merger
        val fusedResults = ReciprocalRankFusion.merge(
            ftsResults = keywordResults,
            semanticResults = semanticResults,
            k = RRF_K
        )

        return fusedResults.map { it.notification }
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

        // 1. FTS Pass (Keyword Search) - High Precision, Fast
        val ftsResults = dao.searchNotificationsFtsSync(trimmedQuery)

        // 2. Vector Pass (Semantic Search)
        val queryVector = VectorEmbeddingHelper.computeEmbedding(trimmedQuery)
        
        // OPTIMIZATION: Limit vector scoring to prevent OOM and latency.
        // We score the union of FTS results and the most recent notifications.
        val recentNotifications = dao.getRecentNotificationsList(1000)
        val candidateSet = (ftsResults + recentNotifications).distinctBy { it.id }
        
        val semanticResults = candidateSet.mapNotNull { entity ->
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
