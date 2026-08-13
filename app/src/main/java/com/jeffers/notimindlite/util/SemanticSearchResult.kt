package com.jeffers.notimindlite.util

import com.jeffers.notimindlite.data.local.NotificationEntity

data class SemanticSearchResult(
    val notification: NotificationEntity,
    val similarityScore: Float
)
