package com.jeffers.notimindlite.util

import com.jeffers.notimindlite.data.local.NotificationEntity

object SemanticSearchHelper {

    private val SEMANTIC_SYNONYM_GROUPS: Map<String, Set<String>> = mapOf(
        "finance" to setOf(
            "money", "bank", "payment", "receipt", "transfer", "balance", "credit", "debit",
            "card", "wallet", "cash", "bill", "invoice", "charge", "paid", "spent", "usd",
            "crypto", "paypal", "venmo", "zelle", "chase", "revolut", "banking", "finance"
        ),
        "chat" to setOf(
            "chat", "message", "dm", "text", "talk", "inbox", "conversation", "sms",
            "whatsapp", "telegram", "signal", "messenger", "discord", "slack", "reply", "typing"
        ),
        "delivery" to setOf(
            "delivery", "food", "order", "package", "track", "courier", "uber", "doordash",
            "grubhub", "amazon", "ship", "shipped", "arriving", "driver", "transit", "pickup"
        ),
        "security" to setOf(
            "code", "otp", "password", "2fa", "security", "verify", "verification", "login",
            "auth", "pin", "token", "alert", "suspicious", "authentication"
        ),
        "travel" to setOf(
            "flight", "ride", "trip", "hotel", "uber", "lyft", "airline", "train", "bus",
            "ticket", "gate", "boarding", "terminal", "travel", "booking"
        ),
        "reminder" to setOf(
            "reminder", "event", "calendar", "meeting", "agenda", "schedule", "alarm", "task",
            "todo", "timer", "due", "appointment"
        ),
        "media" to setOf(
            "music", "video", "song", "play", "pause", "spotify", "youtube", "netflix",
            "podcast", "movie", "audio", "track", "playing"
        ),
        "system" to setOf(
            "battery", "update", "charging", "wifi", "bluetooth", "network", "storage",
            "system", "download", "installed", "sync"
        )
    )

    /**
     * Performs semantic keyword matching on a NotificationEntity against a search query.
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

        // 2. Semantic synonym cluster expansion
        val queryClusters = mutableSetOf<String>()
        for (token in queryTokens) {
            for ((clusterName, synonyms) in SEMANTIC_SYNONYM_GROUPS) {
                if (token == clusterName || synonyms.contains(token)) {
                    queryClusters.add(clusterName)
                }
            }
        }

        if (queryClusters.isNotEmpty()) {
            for (cluster in queryClusters) {
                val synonyms = SEMANTIC_SYNONYM_GROUPS[cluster] ?: emptySet()
                val hasMatchInNotification = synonyms.any { syn -> fullText.contains(syn) }
                if (hasMatchInNotification) return true
            }
        }

        return false
    }
}
