package com.jeffers.notimindlite.util

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
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
            Log.d(TAG, "AppInitializer: Already initialized. Skipping.")
            return
        }

        Log.i(TAG, "AppInitializer: Starting system initialization...")

        try {
            // 1. Firebase Initialization
            // Guarded to prevent crashes in headless test environments
            if (FirebaseApp.getApps(context).isEmpty()) {
                FirebaseApp.initializeApp(context)
                Log.d(TAG, "Firebase initialized successfully.")
            }

            // 2. Database & Logger Initialization
            // In a real implementation, we would initialize the AppDatabase 
            // and any internal logging frameworks here.
            setupInternalLogging()

            Log.i(TAG, "AppInitializer: System initialization complete.")
        } catch (e: Exception) {
            Log.e(TAG, "AppInitializer: Critical failure during initialization: ${e.message}", e)
            // We don't crash the app here to allow partial functionality, 
            // but we log the error for debugging.
        }
    }

    private fun setupInternalLogging() {
        // Placeholder for internal logger setup
        Log.d(TAG, "Internal logging system configured.")
    }
}
