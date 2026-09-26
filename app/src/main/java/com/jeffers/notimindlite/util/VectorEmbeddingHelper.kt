package com.jeffers.notimindlite.util

import android.util.LruCache
import kotlin.math.sqrt

/**
 * On-device vector embedding generator with a tunable memory footprint.
 */
object VectorEmbeddingHelper {
    const val EMBEDDING_DIM = 128
    const val DEFAULT_CACHE_SIZE = 256
    const val LOW_MEMORY_CACHE_SIZE = 64
    const val DEFAULT_BATCH_SIZE = 32
    const val LOW_MEMORY_BATCH_SIZE = 8

    private var lowMemoryMode = false
    private var cacheSize = DEFAULT_CACHE_SIZE
    private var embeddingCache = LruCache<String, FloatArray>(cacheSize)

    /** Applies runtime memory tuning without requiring process restart. */
    @Synchronized
    fun configure(lowMemoryMode: Boolean = false, cacheSize: Int? = null) {
        val requestedSize = cacheSize ?: DEFAULT_CACHE_SIZE
        val targetSize = (if (lowMemoryMode) minOf(requestedSize, LOW_MEMORY_CACHE_SIZE) else requestedSize)
            .coerceIn(1, 10_000)
        if (this.lowMemoryMode == lowMemoryMode && this.cacheSize == targetSize) return
        this.lowMemoryMode = lowMemoryMode
        this.cacheSize = targetSize
        embeddingCache = LruCache(targetSize)
    }

    fun setLowMemoryMode(enabled: Boolean) = configure(enabled)

    fun isLowMemoryMode(): Boolean = lowMemoryMode
    fun cacheCapacity(): Int = cacheSize
    fun embeddingBatchSize(): Int = if (lowMemoryMode) LOW_MEMORY_BATCH_SIZE else DEFAULT_BATCH_SIZE

    fun updateCacheSize(newSize: Int) = configure(lowMemoryMode, newSize)

    fun clearCache() {
        embeddingCache.evictAll()
    }

    /** Computes a normalized dense vector embedding for arbitrary text. */
    fun computeEmbedding(text: String, lowMemoryMode: Boolean = this.lowMemoryMode): FloatArray {
        if (lowMemoryMode != this.lowMemoryMode) configure(lowMemoryMode)
        if (text.isBlank()) return FloatArray(EMBEDDING_DIM)
        val cached = embeddingCache.get(text)
        if (cached != null) return cached

        val vector = FloatArray(EMBEDDING_DIM)
        val cleanText = text.lowercase()
        val words = cleanText.split("\\s+".toRegex()).filter { it.isNotBlank() }
        for (word in words) {
            vector[(word.hashCode() and 0x7FFFFFFF) % 64] += 1.5f
            if (word.length >= 3) {
                for (i in 0..word.length - 3) {
                    val tri = word.substring(i, i + 3)
                    vector[32 + ((tri.hashCode() and 0x7FFFFFFF) % 64)] += 0.8f
                }
            }
        }

        var anchorDimOffset = 96
        for ((_, keywords) in DynamicClusterManager.getDynamicClusters()) {
            var domainMatchWeight = 0f
            for (keyword in keywords) if (cleanText.contains(keyword)) domainMatchWeight += 1.8f
            if (domainMatchWeight > 0f) {
                val dim = anchorDimOffset % EMBEDDING_DIM
                vector[dim] += domainMatchWeight
                vector[(dim + 1) % EMBEDDING_DIM] += domainMatchWeight * 0.7f
            }
            anchorDimOffset += 4
        }

        var sumSquares = 0.0
        for (value in vector) sumSquares += value * value
        val magnitude = sqrt(sumSquares).toFloat()
        if (magnitude > 0f) for (i in vector.indices) vector[i] /= magnitude
        embeddingCache.put(text, vector)
        return vector
    }

    fun cosineSimilarity(vecA: FloatArray, vecB: FloatArray): Float {
        if (vecA.size != vecB.size || vecA.isEmpty()) return 0f
        var dotProduct = 0f
        for (i in vecA.indices) {
            val a = vecA[i]
            val b = vecB[i]
            if (!a.isNaN() && !b.isNaN() && !a.isInfinite() && !b.isInfinite()) dotProduct += a * b
        }
        if (dotProduct.isNaN() || dotProduct.isInfinite()) return 0f
        return dotProduct.coerceIn(0f, 1f)
    }
}
