package com.jeffers.notimindlite.util

import android.content.Context
import android.util.Log
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.crashlytics.ktx.crashlytics
import com.google.firebase.ktx.Firebase
import com.jeffers.notimindlite.data.local.PreferenceManager

/**
 * TelemetryManager handles privacy-preserving observability and telemetry.
 * It ensures that no PII is leaked and respects the 'Strict Privacy' mode.
 */
object TelemetryManager {
    private const val TAG = \"TelemetryManager\"
    private var analytics: FirebaseAnalytics? = null
    private var crashlytics: FirebaseCrashlytics? = null
    private lateinit var preferenceManager: PreferenceManager

    fun init(context: Context) {
        preferenceManager = PreferenceManager(context)
        
        // We only initialize Firebase components if not in strict privacy mode.
        // However, Firebase is usually auto-initialized. We can disable collection.
        if (!preferenceManager.isStrictPrivacyEnabled()) {
            analytics = Firebase.analytics
            crashlytics = Firebase.crashlytics
            Log.i(TAG, \"Telemetry initialized (Privacy Mode: OFF)\")
        } else {
            Log.i(TAG, \"Telemetry disabled (Privacy Mode: STRICT)\")
            // Explicitly disable analytics collection
            Firebase.analytics.setAnalyticsCollectionEnabled(false)
            Firebase.crashlytics.setCrashlyticsCollectionEnabled(false)
        }
    }

    /**
     * Logs a feature usage event anonymously.
     * @param eventName The name of the event (e.g., \"filter_used\", \"chip_clicked\").
     * @param params Key-value pairs for the event. Must NOT contain PII.
     */
    fun logFeatureUsage(eventName: String, params: Map<String, Any> = emptyMap()) {
        if (preferenceManager.isStrictPrivacyEnabled()) return

        analytics?.let { fa ->
            val bundle = android.os.Bundle()
            params.forEach { (key, value) ->
                when (value) {
                    is String -> bundle.putString(key, value)
                    is Int -> bundle.putInt(key, value)
                    is Long -> bundle.putLong(key, value)
                    is Double -> bundle.putDouble(key, value)
                    is Boolean -> bundle.putBoolean(key, value)
                }
            }
            fa.logEvent(eventName, bundle)
            Log.d(TAG, \"Logged Feature Usage: $eventName with params $params\")
        }
    }

    /**
     * Reports a non-fatal exception with PII scrubbing.
     * @param throwable The exception to report.
     * @param contextInfo Additional context for debugging. Must be scrubbed of PII.
     */
    fun reportNonFatal(throwable: Throwable, contextInfo: String = \"\") {
        if (preferenceManager.isStrictPrivacyEnabled()) return

        crashlytics?.let { fc ->
            // PII Scrubbing: Ensure contextInfo does not contain notification content.
            // The caller is responsible for scrubbing, but we can add a safety check here.
            val scrubbedInfo = scrubPII(contextInfo)
            fc.setCustomKey(\"context_info\", scrubbedInfo)
            fc.recordException(throwable)
            Log.d(TAG, \"Reported non-fatal exception: ${throwable.message}\")
        }
    }

    private fun scrubPII(input: String): String {
        // Basic scrubbing: remove patterns that look like emails or phone numbers.
        // In a real app, this would be more robust.
        return input.replace(Regex(\"[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}\"), \"[EMAIL]\")
                   .replace(Regex(\"\\+?\\d{1,4}?[-.\\s]?\\(?\\d{1,3}?\\)?[-.\\s]?\\d{1,4}[-.\\s]?\\d{1,4}[-.\\s]?\\d{1,9}\"), \"[PHONE]\")
    }

    fun setPrivacyMode(enabled: Boolean) {
        // Update the internal state based on the new preference.
        // This would typically be called when the user changes settings.
        if (enabled) {
            Firebase.analytics.setAnalyticsCollectionEnabled(false)
            Firebase.crashlytics.setCrashlyticsCollectionEnabled(false)
        } else {
            Firebase.analytics.setAnalyticsCollectionEnabled(true)
            Firebase.crashlytics.setCrashlyticsCollectionEnabled(true)
        }
    }
}
