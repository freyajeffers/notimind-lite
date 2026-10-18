package com.jeffers.notimindlite.data.local

import android.content.SharedPreferences
import com.jeffers.notimindlite.BuildConfig

// Extracted helpers so ExhaustivePreferencesRepository stays below detekt thresholds.
private const val MAX_DATABASE_SIZE_MB = 512
private const val CHECKPOINT_INTERVAL_HOURS = 24
private const val VECTOR_DIMENSIONS = 128
private const val SEARCH_WEIGHT_PERCENT = 50
private const val RRF_DEFAULT_K = 60
private const val SYNC_INTERVAL_MINUTES = 360
private const val CLEANUP_HOUR = 3
private const val COMPACT_DAYS = 30
private const val REINDEX_DAYS = 7
private const val VECTOR_CACHE_MAX_ENTRIES = 1000
private const val EMBEDDING_RATE_MILLISECONDS = 100
private const val THREAD_POOL_SIZE = 2
private const val AUDIT_SAMPLING_PERCENT = 100
private const val PREVIEW_LENGTH = 240
private const val ICON_CACHE_SIZE = 128

private fun putIfAbsentBoolean(
    backing: SharedPreferences,
    edit: SharedPreferences.Editor,
    key: String,
    value: Boolean
) {
    if (!backing.contains(key)) edit.putBoolean(key, value)
}

private fun putIfAbsentInt(backing: SharedPreferences, edit: SharedPreferences.Editor, key: String, value: Int) {
    if (!backing.contains(key)) edit.putInt(key, value)
}

private fun putIfAbsentText(backing: SharedPreferences, edit: SharedPreferences.Editor, key: String, value: String) {
    if (!backing.contains(key)) edit.putString(key, value)
}

internal object ExhaustivePreferencesDefaults {
    fun applyCoreDefaults(backing: SharedPreferences, edit: SharedPreferences.Editor) {
        applyCaptureDefaults(backing, edit)
        putIfAbsentInt(
            backing,
            edit,
            ExhaustivePreferencesRepository.KEY_RETENTION_DAYS,
            if (BuildConfig.DEBUG) ExhaustivePreferencesRepository.RETAIN_ALL_DAYS
            else ExhaustivePreferencesRepository.DEFAULT_RETENTION_DAYS
        )
        putIfAbsentBoolean(backing, edit, ExhaustivePreferencesRepository.KEY_RETAIN_DISMISSED, true)
        putIfAbsentInt(backing, edit, ExhaustivePreferencesRepository.KEY_MAX_DB_MB, MAX_DATABASE_SIZE_MB)
        putIfAbsentInt(backing, edit, ExhaustivePreferencesRepository.KEY_ARCHIVE_DAYS, 0)
        putIfAbsentBoolean(backing, edit, ExhaustivePreferencesRepository.KEY_INDEX_AFTER_DELETE, true)
        putIfAbsentInt(
            backing,
            edit,
            ExhaustivePreferencesRepository.KEY_DB_CHECKPOINT_HOURS,
            CHECKPOINT_INTERVAL_HOURS
        )
        putIfAbsentBoolean(backing, edit, ExhaustivePreferencesRepository.KEY_AUTO_DELETE_ON_READ, false)
        putIfAbsentBoolean(backing, edit, ExhaustivePreferencesRepository.KEY_ENABLE_SEMANTIC_RANKING, true)
        putIfAbsentText(backing, edit, ExhaustivePreferencesRepository.KEY_VECTOR_MODEL, "local")
        putIfAbsentInt(backing, edit, ExhaustivePreferencesRepository.KEY_VECTOR_DIM, VECTOR_DIMENSIONS)
        putIfAbsentInt(backing, edit, ExhaustivePreferencesRepository.KEY_SEARCH_WEIGHT, SEARCH_WEIGHT_PERCENT)
        putIfAbsentInt(backing, edit, ExhaustivePreferencesRepository.KEY_RRF_K, RRF_DEFAULT_K)
        putIfAbsentText(backing, edit, ExhaustivePreferencesRepository.KEY_SEMANTIC_THRESHOLD, "0.35")
        putIfAbsentBoolean(backing, edit, ExhaustivePreferencesRepository.KEY_QUERY_SPELLING, true)
        putIfAbsentBoolean(backing, edit, ExhaustivePreferencesRepository.KEY_SEARCH_HISTORY, true)
        putIfAbsentInt(backing, edit, ExhaustivePreferencesRepository.KEY_SYNC_INTERVAL_MIN, SYNC_INTERVAL_MINUTES)
        putIfAbsentBoolean(backing, edit, ExhaustivePreferencesRepository.KEY_SYNC_WIFI_ONLY, true)
        putIfAbsentBoolean(backing, edit, ExhaustivePreferencesRepository.KEY_SYNC_CHARGING_ONLY, false)
        putIfAbsentText(backing, edit, ExhaustivePreferencesRepository.KEY_SYNC_CONFLICT_POLICY, "newest")
        if (!backing.contains(ExhaustivePreferencesRepository.KEY_LAST_SYNC_TS)) {
            edit.putLong(ExhaustivePreferencesRepository.KEY_LAST_SYNC_TS, 0L)
        }
        putIfAbsentInt(backing, edit, ExhaustivePreferencesRepository.KEY_SYNC_BANDWIDTH_KBPS, 0)
        putIfAbsentBoolean(backing, edit, ExhaustivePreferencesRepository.KEY_SYNC_BIDIRECTIONAL, true)
        putIfAbsentBoolean(backing, edit, ExhaustivePreferencesRepository.KEY_EXPORT_ENCRYPTION, true)
        putIfAbsentInt(backing, edit, ExhaustivePreferencesRepository.KEY_BACKUP_INTERVAL_DAYS, 0)
        putIfAbsentText(backing, edit, ExhaustivePreferencesRepository.KEY_BACKUP_TARGET, "app-storage")
        putIfAbsentText(backing, edit, ExhaustivePreferencesRepository.KEY_EXPORT_FORMATS, "json,csv")
        putIfAbsentBoolean(backing, edit, ExhaustivePreferencesRepository.KEY_EXPORT_INCLUDE_ATTACHMENTS, false)
        putIfAbsentText(backing, edit, ExhaustivePreferencesRepository.KEY_BACKUP_CIPHER, "AES-GCM")
        putIfAbsentText(backing, edit, ExhaustivePreferencesRepository.KEY_BACKUP_KEY_MODE, "keystore")
        putIfAbsentBoolean(backing, edit, ExhaustivePreferencesRepository.KEY_AUTO_RESTORE, false)
        putIfAbsentBoolean(backing, edit, ExhaustivePreferencesRepository.KEY_EXPORT_ANONYMIZE, false)
        putIfAbsentBoolean(backing, edit, ExhaustivePreferencesRepository.KEY_PII_REDACTION, false)
        putIfAbsentText(backing, edit, ExhaustivePreferencesRepository.KEY_PII_REDACTION_LEVEL, "standard")
        putIfAbsentBoolean(backing, edit, ExhaustivePreferencesRepository.KEY_STRICT_PRIVACY, false)
        putIfAbsentBoolean(backing, edit, ExhaustivePreferencesRepository.KEY_ANONYMIZE_APP_NAMES, false)
        putIfAbsentBoolean(backing, edit, ExhaustivePreferencesRepository.KEY_REDACT_PHONES, true)
        putIfAbsentBoolean(backing, edit, ExhaustivePreferencesRepository.KEY_REDACT_EMAILS, true)
        putIfAbsentBoolean(backing, edit, ExhaustivePreferencesRepository.KEY_REDACT_CARDS, true)
        putIfAbsentInt(backing, edit, ExhaustivePreferencesRepository.KEY_RAW_HOLD_DAYS, 0)
        putIfAbsentBoolean(backing, edit, ExhaustivePreferencesRepository.KEY_EXPORT_PII_CONFIRM, true)
    }

    private fun applyCaptureDefaults(backing: SharedPreferences, edit: SharedPreferences.Editor) {
        putIfAbsentBoolean(backing, edit, ExhaustivePreferencesRepository.KEY_ENABLE_SYNC, true)
        putIfAbsentBoolean(backing, edit, ExhaustivePreferencesRepository.KEY_ENABLE_VECTOR, true)
        putIfAbsentBoolean(backing, edit, ExhaustivePreferencesRepository.KEY_ENABLE_FTS4, true)
        putIfAbsentBoolean(backing, edit, ExhaustivePreferencesRepository.KEY_CAPTURE_NOTIFICATIONS, true)
        putIfAbsentBoolean(backing, edit, ExhaustivePreferencesRepository.KEY_CAPTURE_FOREGROUND_ONLY, false)
        putIfAbsentBoolean(backing, edit, ExhaustivePreferencesRepository.KEY_CAPTURE_ATTACHMENTS, true)
        putIfAbsentBoolean(backing, edit, ExhaustivePreferencesRepository.KEY_CAPTURE_ONGOING, true)
        putIfAbsentText(backing, edit, ExhaustivePreferencesRepository.KEY_CAPTURE_PACKAGE_ALLOWLIST, "")
        putIfAbsentText(backing, edit, ExhaustivePreferencesRepository.KEY_CAPTURE_PACKAGE_BLOCKLIST, "")
        putIfAbsentInt(backing, edit, ExhaustivePreferencesRepository.KEY_MIN_IMPORTANCE, 0)
        putIfAbsentBoolean(backing, edit, ExhaustivePreferencesRepository.KEY_CAPTURE_ACTIONS_ONLY, false)
    }


    fun applyDisplayDefaults(backing: SharedPreferences, edit: SharedPreferences.Editor) {
        putIfAbsentBoolean(backing, edit, ExhaustivePreferencesRepository.KEY_SHOW_APP_ICONS, true)
        putIfAbsentBoolean(backing, edit, ExhaustivePreferencesRepository.KEY_COMPACT_MODE, false)
        putIfAbsentText(backing, edit, ExhaustivePreferencesRepository.KEY_SORT_ORDER, "newest")
        putIfAbsentBoolean(backing, edit, ExhaustivePreferencesRepository.KEY_GROUP_BY_APP, false)
        putIfAbsentBoolean(backing, edit, ExhaustivePreferencesRepository.KEY_SHOW_DISMISSED_BADGE, true)
        putIfAbsentInt(backing, edit, ExhaustivePreferencesRepository.KEY_PREVIEW_LENGTH, PREVIEW_LENGTH)
        putIfAbsentText(backing, edit, ExhaustivePreferencesRepository.KEY_THEME_ACCENT, "default")
        putIfAbsentBoolean(backing, edit, ExhaustivePreferencesRepository.KEY_DYNAMIC_THEME, true)
    }

    fun applyActionDefaults(backing: SharedPreferences, edit: SharedPreferences.Editor) {
        putIfAbsentBoolean(backing, edit, ExhaustivePreferencesRepository.KEY_CONFIRM_EXTERNAL_INTENT, true)
        putIfAbsentBoolean(backing, edit, ExhaustivePreferencesRepository.KEY_OPEN_IN_APP, false)
        putIfAbsentBoolean(backing, edit, ExhaustivePreferencesRepository.KEY_AUTO_ACT_ON_NOTIFICATION, false)
        putIfAbsentText(backing, edit, ExhaustivePreferencesRepository.KEY_DEFAULT_REPLY_METHOD, "system")
        putIfAbsentText(backing, edit, ExhaustivePreferencesRepository.KEY_LONG_PRESS_ACTION, "menu")
    }

    fun applyAutomationDefaults(backing: SharedPreferences, edit: SharedPreferences.Editor) {
        putIfAbsentInt(backing, edit, ExhaustivePreferencesRepository.KEY_CLEANUP_HOUR, CLEANUP_HOUR)
        putIfAbsentInt(backing, edit, ExhaustivePreferencesRepository.KEY_DB_COMPACT_DAYS, COMPACT_DAYS)
        putIfAbsentInt(backing, edit, ExhaustivePreferencesRepository.KEY_REINDEX_DAYS, REINDEX_DAYS)
        putIfAbsentBoolean(backing, edit, ExhaustivePreferencesRepository.KEY_AUTO_REEMBED, false)
        putIfAbsentText(backing, edit, ExhaustivePreferencesRepository.KEY_EXPORT_CRON, "")
    }

    fun applySecurityDefaults(backing: SharedPreferences, edit: SharedPreferences.Editor) {
        putIfAbsentBoolean(backing, edit, ExhaustivePreferencesRepository.KEY_USE_KEYSTORE, true)
        putIfAbsentBoolean(backing, edit, ExhaustivePreferencesRepository.KEY_DB_ENCRYPTED, true)
        putIfAbsentText(backing, edit, ExhaustivePreferencesRepository.KEY_DB_KEY_MODE, "keystore")
        putIfAbsentBoolean(backing, edit, ExhaustivePreferencesRepository.KEY_APP_LOCK_ENABLED, false)
        putIfAbsentBoolean(backing, edit, ExhaustivePreferencesRepository.KEY_EXPORT_REQUIRES_BIOMETRIC, false)
    }

    fun applyPerformanceDefaults(backing: SharedPreferences, edit: SharedPreferences.Editor) {
        putIfAbsentInt(backing, edit, ExhaustivePreferencesRepository.KEY_VECTOR_CACHE_MAX, VECTOR_CACHE_MAX_ENTRIES)
        putIfAbsentInt(
            backing,
            edit,
            ExhaustivePreferencesRepository.KEY_EMBEDDING_RATE_MS,
            EMBEDDING_RATE_MILLISECONDS
        )
        putIfAbsentInt(backing, edit, ExhaustivePreferencesRepository.KEY_THREAD_POOL_SIZE, THREAD_POOL_SIZE)
        putIfAbsentBoolean(backing, edit, ExhaustivePreferencesRepository.KEY_LOW_MEMORY_MODE, false)
        putIfAbsentInt(backing, edit, ExhaustivePreferencesRepository.KEY_ICON_CACHE_SIZE, ICON_CACHE_SIZE)
    }

    fun applyDevDefaults(backing: SharedPreferences, edit: SharedPreferences.Editor) {
        putIfAbsentBoolean(backing, edit, ExhaustivePreferencesRepository.KEY_ENABLE_TELEMETRY, true)
        putIfAbsentText(backing, edit, ExhaustivePreferencesRepository.KEY_TELEMETRY_LEVEL, "minimal")
        putIfAbsentText(backing, edit, ExhaustivePreferencesRepository.KEY_FEATURE_FLAGS_URL, "")
        putIfAbsentBoolean(backing, edit, ExhaustivePreferencesRepository.KEY_MOCK_DATA_ENABLED, false)
        putIfAbsentText(backing, edit, ExhaustivePreferencesRepository.KEY_TEST_MODE_FLAGS, "")
        putIfAbsentText(backing, edit, ExhaustivePreferencesRepository.KEY_LOG_LEVEL, "info")
        putIfAbsentBoolean(backing, edit, ExhaustivePreferencesRepository.KEY_CRASH_REPORTING, true)
    }

    fun applyAdvancedDefaults(backing: SharedPreferences, edit: SharedPreferences.Editor) {
        putIfAbsentText(backing, edit, ExhaustivePreferencesRepository.KEY_KEY_DERIVATION, "HKDF-SHA256")
        putIfAbsentBoolean(backing, edit, ExhaustivePreferencesRepository.KEY_USE_FTS5, false)
        putIfAbsentBoolean(backing, edit, ExhaustivePreferencesRepository.KEY_OFFLOAD_EMBEDDINGS, false)
        putIfAbsentText(backing, edit, ExhaustivePreferencesRepository.KEY_ACTIVE_PROFILE, "default")
        putIfAbsentBoolean(backing, edit, ExhaustivePreferencesRepository.KEY_VECTOR_GPU, false)
        putIfAbsentInt(backing, edit, ExhaustivePreferencesRepository.KEY_AUDIT_SAMPLING_PCT, AUDIT_SAMPLING_PERCENT)
    }
}
