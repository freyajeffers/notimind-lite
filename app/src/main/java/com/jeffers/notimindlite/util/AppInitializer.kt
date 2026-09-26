package com.jeffers.notimindlite.util

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.jeffers.notimindlite.data.local.PreferencesRepository
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * AppInitializer handles the streamlined startup logic for NotiMind Lite.
 * It ensures that critical system components (Firebase, Database, Logger) 
 * are initialized exactly once and in the correct order.
 */
object AppInitializer {
    private const val TAG = "AppInitializer"
    private val isInitialized = AtomicBoolean(false)

    fun initialize(context: Context) {
        if (isInitialized.getAndSet(true)) {
            
            return
        }

        Log.i(TAG, "AppInitializer: Starting system initialization...")

        try {
            val preferences = PreferencesRepository(context)
            // Apply memory-sensitive settings before any embedding work can start.
            VectorEmbeddingHelper.configure(
                lowMemoryMode = preferences.lowMemoryMode.value,
                cacheSize = preferences.vectorCacheMax.value
            )

            // Firebase Initialization, guarded for headless/test environments.
            if (FirebaseApp.getApps(context).isEmpty()) FirebaseApp.initializeApp(context)
            TelemetryManager.configure(
                context = context,
                enabled = preferences.enableTelemetry.value,
                telemetryLevel = preferences.telemetryLevel.value
            )

            setupInternalLogging()

            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                try {
                    AuditLogger.checkAndLogAppDataCleared(context)
                } catch (e: Exception) {
                    Log.w(TAG, "AuditLogger check failed on startup", e)
                }
            }

            Log.i(TAG, "AppInitializer: System initialization complete.")
        } catch (e: Exception) {
            Log.e(TAG, "AppInitializer: Critical failure during initialization: ${e.message}", e)
            // We don't crash the app here to allow partial functionality, 
            // but we log the error for debugging.
        }
    }

    private fun setupInternalLogging() {
        // Placeholder for internal logger setup
        
    }
}
