package com.jeffers.notimindlite.data.local

import android.content.Context
import androidx.core.content.edit

/**
 * ExhaustivePreferencesRepository: additive repository implementing the full catalog of
 * user-facing options. Kept as a separate class to avoid overwriting the existing
 * PreferencesRepository until reviewed.
 */
class ExhaustivePreferencesRepository(
    context: Context
) {
    private val appContext = context.applicationContext

    /** Initialize the active profile's store, not the legacy unscoped store. */
    fun initializeDefaults(profileId: String = "default") {
        val backing = appContext.getSharedPreferences("notimind_lite_prefs_$profileId", Context.MODE_PRIVATE)
        backing.edit {
            ExhaustivePreferencesDefaults.applyCoreDefaults(backing, this)
            ExhaustivePreferencesDefaults.applyDisplayDefaults(backing, this)
            ExhaustivePreferencesDefaults.applyActionDefaults(backing, this)
            ExhaustivePreferencesDefaults.applyAutomationDefaults(backing, this)
            ExhaustivePreferencesDefaults.applySecurityDefaults(backing, this)
            ExhaustivePreferencesDefaults.applyPerformanceDefaults(backing, this)
            ExhaustivePreferencesDefaults.applyDevDefaults(backing, this)
            ExhaustivePreferencesDefaults.applyAdvancedDefaults(backing, this)
        }
    }

    // Helpers moved to ExhaustivePreferencesDefaults to reduce class size and satisfy detekt thresholds.

    companion object {
        internal const val DEFAULT_RETENTION_DAYS = 30
        internal const val RETAIN_ALL_DAYS = 3650

        // Capture & filtering
        internal const val KEY_ENABLE_SYNC = "config_enable_sync"
        internal const val KEY_ENABLE_VECTOR = "config_enable_vector"
        internal const val KEY_ENABLE_FTS4 = "config_enable_fts4"
        internal const val KEY_CAPTURE_NOTIFICATIONS = "config_capture_notifications"
        internal const val KEY_CAPTURE_FOREGROUND_ONLY = "config_capture_foreground_only"
        internal const val KEY_CAPTURE_ATTACHMENTS = "config_capture_attachments"
        internal const val KEY_CAPTURE_ONGOING = "config_capture_ongoing"
        internal const val KEY_CAPTURE_PACKAGE_ALLOWLIST = "config_capture_package_allowlist"
        internal const val KEY_CAPTURE_PACKAGE_BLOCKLIST = "config_capture_package_blocklist"
        internal const val KEY_MIN_IMPORTANCE = "config_min_importance"
        internal const val KEY_CAPTURE_ACTIONS_ONLY = "config_capture_actions_only"

        // Storage & retention
        internal const val KEY_RETENTION_DAYS = "config_retention_days"
        internal const val KEY_RETAIN_DISMISSED = "config_retain_dismissed"
        internal const val KEY_MAX_DB_MB = "config_max_db_mb"
        internal const val KEY_ARCHIVE_DAYS = "config_archive_days"
        internal const val KEY_INDEX_AFTER_DELETE = "config_index_after_delete"
        internal const val KEY_DB_CHECKPOINT_HOURS = "config_db_checkpoint_hours"
        internal const val KEY_AUTO_DELETE_ON_READ = "config_auto_delete_on_read"

        // Search & ranking
        internal const val KEY_ENABLE_SEMANTIC_RANKING = "config_enable_semantic_ranking"
        internal const val KEY_VECTOR_MODEL = "config_vector_model"
        internal const val KEY_VECTOR_DIM = "config_vector_dim"
        internal const val KEY_SEARCH_WEIGHT = "config_search_weight"
        internal const val KEY_RRF_K = "config_rrf_k"
        internal const val KEY_SEMANTIC_THRESHOLD = "config_semantic_threshold"
        internal const val KEY_QUERY_SPELLING = "config_query_spelling"
        internal const val KEY_SEARCH_HISTORY = "config_search_history"

        // Sync & cloud
        internal const val KEY_SYNC_INTERVAL_MIN = "config_sync_interval_min"
        internal const val KEY_SYNC_WIFI_ONLY = "config_sync_wifi_only"
        internal const val KEY_SYNC_CHARGING_ONLY = "config_sync_charging_only"
        internal const val KEY_SYNC_CONFLICT_POLICY = "config_sync_conflict_policy"
        internal const val KEY_LAST_SYNC_TS = "config_last_sync_ts"
        internal const val KEY_SYNC_BANDWIDTH_KBPS = "config_sync_bandwidth_kbps"
        internal const val KEY_SYNC_BIDIRECTIONAL = "config_sync_bidirectional"

        // Backup & export
        internal const val KEY_EXPORT_ENCRYPTION = "config_export_encryption"
        internal const val KEY_BACKUP_INTERVAL_DAYS = "config_backup_interval_days"
        internal const val KEY_BACKUP_TARGET = "config_backup_target"
        internal const val KEY_EXPORT_FORMATS = "config_export_formats"
        internal const val KEY_EXPORT_INCLUDE_ATTACHMENTS = "config_export_include_attachments"
        internal const val KEY_BACKUP_CIPHER = "config_backup_cipher"
        internal const val KEY_BACKUP_KEY_MODE = "config_backup_key_mode"
        internal const val KEY_AUTO_RESTORE = "config_auto_restore"
        internal const val KEY_EXPORT_ANONYMIZE = "config_export_anonymize"

        // Privacy & redaction
        internal const val KEY_PII_REDACTION = "config_pii_redaction"
        internal const val KEY_PII_REDACTION_LEVEL = "config_pii_redaction_level"
        internal const val KEY_STRICT_PRIVACY = "config_strict_privacy"
        internal const val KEY_ANONYMIZE_APP_NAMES = "config_anonymize_app_names"
        internal const val KEY_REDACT_PHONES = "config_redact_phones"
        internal const val KEY_REDACT_EMAILS = "config_redact_emails"
        internal const val KEY_REDACT_CARDS = "config_redact_cards"
        internal const val KEY_RAW_HOLD_DAYS = "config_raw_hold_days"
        internal const val KEY_EXPORT_PII_CONFIRM = "config_export_pii_confirm"

        // Display & UX
        // Display prefs delegated to PreferencesRepository; keep keys for historical compatibility only.
        // See PreferencesRepository for live-sourced MutableStateFlow and setters.
        internal const val KEY_SHOW_APP_ICONS = "config_show_app_icons"
        internal const val KEY_COMPACT_MODE = "config_compact_mode"
        internal const val KEY_SORT_ORDER = "config_sort_order"
        internal const val KEY_GROUP_BY_APP = "config_group_by_app"
        internal const val KEY_SHOW_DISMISSED_BADGE = "config_show_dismissed_badge"
        internal const val KEY_PREVIEW_LENGTH = "config_preview_length"
        internal const val KEY_THEME_ACCENT = "config_theme_accent"
        internal const val KEY_DYNAMIC_THEME = "config_dynamic_theme"

        // Actions & intent
        internal const val KEY_CONFIRM_EXTERNAL_INTENT = "config_confirm_external_intent"
        internal const val KEY_OPEN_IN_APP = "config_open_in_app"
        internal const val KEY_AUTO_ACT_ON_NOTIFICATION = "config_auto_act_on_notification"
        internal const val KEY_DEFAULT_REPLY_METHOD = "config_default_reply_method"
        internal const val KEY_LONG_PRESS_ACTION = "config_long_press_action"

        // Automation
        internal const val KEY_CLEANUP_HOUR = "config_cleanup_hour"
        internal const val KEY_DB_COMPACT_DAYS = "config_db_compact_days"
        internal const val KEY_REINDEX_DAYS = "config_reindex_days"
        internal const val KEY_AUTO_REEMBED = "config_auto_reembed"
        internal const val KEY_EXPORT_CRON = "config_export_cron"

        // Security
        internal const val KEY_USE_KEYSTORE = "config_use_keystore"
        internal const val KEY_DB_ENCRYPTED = "config_db_encrypted"
        internal const val KEY_DB_KEY_MODE = "config_db_key_mode"
        internal const val KEY_APP_LOCK_ENABLED = "config_app_lock_enabled"
        internal const val KEY_EXPORT_REQUIRES_BIOMETRIC = "config_export_requires_biometric"

        // Performance
        internal const val KEY_VECTOR_CACHE_MAX = "config_vector_cache_max"
        internal const val KEY_EMBEDDING_RATE_MS = "config_embedding_rate_ms"
        internal const val KEY_THREAD_POOL_SIZE = "config_thread_pool_size"
        internal const val KEY_LOW_MEMORY_MODE = "config_low_memory_mode"
        internal const val KEY_ICON_CACHE_SIZE = "config_icon_cache_size"

        // Dev
        internal const val KEY_ENABLE_TELEMETRY = "config_enable_telemetry"
        internal const val KEY_TELEMETRY_LEVEL = "config_telemetry_level"
        internal const val KEY_FEATURE_FLAGS_URL = "config_feature_flags_url"
        internal const val KEY_MOCK_DATA_ENABLED = "config_mock_data_enabled"
        internal const val KEY_TEST_MODE_FLAGS = "config_test_mode_flags"
        internal const val KEY_LOG_LEVEL = "config_log_level"
        internal const val KEY_CRASH_REPORTING = "config_crash_reporting"

        // Advanced
        internal const val KEY_KEY_DERIVATION = "config_key_derivation"
        internal const val KEY_USE_FTS5 = "config_use_fts5"
        internal const val KEY_OFFLOAD_EMBEDDINGS = "config_offload_embeddings"
        internal const val KEY_ACTIVE_PROFILE = "config_active_profile"
        internal const val KEY_VECTOR_GPU = "config_vector_gpu"
        internal const val KEY_AUDIT_SAMPLING_PCT = "config_audit_sampling_pct"
    }
}
