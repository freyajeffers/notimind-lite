package com.jeffers.notimindlite.util

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.util.Log
import java.util.concurrent.ConcurrentHashMap

/**
 * Dynamically discovers and builds semantic category clusters based on
 * installed applications and Android's ApplicationInfo.CATEGORY_* metadata.
 */
object DynamicClusterManager {
    private const val TAG = "DynamicClusterManager"

    // Maps category names to dynamically extracted keyword and app name clusters
    private val dynamicClusters = ConcurrentHashMap<String, MutableSet<String>>()

    // Standard category tags mapped from ApplicationInfo.CATEGORY_* constants
    private val CATEGORY_BASE_TAGS: Map<Int, List<String>> = mapOf(
        ApplicationInfo.CATEGORY_GAME to listOf("game", "gaming", "play", "player", "score", "level"),
        ApplicationInfo.CATEGORY_AUDIO to listOf("audio", "music", "song", "track", "podcast", "radio", "sound", "stream"),
        ApplicationInfo.CATEGORY_VIDEO to listOf("video", "movie", "film", "stream", "tv", "clip", "watch", "show"),
        ApplicationInfo.CATEGORY_IMAGE to listOf("image", "photo", "picture", "camera", "gallery", "snapshot"),
        ApplicationInfo.CATEGORY_SOCIAL to listOf("social", "chat", "message", "dm", "text", "conversation", "talk", "post", "friend"),
        ApplicationInfo.CATEGORY_NEWS to listOf("news", "article", "feed", "headline", "breaking", "journal", "magazine"),
        ApplicationInfo.CATEGORY_MAPS to listOf("maps", "navigation", "transit", "route", "commute", "travel", "ride", "location", "gps"),
        ApplicationInfo.CATEGORY_PRODUCTIVITY to listOf("productivity", "work", "office", "task", "todo", "calendar", "document", "sheet", "note", "meeting")
    )

    private val CATEGORY_NAMES: Map<Int, String> = mapOf(
        ApplicationInfo.CATEGORY_GAME to "game",
        ApplicationInfo.CATEGORY_AUDIO to "audio",
        ApplicationInfo.CATEGORY_VIDEO to "video",
        ApplicationInfo.CATEGORY_IMAGE to "image",
        ApplicationInfo.CATEGORY_SOCIAL to "social",
        ApplicationInfo.CATEGORY_NEWS to "news",
        ApplicationInfo.CATEGORY_MAPS to "maps",
        ApplicationInfo.CATEGORY_PRODUCTIVITY to "productivity"
    )

    @Volatile
    private var isInitialized = false

    /**
     * Initializes dynamic clusters by querying Android's PackageManager for installed apps
     * and categorizing them into dynamic keyword sets.
     */
    fun initialize(context: Context) {
        if (isInitialized) return
        synchronized(this) {
            if (isInitialized) return
            try {
                val pm = context.packageManager
                val apps = pm.getInstalledApplications(PackageManager.MATCH_UNINSTALLED_PACKAGES)

                // Initialize base category tags
                for ((catId, tags) in CATEGORY_BASE_TAGS) {
                    val catName = CATEGORY_NAMES[catId] ?: "misc"
                    val cluster = dynamicClusters.getOrPut(catName) { ConcurrentHashMap.newKeySet() }
                    cluster.addAll(tags)
                }

                // Add installed apps to their respective category clusters
                for (app in apps) {
                    val appLabel = try {
                        pm.getApplicationLabel(app).toString().lowercase()
                    } catch (e: Exception) {
                        null
                    }
                    val pkgName = app.packageName.lowercase()
                    val catId = app.category

                    if (catId != ApplicationInfo.CATEGORY_UNDEFINED && CATEGORY_NAMES.containsKey(catId)) {
                        val catName = CATEGORY_NAMES[catId]!!
                        val cluster = dynamicClusters.getOrPut(catName) { ConcurrentHashMap.newKeySet() }

                        if (!appLabel.isNullOrBlank()) {
                            cluster.add(appLabel)
                            cluster.addAll(appLabel.split("\\s+".toRegex()).filter { it.length > 2 })
                        }
                        val pkgParts = pkgName.split(".")
                        val meaningfulPart = pkgParts.lastOrNull { it != "android" && it != "app" && it != "mobile" }
                        if (!meaningfulPart.isNullOrBlank() && meaningfulPart.length > 2) {
                            cluster.add(meaningfulPart)
                        }
                    }
                }
                isInitialized = true
                Log.d(TAG, "Initialized ${dynamicClusters.size} dynamic clusters from PackageManager.")
            } catch (e: Exception) {
                Log.e(TAG, "Error initializing dynamic clusters from PackageManager", e)
            }
        }
    }

    /**
     * Retrieves all discovered dynamic clusters and their associated keywords.
     */
    fun getDynamicClusters(): Map<String, Set<String>> {
        return dynamicClusters
    }

    /**
     * Finds matching cluster names for a given set of query tokens.
     */
    fun findMatchingClusters(queryTokens: List<String>): Set<String> {
        val matched = mutableSetOf<String>()
        for (token in queryTokens) {
            val lowerToken = token.lowercase()
            for ((clusterName, keywords) in dynamicClusters) {
                if (lowerToken == clusterName || keywords.contains(lowerToken)) {
                    matched.add(clusterName)
                }
            }
        }
        return matched
    }
}
