package com.jeffers.notimindlite.util

import com.jeffers.notimindlite.data.local.NotificationEntity

object ReciprocalRankFusion {
    private const val DEFAULT_K = 60.0

    private data class RankAccumulator(
        val notification: NotificationEntity,
        var rrfScore: Double = 0.0,
        var ftsRank: Int? = null,
        var semanticRank: Int? = null,
        var semanticSimilarityScore: Float? = null
    )

    /**
     * Merges FTS results and semantic vector results using Reciprocal Rank Fusion.
     * RRF_Score(d) = sum(w_r / (k + rank_r(d)))
     */
    fun merge(
        ftsResults: List<NotificationEntity>,
        semanticResults: List<SemanticSearchResult>,
        k: Double = DEFAULT_K,
        ftsWeight: Double = 1.0,
        semanticWeight: Double = 1.0
    ): List<HybridSearchResult> {
        val expectedSize = (ftsResults.size + semanticResults.size).coerceAtLeast(16)
        val accumulatorMap = HashMap<Long, RankAccumulator>(expectedSize)

        // 1. Accumulate FTS Rank Contributions
        ftsResults.forEachIndexed { index, entity ->
            val rank = index + 1
            val contribution = ftsWeight / (k + rank)
            val acc = accumulatorMap.getOrPut(entity.id) { RankAccumulator(entity) }
            acc.rrfScore += contribution
            acc.ftsRank = rank
        }

        // 2. Accumulate Vector Semantic Rank Contributions
        semanticResults.forEachIndexed { index, result ->
            val rank = index + 1
            val contribution = semanticWeight / (k + rank)
            val acc = accumulatorMap.getOrPut(result.notification.id) { RankAccumulator(result.notification) }
            acc.rrfScore += contribution
            acc.semanticRank = rank
            acc.semanticSimilarityScore = result.similarityScore
        }

        // 3. Sort by Unified RRF Score Descending
        return accumulatorMap.values
            .sortedByDescending { it.rrfScore }
            .map { acc ->
                HybridSearchResult(
                    notification = acc.notification,
                    rrfScore = acc.rrfScore,
                    ftsRank = acc.ftsRank,
                    semanticRank = acc.semanticRank,
                    semanticSimilarityScore = acc.semanticSimilarityScore
                )
            }
    }
}
