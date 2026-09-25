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

    fun setEnableSync(value: Boolean) { backing.edit().putBoolean(KEY_ENABLE_SYNC, value).apply(); _enableSync.value = value }
    fun setEnableVector(value: Boolean) { backing.edit().putBoolean(KEY_ENABLE_VECTOR, value).apply(); _enableVector.value = value }
    fun setEnableFts4(value: Boolean) { backing.edit().putBoolean(KEY_ENABLE_FTS4, value).apply(); _enableFts4.value = value }
    fun setRetentionDays(value: Int) { val safe = value.coerceIn(1, 3650); backing.edit().putInt(KEY_RETENTION_DAYS, safe).apply(); _retentionDays.value = safe }
    fun setExportEncryption(value: Boolean) { backing.edit().putBoolean(KEY_EXPORT_ENCRYPTION, value).apply(); _exportEncryption.value = value }
    fun setCaptureNotifications(value: Boolean) { backing.edit().putBoolean(KEY_CAPTURE_NOTIFICATIONS, value).apply(); _captureNotifications.value = value }

    companion object {
        private const val DEFAULT_RETENTION_DAYS = 30
        private const val RETAIN_ALL_DAYS = 3650
        private const val KEY_ENABLE_SYNC = "config_enable_sync"
        private const val KEY_ENABLE_VECTOR = "config_enable_vector"
        private const val KEY_ENABLE_FTS4 = "config_enable_fts4"
        private const val KEY_RETENTION_DAYS = "config_retention_days"
        private const val KEY_EXPORT_ENCRYPTION = "config_export_encryption"
        private const val KEY_CAPTURE_NOTIFICATIONS = "config_capture_notifications"
    }
}
