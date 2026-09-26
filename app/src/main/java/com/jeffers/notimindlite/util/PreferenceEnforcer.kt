package com.jeffers.notimindlite.util

import android.content.Context
import androidx.work.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import java.util.concurrent.TimeUnit

/**
 * PreferenceEnforcer centralizes runtime application of user preferences.
 * It subscribes to PreferencesRepository StateFlows and applies their
 * effects across app subsystems in a single place so new preferences can
 * be enacted without touching many disparate modules.
 */
class PreferenceEnforcer(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val prefs = com.jeffers.notimindlite.data.local.PreferencesRepository(context)
    private val workManager = WorkManager.getInstance(context)

    fun start() {
        scope.launch {
            // Wire a few high-impact preference observers in parallel
            launch { observeRetention() }
            launch { observeBackupSchedule() }
            launch { observeSearchToggles() }
            launch { observePerformanceTuning() }
            launch { observeTelemetryAndLogging() }
            launch { observeSecuritySettings() }
        }
    }

    fun stop() {
        scope.cancel()
    }

    private suspend fun observeRetention() {
        prefs.retentionDays.collectLatest { days ->
            // Enforce minimum and debug override inside PreferencesRepository.
            val interval = 24L // check daily
            val req = PeriodicWorkRequestBuilder<RetentionWorker>(interval, TimeUnit.HOURS)
                .setInputData(workDataOf("retention_days" to days))
                .setBackoffCriteria(BackoffPolicy.LINEAR, 30, TimeUnit.SECONDS)
                .build()
            workManager.enqueueUniquePeriodicWork("notimind_retention", ExistingPeriodicWorkPolicy.REPLACE, req)
        }
    }

    private suspend fun observeBackupSchedule() {
        prefs.backupIntervalDays.collectLatest { days ->
            if (days <= 0) {
                workManager.cancelUniqueWork("notimind_backup")
            } else {
                val req = PeriodicWorkRequestBuilder<BackupWorker>(days, TimeUnit.DAYS)
                    .setBackoffCriteria(BackoffPolicy.LINEAR, 30, TimeUnit.SECONDS)
                    .build()
                workManager.enqueueUniquePeriodicWork("notimind_backup", ExistingPeriodicWorkPolicy.REPLACE, req)
            }
        }
    }

    private suspend fun observeSearchToggles() {
        val searchEngine = com.jeffers.notimindlite.util.HybridSearchEngine.getInstance(context)
        prefs.enableFts4.collectLatest { enabled ->
            searchEngine.setFtsEnabled(enabled)
        }
        prefs.enableVector.collectLatest { enabled ->
            searchEngine.setVectorEnabled(enabled)
        }
        prefs.enableSemanticRanking.collectLatest { enabled ->
            searchEngine.setSemanticRankingEnabled(enabled)
        }
        prefs.indexAfterDelete.collectLatest { v ->
            searchEngine.setIndexAfterDelete(v)
        }
    }

    private suspend fun observePerformanceTuning() {
        val vectorHelper = com.jeffers.notimindlite.util.VectorEmbeddingHelper.getInstance(context)
        prefs.vectorCacheMax.collectLatest { size ->
            vectorHelper.updateCacheSize(size)
        }
        prefs.maxDbMb.collectLatest { mb ->
            // bounded in preferences repo already; enforce in DB helper if present
            com.jeffers.notimindlite.util.AppInitializer.configureMaxDbSize(mb)
        }
        prefs.lowMemoryMode.collectLatest { low ->
            // Notify components
            com.jeffers.notimindlite.util.AppInitializer.setLowMemoryMode(low)
        }
    }

    private suspend fun observeTelemetryAndLogging() {
        prefs.enableTelemetry.collectLatest { enabled ->
            com.jeffers.notimindlite.util.TelemetryManager.configurePolicy(enabled, prefs.telemetryLevel.value)
        }
        prefs.telemetryLevel.collectLatest { level ->
            com.jeffers.notimindlite.util.TelemetryManager.configurePolicy(prefs.enableTelemetry.value, level)
        }
        prefs.logLevel.collectLatest { lvl ->
            com.jeffers.notimindlite.util.TelemetryManager.setLogLevel(lvl)
        }
    }

    private suspend fun observeSecuritySettings() {
        prefs.dbEncrypted.collectLatest { encrypted ->
            com.jeffers.notimindlite.util.DatabaseExporter.setEncryptedExports(encrypted)
        }
        prefs.useKeystore.collectLatest { use ->
            com.jeffers.notimindlite.util.EncryptionHelper.setUseKeystore(use)
        }
    }
}

// Simple worker stubs used by enforcer. Existing workers (if present) should be preferred.
class RetentionWorker(appContext: android.content.Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val days = inputData.getLong("retention_days", 365)
        com.jeffers.notimindlite.data.local.NotificationRetention.enforceRetention(applicationContext, days)
        return Result.success()
    }
}

class BackupWorker(appContext: android.content.Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        try {
            val prefs = com.jeffers.notimindlite.data.local.PreferencesRepository(applicationContext)
            com.jeffers.notimindlite.util.DatabaseExporter.exportDatabase(applicationContext, prefs)
            return Result.success()
        } catch (e: Exception) {
            return Result.retry()
        }
    }
}
