package com.jeffers.notimindlite.util

import com.jeffers.notimindlite.data.local.NotificationEntity

data class HybridSearchResult(
    val notification: NotificationEntity,
    val rrfScore: Double,
    val ftsRank: Int?,
    val semanticRank: Int?,
    val semanticSimilarityScore: Float?
)
