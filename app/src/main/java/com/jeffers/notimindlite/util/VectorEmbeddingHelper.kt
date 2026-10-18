package com.jeffers.notimindlite.util

import android.util.LruCache
import kotlin.math.sqrt

private fun isFinite(value: Float): Boolean = !value.isNaN() && !value.isInfinite()

private const val HASH_BUCKETS = 64
private const val TRIGRAM_LENGTH = 3
private const val TRIGRAM_DIMENSION_OFFSET = 32
private const val WORD_WEIGHT = 1.5f
private const val TRIGRAM_WEIGHT = 0.8f
private const val DOMAIN_WEIGHT = 1.8f
private const val DOMAIN_DIMENSION_OFFSET = 96
private const val DOMAIN_DIMENSION_STEP = 4
private const val SECONDARY_DIMENSION_WEIGHT = 0.7f
private const val MIN_CACHE_SIZE = 1
private const val MAX_CACHE_SIZE = 10_000
private const val HASH_MASK = 0x7FFFFFFF

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
            .coerceIn(MIN_CACHE_SIZE, MAX_CACHE_SIZE)
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
        return embeddingCache.get(text) ?: computeAndCacheEmbedding(text)
    }

    private fun computeAndCacheEmbedding(text: String): FloatArray {
        if (text.isBlank()) return FloatArray(EMBEDDING_DIM)
        val vector = FloatArray(EMBEDDING_DIM)
        val cleanText = text.lowercase()
        val words = cleanText.split("\\s+".toRegex()).filter { it.isNotBlank() }
        for (word in words) {
            vector[(word.hashCode() and HASH_MASK) % HASH_BUCKETS] += WORD_WEIGHT
            if (word.length >= TRIGRAM_LENGTH) {
                for (i in 0..word.length - TRIGRAM_LENGTH) {
                    val tri = word.substring(i, i + TRIGRAM_LENGTH)
                    val bucket = (tri.hashCode() and HASH_MASK) % HASH_BUCKETS
                    vector[TRIGRAM_DIMENSION_OFFSET + bucket] += TRIGRAM_WEIGHT
                }
            }
        }

        var anchorDimOffset = DOMAIN_DIMENSION_OFFSET
        for ((_, keywords) in DynamicClusterManager.getDynamicClusters()) {
            var domainMatchWeight = 0f
            for (keyword in keywords) if (cleanText.contains(keyword)) domainMatchWeight += DOMAIN_WEIGHT
            if (domainMatchWeight > 0f) {
                val dim = anchorDimOffset % EMBEDDING_DIM
                vector[dim] += domainMatchWeight
                vector[(dim + 1) % EMBEDDING_DIM] += domainMatchWeight * SECONDARY_DIMENSION_WEIGHT
            }
            anchorDimOffset += DOMAIN_DIMENSION_STEP
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
            if (isFinite(a) && isFinite(b)) dotProduct += a * b
        }
        return if (isFinite(dotProduct)) dotProduct.coerceIn(0f, 1f) else 0f
    }
}
