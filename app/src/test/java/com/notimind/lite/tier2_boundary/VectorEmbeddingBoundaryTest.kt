package com.notimind.lite.tier2_boundary

import com.jeffers.notimindlite.util.VectorEmbeddingHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VectorEmbeddingBoundaryTest {

    @Test
    fun `computeEmbedding - empty string returns zero vector`() {
        val result = VectorEmbeddingHelper.computeEmbedding("")
        assertEquals(VectorEmbeddingHelper.EMBEDDING_DIM, result.size)
        result.forEach { assertEquals(0f, it, 0f) }
    }

    @Test
    fun `computeEmbedding - blank string returns zero vector`() {
        val result = VectorEmbeddingHelper.computeEmbedding("   ")
        assertEquals(VectorEmbeddingHelper.EMBEDDING_DIM, result.size)
        result.forEach { assertEquals(0f, it, 0f) }
    }

    @Test
    fun `computeEmbedding - very long string handles without crash`() {
        val longText = "word ".repeat(10000)
        val result = VectorEmbeddingHelper.computeEmbedding(longText)
        assertEquals(VectorEmbeddingHelper.EMBEDDING_DIM, result.size)
        
        // Verify L2 normalization
        var sumSquares = 0f
        result.forEach { sumSquares += it * it }
        assertEquals(1.0f, sumSquares, 0.01f)
    }

    @Test
    fun `computeEmbedding - special characters only`() {
        val text = "!@#$%^&*()_+ \n\t"
        val result = VectorEmbeddingHelper.computeEmbedding(text)
        assertEquals(VectorEmbeddingHelper.EMBEDDING_DIM, result.size)
    }

    @Test
    fun `cosineSimilarity - identical vectors return 1`() {
        val vec = FloatArray(128) { 0.1f }
        // Normalize first because the helper expects normalized vectors
        var mag = 0f
        vec.forEach { mag += it * it }
        val normVec = vec.map { it / kotlin.math.sqrt(mag) }.toFloatArray()
        
        val sim = VectorEmbeddingHelper.cosineSimilarity(normVec, normVec)
        assertEquals(1.0f, sim, 0.001f)
    }

    @Test
    fun `cosineSimilarity - orthogonal vectors return 0`() {
        val vecA = FloatArray(128) { 0f }
        vecA[0] = 1f
        val vecB = FloatArray(128) { 0f }
        vecB[1] = 1f
        
        val sim = VectorEmbeddingHelper.cosineSimilarity(vecA, vecB)
        assertEquals(0f, sim, 0.001f)
    }

    @Test
    fun `cosineSimilarity - different size vectors return 0`() {
        val vecA = FloatArray(128) { 0.1f }
        val vecB = FloatArray(64) { 0.1f }
        
        val sim = VectorEmbeddingHelper.cosineSimilarity(vecA, vecB)
        assertEquals(0f, sim, 0f)
    }

    @Test
    fun `cosineSimilarity - empty vectors return 0`() {
        val vecA = FloatArray(0)
        val vecB = FloatArray(0)
        
        val sim = VectorEmbeddingHelper.cosineSimilarity(vecA, vecB)
        assertEquals(0f, sim, 0f)
    }

    @Test
    fun `cosineSimilarity - zero vectors return 0`() {
        val vecA = FloatArray(128) { 0f }
        val vecB = FloatArray(128) { 0f }
        
        val sim = VectorEmbeddingHelper.cosineSimilarity(vecA, vecB)
        assertEquals(0f, sim, 0f)
    }

    @Test
    fun `cosineSimilarity - extremely large values handle without overflow`() {
        val vecA = FloatArray(128) { 1e30f }
        val vecB = FloatArray(128) { 1e30f }
        
        val sim = VectorEmbeddingHelper.cosineSimilarity(vecA, vecB)
        // Should still be close to 1.0 if normalized internally or handled correctly
        assertTrue(sim >= 0f && sim <= 1.0f)
    }

    @Test
    fun `cosineSimilarity - NaN or Infinite values handle gracefully`() {
        val vecA = FloatArray(128) { 0.1f }
        val vecB = FloatArray(128) { 0.1f }
        vecB[0] = Float.NaN
        
        val sim = VectorEmbeddingHelper.cosineSimilarity(vecA, vecB)
        assertTrue("Similarity should be in [0, 1] even with NaNs", sim >= 0f && sim <= 1.0f)
    }

    @Test
    fun `computeEmbedding - extreme repetitive text doesn't blow up`() {
        val text = "a".repeat(100000)
        val result = VectorEmbeddingHelper.computeEmbedding(text)
        assertEquals(VectorEmbeddingHelper.EMBEDDING_DIM, result.size)
        
        var sumSquares = 0f
        result.forEach { sumSquares += it * it }
        assertEquals(1.0f, sumSquares, 0.01f)
    }
}
