package com.jeffers.notimindlite.data.local

import android.content.Context
import com.jeffers.notimindlite.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** User-facing runtime options persisted in the existing direct-boot-safe preference store. */
class PreferencesRepository(context: Context) {
    private val backing = context.getSharedPreferences("notimind_lite_prefs", Context.MODE_PRIVATE)

    private val _enableSync = MutableStateFlow(backing.getBoolean(KEY_ENABLE_SYNC, true))
    private val _enableVector = MutableStateFlow(backing.getBoolean(KEY_ENABLE_VECTOR, true))
    private val _enableFts4 = MutableStateFlow(backing.getBoolean(KEY_ENABLE_FTS4, true))
    private val _retentionDays = MutableStateFlow(
        backing.getInt(KEY_RETENTION_DAYS, if (BuildConfig.DEBUG) RETAIN_ALL_DAYS else DEFAULT_RETENTION_DAYS)
    )
    private val _exportEncryption = MutableStateFlow(backing.getBoolean(KEY_EXPORT_ENCRYPTION, true))
    private val _captureNotifications = MutableStateFlow(backing.getBoolean(KEY_CAPTURE_NOTIFICATIONS, true))

    val enableSync: StateFlow<Boolean> = _enableSync
    val enableVector: StateFlow<Boolean> = _enableVector
    val enableFts4: StateFlow<Boolean> = _enableFts4
    val retentionDays: StateFlow<Int> = _retentionDays
    val exportEncryption: StateFlow<Boolean> = _exportEncryption
    val captureNotifications: StateFlow<Boolean> = _captureNotifications

    private val _enableSemanticRanking = MutableStateFlow(backing.getBoolean(KEY_ENABLE_SEMANTIC_RANKING, true))
    private val _autoDeleteOnRead = MutableStateFlow(backing.getBoolean(KEY_AUTO_DELETE_ON_READ, false))
    private val _backupIntervalDays = MutableStateFlow(backing.getInt(KEY_BACKUP_INTERVAL_DAYS, 0))
    private val _anonymizeTitles = MutableStateFlow(backing.getBoolean(KEY_ANONYMIZE_TITLES, false))
    private val _maxCacheSizeMb = MutableStateFlow(backing.getInt(KEY_MAX_CACHE_MB, 50))
    private val _semanticWeight = MutableStateFlow(backing.getInt(KEY_SEMANTIC_WEIGHT, 50))

    val enableSemanticRanking: StateFlow<Boolean> = _enableSemanticRanking
    val autoDeleteOnRead: StateFlow<Boolean> = _autoDeleteOnRead
    val backupIntervalDays: StateFlow<Int> = _backupIntervalDays
    val anonymizeTitles: StateFlow<Boolean> = _anonymizeTitles
    val maxCacheSizeMb: StateFlow<Int> = _maxCacheSizeMb
    val semanticWeight: StateFlow<Int> = _semanticWeight

    private val _enableTelemetry = MutableStateFlow(backing.getBoolean(KEY_ENABLE_TELEMETRY, true))
    private val _telemetryLevel = MutableStateFlow(backing.getString(KEY_TELEMETRY_LEVEL, "minimal") ?: "minimal")
    private val _maxDbMb = MutableStateFlow(backing.getInt(KEY_MAX_DB_MB, 512))
    private val _lowMemoryMode = MutableStateFlow(backing.getBoolean(KEY_LOW_MEMORY_MODE, false))
    private val _vectorCacheMax = MutableStateFlow(backing.getInt(KEY_VECTOR_CACHE_MAX, 1000))
    val enableTelemetry: StateFlow<Boolean> = _enableTelemetry
    val telemetryLevel: StateFlow<String> = _telemetryLevel
    val maxDbMb: StateFlow<Int> = _maxDbMb
    val lowMemoryMode: StateFlow<Boolean> = _lowMemoryMode
    val vectorCacheMax: StateFlow<Int> = _vectorCacheMax

    private val _captureForegroundOnly = MutableStateFlow(backing.getBoolean("config_capture_foreground_only", false))
    private val _captureAttachments = MutableStateFlow(backing.getBoolean("config_capture_attachments", true))
    private val _captureOngoing = MutableStateFlow(backing.getBoolean("config_capture_ongoing", true))
    private val _capturePackageAllowlist = MutableStateFlow(backing.getString("config_capture_allowlist", "") ?: "")
    private val _capturePackageBlocklist = MutableStateFlow(backing.getString("config_capture_blocklist", "") ?: "")
    private val _minImportance = MutableStateFlow(backing.getInt("config_min_importance", 0))
    private val _captureActionsOnly = MutableStateFlow(backing.getBoolean("config_capture_actions_only", false))
    val captureForegroundOnly: StateFlow<Boolean> = _captureForegroundOnly
    val captureAttachments: StateFlow<Boolean> = _captureAttachments
    val captureOngoing: StateFlow<Boolean> = _captureOngoing
    val capturePackageAllowlist: StateFlow<String> = _capturePackageAllowlist
    val capturePackageBlocklist: StateFlow<String> = _capturePackageBlocklist
    val minImportance: StateFlow<Int> = _minImportance
    val captureActionsOnly: StateFlow<Boolean> = _captureActionsOnly

    private val _redactPii = MutableStateFlow(backing.getBoolean("config_redact_pii", true))
    private val _encryptedExports = MutableStateFlow(backing.getBoolean("config_encrypted_exports", true))
    private val _requirePassphrase = MutableStateFlow(backing.getBoolean("config_require_passphrase", false))
    private val _autoLockDb = MutableStateFlow(backing.getBoolean("config_auto_lock_db", false))
    val redactPii: StateFlow<Boolean> = _redactPii
    val encryptedExports: StateFlow<Boolean> = _encryptedExports
    val requirePassphrase: StateFlow<Boolean> = _requirePassphrase
    val autoLockDb: StateFlow<Boolean> = _autoLockDb

    private val _syncInterval = MutableStateFlow(backing.getInt("config_sync_interval_min", 360))
    private val _syncWifiOnly = MutableStateFlow(backing.getBoolean("config_sync_wifi_only", true))
    private val _syncChargingOnly = MutableStateFlow(backing.getBoolean("config_sync_charging_only", true))
    private val _lastSyncTs = MutableStateFlow(backing.getLong("config_last_sync_ts", 0L))
    val syncInterval: StateFlow<Int> = _syncInterval
    val syncWifiOnly: StateFlow<Boolean> = _syncWifiOnly
    val syncChargingOnly: StateFlow<Boolean> = _syncChargingOnly
    val lastSyncTs: StateFlow<Long> = _lastSyncTs

    fun setCaptureForegroundOnly(v: Boolean) { backing.edit().putBoolean("config_capture_foreground_only", v).apply(); _captureForegroundOnly.value = v }
    fun setCaptureAttachments(v: Boolean) { backing.edit().putBoolean("config_capture_attachments", v).apply(); _captureAttachments.value = v }
    fun setCaptureOngoing(v: Boolean) { backing.edit().putBoolean("config_capture_ongoing", v).apply(); _captureOngoing.value = v }
    fun setCapturePackageAllowlist(v: String) { backing.edit().putString("config_capture_allowlist", v).apply(); _capturePackageAllowlist.value = v }
    fun setCapturePackageBlocklist(v: String) { backing.edit().putString("config_capture_blocklist", v).apply(); _capturePackageBlocklist.value = v }
    fun setMinImportance(v: Int) { val n = v.coerceIn(0, 5); backing.edit().putInt("config_min_importance", n).apply(); _minImportance.value = n }
    fun setCaptureActionsOnly(v: Boolean) { backing.edit().putBoolean("config_capture_actions_only", v).apply(); _captureActionsOnly.value = v }
    fun setRedactPii(v: Boolean) { backing.edit().putBoolean("config_redact_pii", v).apply(); _redactPii.value = v }
    fun setEncryptedExports(v: Boolean) { backing.edit().putBoolean("config_encrypted_exports", v).apply(); _encryptedExports.value = v }
    fun setRequirePassphrase(v: Boolean) { backing.edit().putBoolean("config_require_passphrase", v).apply(); _requirePassphrase.value = v }
    fun setAutoLockDb(v: Boolean) { backing.edit().putBoolean("config_auto_lock_db", v).apply(); _autoLockDb.value = v }
    fun setSyncInterval(v: Int) { val n = v.coerceIn(15, 1440); backing.edit().putInt("config_sync_interval_min", n).apply(); _syncInterval.value = n }
    fun setSyncWifiOnly(v: Boolean) { backing.edit().putBoolean("config_sync_wifi_only", v).apply(); _syncWifiOnly.value = v }
    fun setSyncChargingOnly(v: Boolean) { backing.edit().putBoolean("config_sync_charging_only", v).apply(); _syncChargingOnly.value = v }
    fun setLastSyncTs(v: Long) { backing.edit().putLong("config_last_sync_ts", v).apply(); _lastSyncTs.value = v }
    fun setEnableSync(value: Boolean) { backing.edit().putBoolean(KEY_ENABLE_SYNC, value).apply(); _enableSync.value = value }
    fun setEnableVector(value: Boolean) { backing.edit().putBoolean(KEY_ENABLE_VECTOR, value).apply(); _enableVector.value = value }
    fun setEnableFts4(value: Boolean) { backing.edit().putBoolean(KEY_ENABLE_FTS4, value).apply(); _enableFts4.value = value }
    fun setRetentionDays(value: Int) { val safe = value.coerceIn(1, 3650); backing.edit().putInt(KEY_RETENTION_DAYS, safe).apply(); _retentionDays.value = safe }
    fun setExportEncryption(value: Boolean) { backing.edit().putBoolean(KEY_EXPORT_ENCRYPTION, value).apply(); _exportEncryption.value = value }
    fun setCaptureNotifications(value: Boolean) { backing.edit().putBoolean(KEY_CAPTURE_NOTIFICATIONS, value).apply(); _captureNotifications.value = value }
    fun setEnableSemanticRanking(value: Boolean) { backing.edit().putBoolean(KEY_ENABLE_SEMANTIC_RANKING, value).apply(); _enableSemanticRanking.value = value }
    fun setAutoDeleteOnRead(value: Boolean) {
        val safe = value && !BuildConfig.DEBUG
        backing.edit().putBoolean(KEY_AUTO_DELETE_ON_READ, safe).apply()
        _autoDeleteOnRead.value = safe
    }
    fun setBackupIntervalDays(value: Int) { val safe = value.coerceAtLeast(0); backing.edit().putInt(KEY_BACKUP_INTERVAL_DAYS, safe).apply(); _backupIntervalDays.value = safe }
    fun setAnonymizeTitles(value: Boolean) { backing.edit().putBoolean(KEY_ANONYMIZE_TITLES, value).apply(); _anonymizeTitles.value = value }
    fun setMaxCacheSizeMb(value: Int) { val safe = value.coerceIn(1, 1024); backing.edit().putInt(KEY_MAX_CACHE_MB, safe).apply(); _maxCacheSizeMb.value = safe }
    fun setSemanticWeight(value: Int) { val safe = value.coerceIn(0, 100); backing.edit().putInt(KEY_SEMANTIC_WEIGHT, safe).apply(); _semanticWeight.value = safe }
    fun setEnableTelemetry(value: Boolean) { backing.edit().putBoolean(KEY_ENABLE_TELEMETRY, value).apply(); _enableTelemetry.value = value }
    fun setTelemetryLevel(value: String) { backing.edit().putString(KEY_TELEMETRY_LEVEL, value).apply(); _telemetryLevel.value = value }
    fun setMaxDbMb(value: Int) { val safe = value.coerceIn(1, 4096); backing.edit().putInt(KEY_MAX_DB_MB, safe).apply(); _maxDbMb.value = safe }
    fun setLowMemoryMode(value: Boolean) { backing.edit().putBoolean(KEY_LOW_MEMORY_MODE, value).apply(); _lowMemoryMode.value = value; com.jeffers.notimindlite.util.VectorEmbeddingHelper.setLowMemoryMode(value) }
    fun setVectorCacheMax(value: Int) { val safe = value.coerceIn(1, 10000); backing.edit().putInt(KEY_VECTOR_CACHE_MAX, safe).apply(); _vectorCacheMax.value = safe; com.jeffers.notimindlite.util.VectorEmbeddingHelper.updateCacheSize(safe) }

    companion object {
        private const val DEFAULT_RETENTION_DAYS = 30
        private const val RETAIN_ALL_DAYS = 3650
        private const val KEY_ENABLE_SYNC = "config_enable_sync"
        private const val KEY_ENABLE_VECTOR = "config_enable_vector"
        private const val KEY_ENABLE_FTS4 = "config_enable_fts4"
        private const val KEY_RETENTION_DAYS = "config_retention_days"
        private const val KEY_EXPORT_ENCRYPTION = "config_export_encryption"
        private const val KEY_CAPTURE_NOTIFICATIONS = "config_capture_notifications"
        private const val KEY_ENABLE_SEMANTIC_RANKING = "config_enable_semantic_ranking"
        private const val KEY_AUTO_DELETE_ON_READ = "config_auto_delete_on_read"
        private const val KEY_BACKUP_INTERVAL_DAYS = "config_backup_interval_days"
        private const val KEY_ANONYMIZE_TITLES = "config_anonymize_titles"
        private const val KEY_MAX_CACHE_MB = "config_max_cache_mb"
        private const val KEY_SEMANTIC_WEIGHT = "config_semantic_weight"
        private const val KEY_ENABLE_TELEMETRY = "config_enable_telemetry"
        private const val KEY_TELEMETRY_LEVEL = "config_telemetry_level"
        private const val KEY_MAX_DB_MB = "config_max_db_mb"
        private const val KEY_LOW_MEMORY_MODE = "config_low_memory_mode"
        private const val KEY_VECTOR_CACHE_MAX = "config_vector_cache_max"
    }
}
