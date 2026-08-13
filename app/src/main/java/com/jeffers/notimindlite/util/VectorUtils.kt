package com.jeffers.notimindlite.util

import java.nio.ByteBuffer
import kotlin.math.sqrt

object VectorUtils {

    /**
     * Calculates mathematical Cosine Similarity between two float vectors.
     */
    fun cosineSimilarity(vectorA: FloatArray, vectorB: FloatArray): Float {
        if (vectorA.size != vectorB.size || vectorA.isEmpty()) return 0.0f
        var dotProduct = 0.0f
        var normA = 0.0f
        var normB = 0.0f
        for (i in vectorA.indices) {
            val a = vectorA[i]
            val b = vectorB[i]
            dotProduct += a * b
            normA += a * a
            normB += b * b
        }
        val denominator = sqrt(normA) * sqrt(normB)
        return if (denominator > 0.0f) (dotProduct / denominator).coerceIn(0.0f, 1.0f) else 0.0f
    }

    /**
     * Converts a FloatArray into a ByteArray for SQLite BLOB storage.
     */
    fun floatArrayToByteArray(floats: FloatArray): ByteArray {
        val buffer = ByteBuffer.allocate(floats.size * 4)
        buffer.asFloatBuffer().put(floats)
        return buffer.array()
    }

    /**
     * Reconstructs a FloatArray from a ByteArray stored in SQLite BLOB.
     */
    fun byteArrayToFloatArray(bytes: ByteArray): FloatArray {
        val floatBuffer = ByteBuffer.wrap(bytes).asFloatBuffer()
        val floats = FloatArray(floatBuffer.remaining())
        floatBuffer.get(floats)
        return floats
    }
}
