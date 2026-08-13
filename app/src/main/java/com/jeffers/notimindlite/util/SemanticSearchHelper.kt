package com.jeffers.notimindlite.util

import com.jeffers.notimindlite.data.local.NotificationEntity

object SemanticSearchHelper {

    /**
     * Performs semantic keyword matching on a NotificationEntity against dynamic clusters.
     */
    fun matches(item: NotificationEntity, query: String): Boolean {
        val trimmedQuery = query.trim()
        if (trimmedQuery.isEmpty()) return true

        val queryTokens = trimmedQuery.lowercase().split("\\s+".toRegex()).filter { it.isNotEmpty() }
        if (queryTokens.isEmpty()) return true

        // 1. Direct text search check
        val fullText = buildString {
            append(item.appName)
            append(" ")
            append(item.packageName)
            append(" ")
            append(item.title)
            append(" ")
            append(item.content)
            append(" ")
            append(item.subText ?: "")
            append(" ")
            append(item.bigText ?: "")
            append(" ")
            append(item.category ?: "")
        }.lowercase()

        val directMatch = queryTokens.all { token -> fullText.contains(token) }
        if (directMatch) return true

        // 2. Dynamic cluster expansion from DynamicClusterManager
        val dynamicClusters = DynamicClusterManager.getDynamicClusters()
        val queryClusters = DynamicClusterManager.findMatchingClusters(queryTokens)

        if (queryClusters.isNotEmpty()) {
            for (cluster in queryClusters) {
                val keywords = dynamicClusters[cluster] ?: emptySet()
                val hasMatchInNotification = keywords.any { kw -> fullText.contains(kw) }
                if (hasMatchInNotification) return true
            }
        }

        return false
    }
}
