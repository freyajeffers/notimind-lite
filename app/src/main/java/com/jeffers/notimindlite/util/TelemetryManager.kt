package com.jeffers.notimindlite.util

import android.content.Context
import com.google.firebase.analytics.FirebaseAnalytics

/** Privacy-aware adapter for Firebase telemetry. */
object TelemetryManager {
    enum class Level { MINIMAL, STANDARD, FULL }

    @Volatile private var enabled = false
    @Volatile private var level = Level.MINIMAL

    fun configure(context: Context, enabled: Boolean, telemetryLevel: String): Level {
        this.enabled = enabled
        this.level = parseLevel(telemetryLevel)
        // Firebase may be unavailable in local/headless builds; retain the local policy state.
        runCatching {
            FirebaseAnalytics.getInstance(context).setAnalyticsCollectionEnabled(enabled)
        }
        return this.level
    }

    fun parseLevel(value: String?): Level = when (value?.trim()?.lowercase()) {
        "full", "verbose" -> Level.FULL
        "standard", "normal" -> Level.STANDARD
        else -> Level.MINIMAL
    }

    fun isEnabled(): Boolean = enabled
    fun currentLevel(): Level = level

    /** Returns whether an event at [requiredLevel] may be sent. */
    fun allows(requiredLevel: Level = Level.MINIMAL): Boolean =
        enabled && level.ordinal >= requiredLevel.ordinal

    fun disable(context: Context) = configure(context, false, "minimal")
}
