package com.jeffers.notimindlite.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VectorEmbeddingHelperExtendedTest {
    @Test
    fun cosineSimilarity_identicalVectors_returnsOne() {
        val vector = floatArrayOf(0.5f, 0.5f, 0.5f, 0.5f)
        val similarity = VectorEmbeddingHelper.cosineSimilarity(vector, vector)
        assertEquals(1.0f, similarity, 0.001f)
    }

    @Test
    fun cosineSimilarity_orthogonalVectors_returnsZero() {
        val vecA = floatArrayOf(1f, 0f, 0f, 0f)
        val vecB = floatArrayOf(0f, 1f, 0f, 0f)
        val similarity = VectorEmbeddingHelper.cosineSimilarity(vecA, vecB)
        assertEquals(0.0f, similarity, 0.001f)
    }

    @Test
    fun cosineSimilarity_mismatchedVectorLengths_returnsZero() {
        val vecA = floatArrayOf(1f, 0f)
        val vecB = floatArrayOf(1f, 0f, 0f)
        assertEquals(0.0f, VectorEmbeddingHelper.cosineSimilarity(vecA, vecB), 0.0f)
    }

    @Test
    fun cosineSimilarity_emptyVectors_returnsZero() {
        assertEquals(0.0f, VectorEmbeddingHelper.cosineSimilarity(floatArrayOf(), floatArrayOf()), 0.0f)
    }

    @Test
    fun cosineSimilarity_allNonFiniteVector_returnsZero() {
        val allNanVec = floatArrayOf(Float.NaN, Float.NaN)
        val normalVec = floatArrayOf(1f, 1f)
        assertEquals(0.0f, VectorEmbeddingHelper.cosineSimilarity(allNanVec, normalVec), 0.0f)

        val allInfVec = floatArrayOf(Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)
        assertEquals(0.0f, VectorEmbeddingHelper.cosineSimilarity(allInfVec, normalVec), 0.0f)
    }

    @Test
    fun cosineSimilarity_partiallyNonFiniteVector_skipsNonFiniteDimensionsGracefully() {
        val partiallyNan = floatArrayOf(Float.NaN, 1f)
        val normalVec = floatArrayOf(1f, 1f)
        // NaN dimension is skipped; finite dimension 1f * 1f yields 1.0f
        assertEquals(1.0f, VectorEmbeddingHelper.cosineSimilarity(partiallyNan, normalVec), 0.001f)
    }

    @Test
    fun cacheManagement_clearCacheAndRecompute_operatesConsistently() {
        VectorEmbeddingHelper.clearCache()
        val text = "Consistent Notification Alert"
        val firstComputation = VectorEmbeddingHelper.computeEmbedding(text)
        val secondComputation = VectorEmbeddingHelper.computeEmbedding(text)

        // Cached result should match element-wise
        assertEquals(firstComputation.size, secondComputation.size)
        for (i in firstComputation.indices) {
            assertEquals(firstComputation[i], secondComputation[i], 0.0001f)
        }

        VectorEmbeddingHelper.clearCache()
        val thirdComputation = VectorEmbeddingHelper.computeEmbedding(text)
        assertEquals(firstComputation.size, thirdComputation.size)
        for (i in firstComputation.indices) {
            assertEquals(firstComputation[i], thirdComputation[i], 0.0001f)
        }
    }

    @Test
    fun computeEmbedding_normalizedL2Norm_isApproximatelyOneForNonEmpty() {
        val text = "Package delivery notification with status update"
        val embedding = VectorEmbeddingHelper.computeEmbedding(text)
        assertEquals(VectorEmbeddingHelper.EMBEDDING_DIM, embedding.size)

        var sumSquares = 0.0
        for (v in embedding) {
            sumSquares += (v * v)
        }
        val norm = Math.sqrt(sumSquares).toFloat()
        assertTrue("Expected L2 norm near 1.0, got $norm", Math.abs(norm - 1.0f) < 0.01f)
    }
}
