package com.jeffers.notimindlite.util

import kotlin.math.sqrt

/**
 * On-device vector embedding generator.
 * Produces normalized 128-dimensional dense vector embeddings using
 * subword n-gram hashing and semantic domain feature projections.
 */
object VectorEmbeddingHelper {

    const val EMBEDDING_DIM = 128

    // Semantic category anchors projected onto dedicated vector dimensions
    private val ANCHOR_DOMAINS: Map<String, List<String>> = mapOf(
        "finance" to listOf("money", "bank", "payment", "receipt", "transfer", "balance", "credit", "debit", "card", "wallet", "cash", "bill", "invoice", "charge", "usd", "crypto", "paypal", "venmo", "zelle"),
        "chat" to listOf("chat", "message", "dm", "text", "talk", "inbox", "conversation", "sms", "whatsapp", "telegram", "signal", "messenger", "discord", "slack", "reply"),
        "delivery" to listOf("delivery", "food", "order", "package", "track", "courier", "uber", "doordash", "grubhub", "amazon", "ship", "shipped", "arriving", "driver"),
        "security" to listOf("code", "otp", "password", "2fa", "security", "verify", "verification", "login", "auth", "pin", "token", "alert"),
        "travel" to listOf("flight", "ride", "trip", "hotel", "uber", "lyft", "airline", "train", "bus", "ticket", "gate", "booking"),
        "reminder" to listOf("reminder", "event", "calendar", "meeting", "agenda", "schedule", "alarm", "task", "todo", "appointment"),
        "media" to listOf("music", "video", "song", "play", "pause", "spotify", "youtube", "netflix", "podcast", "movie", "audio"),
        "system" to listOf("battery", "update", "charging", "wifi", "bluetooth", "network", "storage", "system", "download")
    )

    /**
     * Computes a normalized dense vector embedding for arbitrary text.
     */
    fun computeEmbedding(text: String): FloatArray {
        val vector = FloatArray(EMBEDDING_DIM)
        if (text.isBlank()) return vector

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

        // 2. Semantic Anchor Domain Projection (Dimensions 96..127)
        var anchorDimOffset = 96
        for ((_, keywords) in ANCHOR_DOMAINS) {
            var domainMatchWeight = 0f
            for (kw in keywords) {
                if (cleanText.contains(kw)) {
                    domainMatchWeight += 2.0f
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
