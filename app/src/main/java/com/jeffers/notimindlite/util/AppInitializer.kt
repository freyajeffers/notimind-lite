package com.jeffers.notimindlite.util

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.google.firebase.FirebaseApp
import com.jeffers.notimindlite.data.local.PreferencesRepository
import com.jeffers.notimindlite.data.local.RetentionCleanupWorker
import com.jeffers.notimindlite.util.AppLogger
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * AppInitializer handles the streamlined startup logic for NotiMind Lite.
 * It ensures that critical system components (Firebase, Database, Logger)
 * are initialized exactly once and in the correct order.
 */
object AppInitializer {
    private const val TAG = "AppInitializer"
    private val isInitialized = AtomicBoolean(false)

    @Volatile private var activeFeatureFlags: FeatureFlagLoader = FeatureFlagLoader()

    fun featureFlags(): FeatureFlagLoader = activeFeatureFlags

    fun initialize(context: Context) {
        if (isInitialized.getAndSet(true)) {
            return
        }

        AppLogger.i(TAG, "AppInitializer: Starting system initialization...")

        try {
            val preferences = PreferencesRepository(context)
            val featureFlags = FeatureFlagLoader().also { loader ->
                loader.setTestModeFlags(preferences.experimentalManager.testModeFlags.value)
                activeFeatureFlags = loader
            }
            if (preferences.experimentalManager.featureFlagsUrl.value
                    .isNotBlank()
            ) {
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                    featureFlags.loadFromUrl(preferences.experimentalManager.featureFlagsUrl.value)
                }
            }
            // Apply memory-sensitive settings before any embedding work can start.
            VectorEmbeddingHelper.configure(
                lowMemoryMode = preferences.lowMemoryMode.value,
                cacheSize = preferences.vectorCacheMax.value
            )
            val retentionWork = PeriodicWorkRequestBuilder<RetentionCleanupWorker>(1, TimeUnit.DAYS).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "notimind-retention-cleanup",
                ExistingPeriodicWorkPolicy.UPDATE,
                retentionWork
            )

            // Firebase Initialization, guarded for headless/test environments.
            if (FirebaseApp.getApps(context).isEmpty()) FirebaseApp.initializeApp(context)
            TelemetryManager.configure(
                context = context,
                enabled = preferences.telemetryManager.enabled.value,
                telemetryLevel = preferences.telemetryManager.level.value
            )

            setupInternalLogging()

            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                try {
                    AuditLogger.checkAndLogAppDataCleared(context)
                } catch (e: java.io.IOException) {
                    AppLogger.w(TAG, "AuditLogger IO check failed on startup", e)
                } catch (e: SecurityException) {
                    AppLogger.w(TAG, "AuditLogger permission denied on startup", e)
                }
            }

            AppLogger.i(TAG, "AppInitializer: System initialization complete.")
        } catch (e: com.google.firebase.FirebaseException) {
            AppLogger.e(TAG, "AppInitializer: Firebase initialization failed: ${e.message}", e)
        } catch (e: java.io.IOException) {
            AppLogger.e(TAG, "AppInitializer: IO failure during initialization: ${e.message}", e)
        } catch (e: IllegalStateException) {
            AppLogger.e(TAG, "AppInitializer: Illegal state during initialization: ${e.message}", e)
        } catch (e: IllegalArgumentException) {
            AppLogger.e(TAG, "AppInitializer: Invalid argument during initialization: ${e.message}", e)
        } catch (e: SecurityException) {
            AppLogger.e(TAG, "AppInitializer: Security exception during initialization: ${e.message}", e)
        }
    }

    private fun setupInternalLogging() {
        // Placeholder for internal logger setup
    }
}
