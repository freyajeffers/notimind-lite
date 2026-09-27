package com.jeffers.notimindlite.util

import android.content.Context

/**
 * Compatibility facade for callers that historically imported the utility package.
 * The implementation lives in the domain clustering package.
 */
typealias ClusterNode = com.jeffers.notimindlite.domain.clustering.ClusterNode

object DynamicClusterManager {
    fun initialize(context: Context) =
        com.jeffers.notimindlite.domain.clustering.DynamicClusterManager.initialize(context)

    fun getDynamicClusters(): Map<String, Set<String>> =
        com.jeffers.notimindlite.domain.clustering.DynamicClusterManager.getDynamicClusters()

    fun getHierarchicalClusters(): Map<String, ClusterNode> =
        com.jeffers.notimindlite.domain.clustering.DynamicClusterManager.getHierarchicalClusters()

    fun getSubClusters(domain: String): Map<String, ClusterNode>? =
        com.jeffers.notimindlite.domain.clustering.DynamicClusterManager.getSubClusters(domain)

    fun findMatchingClusters(queryTokens: List<String>): Map<String, Set<ClusterNode>> =
        com.jeffers.notimindlite.domain.clustering.DynamicClusterManager.findMatchingClusters(queryTokens)
}
