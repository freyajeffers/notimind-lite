package com.jeffers.notimindlite.util

import kotlin.math.sqrt

/**
 * On-device vector embedding generator.
 * Produces normalized 128-dimensional dense vector embeddings using
 * subword n-gram hashing and dynamically generated ApplicationInfo category clusters.
 */
object VectorEmbeddingHelper {

    const val EMBEDDING_DIM = 128
    private val embeddingCache = android.util.LruCache<String, FloatArray>(256)

    fun clearCache() {
        embeddingCache.evictAll()
    }


    /**
     * Computes a normalized dense vector embedding for arbitrary text.
     */
    fun computeEmbedding(text: String): FloatArray {
        if (text.isBlank()) return FloatArray(EMBEDDING_DIM)
        val cached = embeddingCache.get(text)
        if (cached != null) return cached

        val vector = FloatArray(EMBEDDING_DIM)

        val cleanText = text.lowercase()
        val words = cleanText.split("\\s+".toRegex()).filter { it.isNotBlank() }

        // 1. Subword character 3-gram and 4-gram feature hashing (Dimensions 0..95)
        for (word in words) {
            // Word hash
            val wordHash = (word.hashCode() and 0x7FFFFFFF) % 64
            vector[wordHash] += 1.5f

            // Character n-grams
            if (word.length >= 3) {
                for (i in 0..word.length - 3) {
                    val tri = word.substring(i, i + 3)
                    val triHash = 32 + ((tri.hashCode() and 0x7FFFFFFF) % 64)
                    vector[triHash] += 0.8f
                }
            }
        }

        // 2. Dynamic Semantic Cluster Anchor Projection (Dimensions 96..127)
        val dynamicClusters = DynamicClusterManager.getDynamicClusters()
        var anchorDimOffset = 96

        for ((_, keywords) in dynamicClusters) {
            var domainMatchWeight = 0f
            for (kw in keywords) {
                if (cleanText.contains(kw)) {
                    domainMatchWeight += 1.8f
                }
            }
            if (domainMatchWeight > 0f) {
                val dim = anchorDimOffset % EMBEDDING_DIM
                vector[dim] += domainMatchWeight
                vector[(dim + 1) % EMBEDDING_DIM] += domainMatchWeight * 0.7f
            }
            anchorDimOffset += 4
        }

        // 3. L2 Unit-Length Normalization
        var sumSquares = 0.0
        for (v in vector) {
            sumSquares += (v * v)
        }
        val magnitude = sqrt(sumSquares).toFloat()
        if (magnitude > 0f) {
            for (i in vector.indices) {
                vector[i] /= magnitude
            }
        }

        embeddingCache.put(text, vector)
        return vector
    }

    /**
     * Calculates the Cosine Similarity between two normalized dense vectors.
     * Returns a float in range [-1.0, 1.0], normalized to [0.0, 1.0].
     */
    fun cosineSimilarity(vecA: FloatArray, vecB: FloatArray): Float {
        if (vecA.size != vecB.size || vecA.isEmpty()) return 0f
        var dotProduct = 0f
        for (i in vecA.indices) {
            dotProduct += vecA[i] * vecB[i]
        }
        // Clamped to [0.0, 1.0] for scoring
        return dotProduct.coerceIn(0f, 1f)
    }
}
