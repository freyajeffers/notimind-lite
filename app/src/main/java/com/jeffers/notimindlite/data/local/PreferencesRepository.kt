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

    fun setEnableSync(value: Boolean) { backing.edit().putBoolean(KEY_ENABLE_SYNC, value).apply(); _enableSync.value = value }
    fun setEnableVector(value: Boolean) { backing.edit().putBoolean(KEY_ENABLE_VECTOR, value).apply(); _enableVector.value = value }
    fun setEnableFts4(value: Boolean) { backing.edit().putBoolean(KEY_ENABLE_FTS4, value).apply(); _enableFts4.value = value }
    fun setRetentionDays(value: Int) { val safe = value.coerceIn(1, 3650); backing.edit().putInt(KEY_RETENTION_DAYS, safe).apply(); _retentionDays.value = safe }
    fun setExportEncryption(value: Boolean) { backing.edit().putBoolean(KEY_EXPORT_ENCRYPTION, value).apply(); _exportEncryption.value = value }
    fun setCaptureNotifications(value: Boolean) { backing.edit().putBoolean(KEY_CAPTURE_NOTIFICATIONS, value).apply(); _captureNotifications.value = value }
    fun setEnableSemanticRanking(value: Boolean) { backing.edit().putBoolean(KEY_ENABLE_SEMANTIC_RANKING, value).apply(); _enableSemanticRanking.value = value }
    fun setAutoDeleteOnRead(value: Boolean) { backing.edit().putBoolean(KEY_AUTO_DELETE_ON_READ, value).apply(); _autoDeleteOnRead.value = value }
    fun setBackupIntervalDays(value: Int) { val safe = value.coerceAtLeast(0); backing.edit().putInt(KEY_BACKUP_INTERVAL_DAYS, safe).apply(); _backupIntervalDays.value = safe }
    fun setAnonymizeTitles(value: Boolean) { backing.edit().putBoolean(KEY_ANONYMIZE_TITLES, value).apply(); _anonymizeTitles.value = value }
    fun setMaxCacheSizeMb(value: Int) { val safe = value.coerceIn(1, 1024); backing.edit().putInt(KEY_MAX_CACHE_MB, safe).apply(); _maxCacheSizeMb.value = safe }
    fun setSemanticWeight(value: Int) { val safe = value.coerceIn(0, 100); backing.edit().putInt(KEY_SEMANTIC_WEIGHT, safe).apply(); _semanticWeight.value = safe }

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
    }
}
