package com.jeffers.notimindlite.util

import com.jeffers.notimindlite.data.local.NotificationEntity

data class ScoredNotification(
    val entity: NotificationEntity,
    val hybridScore: Float,
    val vectorScore: Float,
    val textScore: Float
)

object HybridSearchEngine {

    private const val VECTOR_WEIGHT = 0.55f
    private const val TEXT_WEIGHT = 0.45f
    private const val MATCH_THRESHOLD = 0.15f

    /**
     * Performs combined Semantic Vector + Full-Text Search ranking on a collection of notifications.
     * Returns results sorted in descending order of best match hybrid score.
     */
    fun searchAndRank(
        notifications: List<NotificationEntity>,
        query: String
    ): List<NotificationEntity> {
        val trimmedQuery = query.trim()
        if (trimmedQuery.isEmpty()) return notifications

        val queryVector = VectorEmbeddingHelper.computeEmbedding(trimmedQuery)
        val queryTokens = trimmedQuery.lowercase().split("\\s+".toRegex()).filter { it.isNotBlank() }

        val scoredResults = notifications.mapNotNull { item ->
            // 1. Vector Semantic Embedding Cosine Similarity
            val itemText = buildString {
                append(item.appName).append(" ")
                append(item.title).append(" ")
                append(item.content).append(" ")
                if (!item.subText.isNullOrEmpty()) append(item.subText).append(" ")
                if (!item.bigText.isNullOrEmpty()) append(item.bigText).append(" ")
                if (!item.category.isNullOrEmpty()) append(item.category).append(" ")
                append(item.packageName)
            }
            val itemVector = VectorEmbeddingHelper.computeEmbedding(itemText)
            val vectorScore = VectorEmbeddingHelper.cosineSimilarity(queryVector, itemVector)

            // 2. Full-Text Search Keyword Match Score
            val lowerText = itemText.lowercase()
            var tokenHits = 0
            var exactPhraseBonus = 0f
            if (lowerText.contains(trimmedQuery.lowercase())) {
                exactPhraseBonus = 0.4f
            }

            for (token in queryTokens) {
                if (item.title.contains(token, ignoreCase = true)) {
                    tokenHits += 2 // Title match has higher weight
                } else if (item.appName.contains(token, ignoreCase = true)) {
                    tokenHits += 2 // App name match has higher weight
                } else if (lowerText.contains(token)) {
                    tokenHits += 1
                }
            }

            val maxPossibleHits = queryTokens.size * 2
            val textScore = if (maxPossibleHits > 0) {
                ((tokenHits.toFloat() / maxPossibleHits.toFloat()) * 0.6f + exactPhraseBonus).coerceIn(0f, 1f)
            } else {
                0f
            }

            // 3. Combined Hybrid Score (Reciprocal Fusion)
            val hybridScore = (vectorScore * VECTOR_WEIGHT) + (textScore * TEXT_WEIGHT)

            // 4. Threshold check or fallback matching
            val isSemanticClusterMatch = SemanticSearchHelper.matches(item, trimmedQuery)
            if (hybridScore >= MATCH_THRESHOLD || isSemanticClusterMatch) {
                ScoredNotification(
                    entity = item,
                    hybridScore = if (isSemanticClusterMatch && hybridScore < 0.3f) 0.3f + hybridScore else hybridScore,
                    vectorScore = vectorScore,
                    textScore = textScore
                )
            } else {
                null
            }
        }

        // Sort descending by highest hybrid score (best match first)
        return scoredResults
            .sortedByDescending { it.hybridScore }
            .map { it.entity }
    }
}
