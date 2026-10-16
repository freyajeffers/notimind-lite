package com.jeffers.notimindlite.data.local

import android.content.Context
import com.jeffers.notimindlite.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

data class AutoExecuteRule(
    val id: String = java.util.UUID.randomUUID().toString(),
    val packageName: String = "",
    val titleContains: String = "",
    val actionIndex: Int = 0,
    val enabled: Boolean = true
)

/** User-facing runtime options persisted in the existing direct-boot-safe preference store. */
class PreferencesRepository(context: Context) :
    PreferencesPerformanceOperations by PreferencesPerformanceOperationsImpl(context) {
    private val appContext = context.applicationContext
    private val deContext = appContext.createDeviceProtectedStorageContext()
    val profileManager = ProfilePreferencesManager(context)
    val sharedSearchManager = SharedSearchStateManager(context, profileManager)
    val automationManager = AutomationPreferencesManager(context, profileManager)
    val captureManager = CapturePreferencesManager(context, profileManager)
    val syncManager = SyncPreferencesManager(context, profileManager)
    val experimentalManager = ExperimentalPreferencesManager(context, profileManager)
    private val backing: android.content.SharedPreferences
        get() = deContext.getSharedPreferences(profileManager.activePreferencesName, Context.MODE_PRIVATE)

    init {
        migrateRenamedKeys()
        ExhaustivePreferencesRepository(context).initializeDefaults(profileManager.activeProfileId.value)
        profileManager.onProfileChanged = {
            reloadProfileFlows()
            sharedSearchManager.reload()
            automationManager.reload()
            captureManager.reload()
            syncManager.reload()
            experimentalManager.reload()
        }
    }

    private fun migrateRenamedKeys() {
        val editor = backing.edit()
        val aliases = mapOf(
            "config_capture_allowlist" to "config_capture_package_allowlist",
            "config_capture_blocklist" to "config_capture_package_blocklist",
            "config_redact_pii" to "config_pii_redaction"
        )
        aliases.forEach { (oldKey, newKey) ->
            if (!backing.contains(newKey) && backing.contains(oldKey)) {
                when (val value = backing.all[oldKey]) {
                    is Boolean -> editor.putBoolean(newKey, value)
                    is Int -> editor.putInt(newKey, value)
                    is Long -> editor.putLong(newKey, value)
                    is String -> editor.putString(newKey, value)
                }
            }
        }
        editor.apply()
    }


    private val _enableSync = MutableStateFlow(backing.getBoolean(KEY_ENABLE_SYNC, true))
    private val _enableVector = MutableStateFlow(backing.getBoolean(KEY_ENABLE_VECTOR, true))
    private val _enableFts4 = MutableStateFlow(backing.getBoolean(KEY_ENABLE_FTS4, true))
    private val _retentionDays = MutableStateFlow(
        backing.getInt(KEY_RETENTION_DAYS, if (BuildConfig.DEBUG) RETAIN_ALL_DAYS else DEFAULT_RETENTION_DAYS)
    )
    private val _exportEncryption = MutableStateFlow(backing.getBoolean(KEY_EXPORT_ENCRYPTION, true))


    val enableSync: StateFlow<Boolean> = _enableSync
    val enableVector: StateFlow<Boolean> = _enableVector
    val enableFts4: StateFlow<Boolean> = _enableFts4
    val retentionDays: StateFlow<Int> = _retentionDays
    val exportEncryption: StateFlow<Boolean> = _exportEncryption


    private val _enableSemanticRanking = MutableStateFlow(backing.getBoolean(KEY_ENABLE_SEMANTIC_RANKING, true))
    private val _autoDeleteOnRead = MutableStateFlow(backing.getBoolean(KEY_AUTO_DELETE_ON_READ, false))
    private val _backupIntervalDays = MutableStateFlow(backing.getInt(KEY_BACKUP_INTERVAL_DAYS, 0))
    private val _exportFormats = MutableStateFlow(backing.getString("config_export_formats", "json,csv") ?: "json,csv")
    private val _exportAnonymize = MutableStateFlow(backing.getBoolean("config_export_anonymize", false))
    private val _anonymizeTitles = MutableStateFlow(backing.getBoolean(KEY_ANONYMIZE_TITLES, false))
    private val _maxCacheSizeMb = MutableStateFlow(backing.getInt(KEY_MAX_CACHE_MB, 50))
    private val _semanticWeight = MutableStateFlow(backing.getInt(KEY_SEMANTIC_WEIGHT, 50))
    private val _compactMode = MutableStateFlow(backing.getBoolean(KEY_COMPACT_MODE, false))
    private val _showAppIcons = MutableStateFlow(backing.getBoolean(KEY_SHOW_APP_ICONS, true))
    private val _sortOrder = MutableStateFlow(backing.getString(KEY_SORT_ORDER, "newest") ?: "newest")
    private val _groupByApp = MutableStateFlow(backing.getBoolean(KEY_GROUP_BY_APP, true))
    private val _previewLength = MutableStateFlow(backing.getInt(KEY_PREVIEW_LENGTH, 140))
    private val _themeAccent = MutableStateFlow(backing.getString(KEY_THEME_ACCENT, "system") ?: "system")

    val enableSemanticRanking: StateFlow<Boolean> = _enableSemanticRanking
    val autoDeleteOnRead: StateFlow<Boolean> = _autoDeleteOnRead
    val backupIntervalDays: StateFlow<Int> = _backupIntervalDays
    val exportFormats: StateFlow<String> = _exportFormats
    val exportAnonymize: StateFlow<Boolean> = _exportAnonymize
    val anonymizeTitles: StateFlow<Boolean> = _anonymizeTitles
    val maxCacheSizeMb: StateFlow<Int> = _maxCacheSizeMb
    val semanticWeight: StateFlow<Int> = _semanticWeight
    val compactMode: StateFlow<Boolean> = _compactMode
    val showAppIcons: StateFlow<Boolean> = _showAppIcons
    val sortOrder: StateFlow<String> = _sortOrder
    val groupByApp: StateFlow<Boolean> = _groupByApp
    val previewLength: StateFlow<Int> = _previewLength
    val themeAccent: StateFlow<String> = _themeAccent

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

    // Experimental options are deliberately opt-in and remain profile scoped.

    private val _redactPii = MutableStateFlow(
        backing.getBoolean("config_pii_redaction", backing.getBoolean("config_redact_pii", false))
    )
    private val _encryptedExports = MutableStateFlow(backing.getBoolean("config_encrypted_exports", true))
    private val _requirePassphrase = MutableStateFlow(backing.getBoolean("config_require_passphrase", false))
    private val _autoLockDb = MutableStateFlow(backing.getBoolean("config_auto_lock_db", false))
    val redactPii: StateFlow<Boolean> = _redactPii
    val encryptedExports: StateFlow<Boolean> = _encryptedExports
    val requirePassphrase: StateFlow<Boolean> = _requirePassphrase
    val autoLockDb: StateFlow<Boolean> = _autoLockDb

    /** Security controls are kept in the same store so they are available before the UI starts. */
    private val _exportRequiresBiometric = MutableStateFlow(backing.getBoolean(KEY_EXPORT_REQUIRES_BIOMETRIC, false))
    private val _appLockEnabled = MutableStateFlow(backing.getBoolean(KEY_APP_LOCK_ENABLED, false))
    private val _useKeystore = MutableStateFlow(backing.getBoolean(KEY_USE_KEYSTORE, true))
    private val _dbEncrypted = MutableStateFlow(backing.getBoolean(KEY_DB_ENCRYPTED, true))
    private val _dbEncryptionMode = MutableStateFlow(
        DbEncryptionMode.fromPersisted(backing.getString(KEY_DB_ENCRYPTION_MODE, DbEncryptionMode.KEYSTORE.persisted))
    )
    val exportRequiresBiometric: StateFlow<Boolean> = _exportRequiresBiometric
    val appLockEnabled: StateFlow<Boolean> = _appLockEnabled
    val useKeystore: StateFlow<Boolean> = _useKeystore
    val dbEncrypted: StateFlow<Boolean> = _dbEncrypted
    val dbEncryptionMode: StateFlow<DbEncryptionMode> = _dbEncryptionMode


    private val _autoActOnNotification = MutableStateFlow(
        backing.getBoolean(KEY_AUTO_ACT_ON_NOTIFICATION, false)
    )
    private val _defaultReplyMethod = MutableStateFlow(
        backing.getString(KEY_DEFAULT_REPLY_METHOD, "inline") ?: "inline"
    )
    private val _longPressAction = MutableStateFlow(
        backing.getString(KEY_LONG_PRESS_ACTION, "open") ?: "open"
    )

    val autoActOnNotification: StateFlow<Boolean> = _autoActOnNotification
    val defaultReplyMethod: StateFlow<String> = _defaultReplyMethod
    val longPressAction: StateFlow<String> = _longPressAction
    val autoExecuteRules: StateFlow<List<AutoExecuteRule>> = automationManager.rules



    private fun reloadProfileFlows() {
        _enableSync.value = backing.getBoolean(KEY_ENABLE_SYNC, true)
        _enableVector.value = backing.getBoolean(KEY_ENABLE_VECTOR, true)

        _enableFts4.value = backing.getBoolean(KEY_ENABLE_FTS4, true)
        _retentionDays.value = backing.getInt(KEY_RETENTION_DAYS, DEFAULT_RETENTION_DAYS)

        _exportEncryption.value = backing.getBoolean(KEY_EXPORT_ENCRYPTION, true)


        _enableSemanticRanking.value = backing.getBoolean(KEY_ENABLE_SEMANTIC_RANKING, true)
        _autoDeleteOnRead.value = backing.getBoolean(KEY_AUTO_DELETE_ON_READ, false)

        _backupIntervalDays.value = backing.getInt(KEY_BACKUP_INTERVAL_DAYS, 0)
        _exportFormats.value = backing.getString("config_export_formats", "json,csv") ?: "json,csv"
        _exportAnonymize.value = backing.getBoolean("config_export_anonymize", false)
        _anonymizeTitles.value = backing.getBoolean(KEY_ANONYMIZE_TITLES, false)

        _maxCacheSizeMb.value = backing.getInt(KEY_MAX_CACHE_MB, 50)
        _semanticWeight.value = backing.getInt(KEY_SEMANTIC_WEIGHT, 50)

        _compactMode.value = backing.getBoolean(KEY_COMPACT_MODE, false)
        _showAppIcons.value = backing.getBoolean(KEY_SHOW_APP_ICONS, true)

        _sortOrder.value = backing.getString(KEY_SORT_ORDER, "newest") ?: "newest"
        _groupByApp.value = backing.getBoolean(KEY_GROUP_BY_APP, true)

        _previewLength.value = backing.getInt(KEY_PREVIEW_LENGTH, 140)
        _themeAccent.value = backing.getString(KEY_THEME_ACCENT, "system") ?: "system"

        _enableTelemetry.value = backing.getBoolean(KEY_ENABLE_TELEMETRY, true)
        _telemetryLevel.value = backing.getString(KEY_TELEMETRY_LEVEL, "minimal") ?: "minimal"

        _maxDbMb.value = backing.getInt(KEY_MAX_DB_MB, 512)
        _lowMemoryMode.value = backing.getBoolean(KEY_LOW_MEMORY_MODE, false)
        _vectorCacheMax.value = backing.getInt(KEY_VECTOR_CACHE_MAX, 1000)


        _exportRequiresBiometric.value = backing.getBoolean(KEY_EXPORT_REQUIRES_BIOMETRIC, false)

        _appLockEnabled.value = backing.getBoolean(KEY_APP_LOCK_ENABLED, false)

        _useKeystore.value = backing.getBoolean(KEY_USE_KEYSTORE, true)

        _dbEncrypted.value = backing.getBoolean(KEY_DB_ENCRYPTED, true)

        _dbEncryptionMode.value = DbEncryptionMode.fromPersisted(
            backing.getString(KEY_DB_ENCRYPTION_MODE, DbEncryptionMode.KEYSTORE.persisted)
        )


        _autoActOnNotification.value = backing.getBoolean(KEY_AUTO_ACT_ON_NOTIFICATION, false)

        _defaultReplyMethod.value = backing.getString(KEY_DEFAULT_REPLY_METHOD, "inline") ?: "inline"

        _longPressAction.value = backing.getString(KEY_LONG_PRESS_ACTION, "open") ?: "open"

        automationManager.reload()
    }


    fun setRedactPii(v: Boolean) {
        backing.edit().putBoolean("config_pii_redaction", v).apply()
        _redactPii.value = v
    }

    fun setEncryptedExports(v: Boolean) {
        backing.edit().putBoolean("config_encrypted_exports", v).apply()
        _encryptedExports.value = v
    }

    fun setRequirePassphrase(v: Boolean) {
        backing.edit().putBoolean("config_require_passphrase", v).apply()
        _requirePassphrase.value = v
    }

    fun setAutoLockDb(v: Boolean) {
        backing.edit().putBoolean("config_auto_lock_db", v).apply()
        _autoLockDb.value = v
    }

    fun setExportRequiresBiometric(value: Boolean) {
        backing.edit().putBoolean(KEY_EXPORT_REQUIRES_BIOMETRIC, value).apply()
        _exportRequiresBiometric.value = value
    }
    fun setAppLockEnabled(value: Boolean) {
        backing.edit().putBoolean(KEY_APP_LOCK_ENABLED, value).apply()
        _appLockEnabled.value = value
    }

    fun setUseKeystore(value: Boolean) {
        backing.edit().putBoolean(KEY_USE_KEYSTORE, value).apply()
        _useKeystore.value = value
    }

    fun setDbEncrypted(value: Boolean) {
        backing.edit().putBoolean(KEY_DB_ENCRYPTED, value).apply()
        _dbEncrypted.value = value
    }
    fun setDbEncryptionMode(value: DbEncryptionMode) {
        backing.edit().putString(KEY_DB_ENCRYPTION_MODE, value.persisted).apply()
        _dbEncryptionMode.value = value
    }

    /** Export only user-facing settings; auth tokens and local sync metadata are excluded. */
    fun exportSyncableSettings(): org.json.JSONObject {
        val result = org.json.JSONObject()
        backing.all.forEach { (key, value) ->
            if (!key.startsWith("config_") || key == "config_use_supabase_sync") {
                when (value) {
                    is Boolean, is Int, is Long, is Float, is String -> result.put(key, value)
                }
            }
        }
        return result
    }

    fun importSyncableSettings(settings: org.json.JSONObject) {
        val editor = backing.edit()
        settings.keys().forEach { key ->
            if (!key.startsWith("config_") || key == "config_use_supabase_sync") {
                when (val value = settings.opt(key)) {
                    is Boolean -> editor.putBoolean(key, value)
                    is Int -> editor.putInt(key, value)
                    is Long -> editor.putLong(key, value)
                    is Double -> editor.putFloat(key, value.toFloat())
                    is String -> editor.putString(key, value)
                }
            }
        }
        editor.apply()
    }
    fun setEnableSync(value: Boolean) {
        backing.edit().putBoolean(KEY_ENABLE_SYNC, value).apply()
        _enableSync.value = value
    }

    fun setEnableVector(value: Boolean) {
        backing.edit().putBoolean(KEY_ENABLE_VECTOR, value).apply()
        _enableVector.value = value
    }

    fun setEnableFts4(value: Boolean) {
        backing.edit().putBoolean(KEY_ENABLE_FTS4, value).apply()
        _enableFts4.value = value
    }

    fun setRetentionDays(value: Int) {
        val safe = value.coerceIn(1, 3650)
        backing.edit().putInt(KEY_RETENTION_DAYS, safe).apply()
        _retentionDays.value = safe
    }

    fun setExportEncryption(value: Boolean) {
        backing.edit().putBoolean(KEY_EXPORT_ENCRYPTION, value).apply()
        _exportEncryption.value = value
    }


    fun setEnableSemanticRanking(value: Boolean) {
        backing.edit().putBoolean(KEY_ENABLE_SEMANTIC_RANKING, value).apply()
        _enableSemanticRanking.value = value
    }
    fun setAutoDeleteOnRead(value: Boolean) {
        val safe = value && !BuildConfig.DEBUG
        backing.edit().putBoolean(KEY_AUTO_DELETE_ON_READ, safe).apply()
        _autoDeleteOnRead.value = safe
    }
    fun setBackupIntervalDays(value: Int) {
        val safe = value.coerceAtLeast(0)
        backing.edit().putInt(KEY_BACKUP_INTERVAL_DAYS, safe).apply()
        _backupIntervalDays.value = safe
    }

    fun setExportFormats(value: String) {
        val safe = value.split(',', ' ', '\n')
            .map(String::trim)
            .filter { it == "json" || it == "csv" || it == "ndjson" }
            .distinct()
            .joinToString(",")
            .ifBlank { "json" }
        backing.edit().putString("config_export_formats", safe).apply()
        _exportFormats.value = safe
    }

    fun setExportAnonymize(value: Boolean) {
        backing.edit().putBoolean("config_export_anonymize", value).apply()
        _exportAnonymize.value = value
    }

    fun setAnonymizeTitles(value: Boolean) {
        backing.edit().putBoolean(KEY_ANONYMIZE_TITLES, value).apply()
        _anonymizeTitles.value = value
    }
    fun setMaxCacheSizeMb(value: Int) {
        val safe = value.coerceIn(1, 1024)
        backing.edit().putInt(KEY_MAX_CACHE_MB, safe).apply()
        _maxCacheSizeMb.value = safe
    }

    fun setSemanticWeight(value: Int) {
        val safe = value.coerceIn(0, 100)
        backing.edit().putInt(KEY_SEMANTIC_WEIGHT, safe).apply()
        _semanticWeight.value = safe
    }

    fun setCompactMode(value: Boolean) {
        backing.edit().putBoolean(KEY_COMPACT_MODE, value).apply()
        _compactMode.value = value
    }

    fun setShowAppIcons(value: Boolean) {
        backing.edit().putBoolean(KEY_SHOW_APP_ICONS, value).apply()
        _showAppIcons.value = value
    }

    fun setSortOrder(value: String) {
        val safe = if (value in SORT_ORDERS) value else "newest"
        backing.edit().putString(KEY_SORT_ORDER, safe).apply()
        _sortOrder.value = safe
    }

    fun setGroupByApp(value: Boolean) {
        backing.edit().putBoolean(KEY_GROUP_BY_APP, value).apply()
        _groupByApp.value = value
    }

    fun setPreviewLength(value: Int) {
        val safe = value.coerceIn(40, 500)
        backing.edit().putInt(KEY_PREVIEW_LENGTH, safe).apply()
        _previewLength.value = safe
    }

    fun setThemeAccent(value: String) {
        val safe = if (value in THEME_ACCENTS) value else "system"
        backing.edit().putString(KEY_THEME_ACCENT, safe).apply()
        _themeAccent.value = safe
    }
    fun setEnableTelemetry(value: Boolean) {
        backing.edit().putBoolean(KEY_ENABLE_TELEMETRY, value).apply()
        _enableTelemetry.value = value
    }

    fun setTelemetryLevel(value: String) {
        backing.edit().putString(KEY_TELEMETRY_LEVEL, value).apply()
        _telemetryLevel.value = value
    }

    fun setMaxDbMb(value: Int) {
        val safe = value.coerceIn(1, 4096)
        backing.edit().putInt(KEY_MAX_DB_MB, safe).apply()
        _maxDbMb.value = safe
    }

    fun setLowMemoryMode(value: Boolean) {
        backing.edit().putBoolean(KEY_LOW_MEMORY_MODE, value).apply()
        _lowMemoryMode.value = value
        com.jeffers.notimindlite.util.VectorEmbeddingHelper.setLowMemoryMode(value)
    }

    fun setVectorCacheMax(value: Int) {
        val safe = value.coerceIn(1, 10000)
        backing.edit().putInt(KEY_VECTOR_CACHE_MAX, safe).apply()
        _vectorCacheMax.value = safe
        com.jeffers.notimindlite.util.VectorEmbeddingHelper.updateCacheSize(safe)
    }


    fun setAutoActOnNotification(value: Boolean) {
        backing.edit().putBoolean(KEY_AUTO_ACT_ON_NOTIFICATION, value).apply()
        _autoActOnNotification.value = value
    }

    fun setDefaultReplyMethod(value: String) {
        val safe = if (value in REPLY_METHODS) value else "inline"
        backing.edit().putString(KEY_DEFAULT_REPLY_METHOD, safe).apply()
        _defaultReplyMethod.value = safe
    }

    fun setLongPressAction(value: String) {
        val safe = if (value in LONG_PRESS_ACTIONS) value else "open"
        backing.edit().putString(KEY_LONG_PRESS_ACTION, safe).apply()
        _longPressAction.value = safe
    }

    companion object {

        private const val DEFAULT_RETENTION_DAYS = 30
        const val RETAIN_ALL_DAYS = 3650
        private const val KEY_ENABLE_SYNC = "config_enable_sync"
        private const val KEY_ENABLE_VECTOR = "config_enable_vector"
        private const val KEY_ENABLE_FTS4 = "config_enable_fts4"
        private const val KEY_RETENTION_DAYS = "config_retention_days"
        private const val KEY_EXPORT_ENCRYPTION = "config_export_encryption"

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

        private const val KEY_USE_KEYSTORE = "config_use_keystore"
        private const val KEY_DB_ENCRYPTED = "config_db_encrypted"
        private const val KEY_DB_ENCRYPTION_MODE = "config_db_key_mode"
        private const val KEY_APP_LOCK_ENABLED = "config_app_lock_enabled"
        private const val KEY_EXPORT_REQUIRES_BIOMETRIC = "config_export_requires_biometric"
        private const val KEY_COMPACT_MODE = "config_compact_mode"
        private const val KEY_SHOW_APP_ICONS = "config_show_app_icons"
        private const val KEY_SORT_ORDER = "config_sort_order"
        private const val KEY_GROUP_BY_APP = "config_group_by_app"
        private const val KEY_PREVIEW_LENGTH = "config_preview_length"
        private const val KEY_THEME_ACCENT = "config_theme_accent"
        private const val KEY_AUTO_ACT_ON_NOTIFICATION = "config_auto_act_on_notification"
        private const val KEY_DEFAULT_REPLY_METHOD = "config_default_reply_method"
        private const val KEY_LONG_PRESS_ACTION = "config_long_press_action"

        private val SORT_ORDERS = setOf("newest", "oldest", "app", "title", "dismissed", "received", "all")
        private val THEME_ACCENTS = setOf("system", "blue", "green", "purple", "orange", "amoled")
        val REPLY_METHODS = setOf("inline", "open_app", "copy")
        val LONG_PRESS_ACTIONS = setOf("open", "reply", "dismiss")
        private const val PROFILE_STORE = "notimind_profiles"

        private const val KEY_ACTIVE_PROFILE = "active_profile"
        const val DEFAULT_PROFILE_ID = "default"


        fun activeProfileId(context: Context): String = context.applicationContext
            .getSharedPreferences(PROFILE_STORE, Context.MODE_PRIVATE)
            .getString(KEY_ACTIVE_PROFILE, DEFAULT_PROFILE_ID) ?: DEFAULT_PROFILE_ID
    }
}

enum class DbEncryptionMode(val persisted: String) {
    NONE("none"), KEYSTORE("keystore"), LOCAL_KEY("local_key");

    companion object {
        fun fromPersisted(value: String?): DbEncryptionMode =
            entries.firstOrNull { it.persisted == value } ?: KEYSTORE
    }
}
