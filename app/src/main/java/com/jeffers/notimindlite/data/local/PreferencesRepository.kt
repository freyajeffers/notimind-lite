package com.jeffers.notimindlite.data.local

import android.content.Context
import com.jeffers.notimindlite.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

private const val DEFAULT_MAX_CACHE_MB = 50
private const val DEFAULT_SEMANTIC_WEIGHT = 50
private const val DEFAULT_MAX_DB_MB = 512
private const val DEFAULT_VECTOR_CACHE_MAX = 1000
private const val MIN_RETENTION_DAYS = 1
private const val MAX_RETENTION_DAYS = 3650
private const val MIN_CACHE_SIZE_MB = 1
private const val MAX_CACHE_SIZE_MB = 1024
private const val MIN_SEMANTIC_WEIGHT = 0
private const val MAX_SEMANTIC_WEIGHT = 100
private const val MIN_DB_SIZE_MB = 1
private const val MAX_DB_SIZE_MB = 4096
private const val MIN_VECTOR_CACHE_SIZE = 1
private const val MAX_VECTOR_CACHE_SIZE = 10000

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
    val securityManager = SecurityPreferencesManager(context, profileManager)
    val displayManager = DisplayPreferencesManager(context, profileManager)
    val telemetryManager = TelemetryPreferencesManager(context, profileManager)
    val exportManager = ExportPreferencesManager(context, profileManager)
    private val backing: android.content.SharedPreferences
        get() = deContext.getSharedPreferences(profileManager.activePreferencesName, Context.MODE_PRIVATE)
    val settingsSyncManager = SettingsSyncManager { backing }

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
            securityManager.reload()
            displayManager.reload()
            telemetryManager.reload()
            exportManager.reload()
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



    private val _retentionDays = MutableStateFlow(
        backing.getInt(KEY_RETENTION_DAYS, if (BuildConfig.DEBUG) RETAIN_ALL_DAYS else DEFAULT_RETENTION_DAYS)
    )
    private val _exportEncryption = MutableStateFlow(backing.getBoolean(KEY_EXPORT_ENCRYPTION, true))



    val retentionDays: StateFlow<Int> = _retentionDays
    val exportEncryption: StateFlow<Boolean> = _exportEncryption


    private val _enableSemanticRanking = MutableStateFlow(backing.getBoolean(KEY_ENABLE_SEMANTIC_RANKING, true))
    private val _autoDeleteOnRead = MutableStateFlow(backing.getBoolean(KEY_AUTO_DELETE_ON_READ, false))
    private val _backupIntervalDays = MutableStateFlow(backing.getInt(KEY_BACKUP_INTERVAL_DAYS, 0))

    private val _anonymizeTitles = MutableStateFlow(backing.getBoolean(KEY_ANONYMIZE_TITLES, false))
    private val _maxCacheSizeMb = MutableStateFlow(backing.getInt(KEY_MAX_CACHE_MB, DEFAULT_MAX_CACHE_MB))
    private val _semanticWeight = MutableStateFlow(backing.getInt(KEY_SEMANTIC_WEIGHT, DEFAULT_SEMANTIC_WEIGHT))


    val enableSemanticRanking: StateFlow<Boolean> = _enableSemanticRanking
    val autoDeleteOnRead: StateFlow<Boolean> = _autoDeleteOnRead
    val backupIntervalDays: StateFlow<Int> = _backupIntervalDays

    val anonymizeTitles: StateFlow<Boolean> = _anonymizeTitles
    val maxCacheSizeMb: StateFlow<Int> = _maxCacheSizeMb
    val semanticWeight: StateFlow<Int> = _semanticWeight


    private val _maxDbMb = MutableStateFlow(backing.getInt(KEY_MAX_DB_MB, DEFAULT_MAX_DB_MB))
    private val _lowMemoryMode = MutableStateFlow(backing.getBoolean(KEY_LOW_MEMORY_MODE, false))
    private val _vectorCacheMax = MutableStateFlow(backing.getInt(KEY_VECTOR_CACHE_MAX, DEFAULT_VECTOR_CACHE_MAX))
    val maxDbMb: StateFlow<Int> = _maxDbMb
    val lowMemoryMode: StateFlow<Boolean> = _lowMemoryMode
    val vectorCacheMax: StateFlow<Int> = _vectorCacheMax


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

        _retentionDays.value = backing.getInt(KEY_RETENTION_DAYS, DEFAULT_RETENTION_DAYS)

        _exportEncryption.value = backing.getBoolean(KEY_EXPORT_ENCRYPTION, true)


        _enableSemanticRanking.value = backing.getBoolean(KEY_ENABLE_SEMANTIC_RANKING, true)
        _autoDeleteOnRead.value = backing.getBoolean(KEY_AUTO_DELETE_ON_READ, false)

        _backupIntervalDays.value = backing.getInt(KEY_BACKUP_INTERVAL_DAYS, 0)

        _anonymizeTitles.value = backing.getBoolean(KEY_ANONYMIZE_TITLES, false)

        _maxCacheSizeMb.value = backing.getInt(KEY_MAX_CACHE_MB, DEFAULT_MAX_CACHE_MB)
        _semanticWeight.value = backing.getInt(KEY_SEMANTIC_WEIGHT, DEFAULT_SEMANTIC_WEIGHT)


        _maxDbMb.value = backing.getInt(KEY_MAX_DB_MB, DEFAULT_MAX_DB_MB)
        _lowMemoryMode.value = backing.getBoolean(KEY_LOW_MEMORY_MODE, false)
        _vectorCacheMax.value = backing.getInt(KEY_VECTOR_CACHE_MAX, DEFAULT_VECTOR_CACHE_MAX)



        _autoActOnNotification.value = backing.getBoolean(KEY_AUTO_ACT_ON_NOTIFICATION, false)

        _defaultReplyMethod.value = backing.getString(KEY_DEFAULT_REPLY_METHOD, "inline") ?: "inline"

        _longPressAction.value = backing.getString(KEY_LONG_PRESS_ACTION, "open") ?: "open"

        automationManager.reload()
    }





    fun setRetentionDays(value: Int) {
        val safe = value.coerceIn(MIN_RETENTION_DAYS, MAX_RETENTION_DAYS)
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

    fun setAnonymizeTitles(value: Boolean) {
        backing.edit().putBoolean(KEY_ANONYMIZE_TITLES, value).apply()
        _anonymizeTitles.value = value
    }
    fun setMaxCacheSizeMb(value: Int) {
        val safe = value.coerceIn(MIN_CACHE_SIZE_MB, MAX_CACHE_SIZE_MB)
        backing.edit().putInt(KEY_MAX_CACHE_MB, safe).apply()
        _maxCacheSizeMb.value = safe
    }

    fun setSemanticWeight(value: Int) {
        val safe = value.coerceIn(MIN_SEMANTIC_WEIGHT, MAX_SEMANTIC_WEIGHT)
        backing.edit().putInt(KEY_SEMANTIC_WEIGHT, safe).apply()
        _semanticWeight.value = safe
    }




    fun setMaxDbMb(value: Int) {
        val safe = value.coerceIn(MIN_DB_SIZE_MB, MAX_DB_SIZE_MB)
        backing.edit().putInt(KEY_MAX_DB_MB, safe).apply()
        _maxDbMb.value = safe
    }

    fun setLowMemoryMode(value: Boolean) {
        backing.edit().putBoolean(KEY_LOW_MEMORY_MODE, value).apply()
        _lowMemoryMode.value = value
        com.jeffers.notimindlite.util.VectorEmbeddingHelper.setLowMemoryMode(value)
    }

    fun setVectorCacheMax(value: Int) {
        val safe = value.coerceIn(MIN_VECTOR_CACHE_SIZE, MAX_VECTOR_CACHE_SIZE)
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

        private const val KEY_RETENTION_DAYS = "config_retention_days"
        private const val KEY_EXPORT_ENCRYPTION = "config_export_encryption"

        private const val KEY_ENABLE_SEMANTIC_RANKING = "config_enable_semantic_ranking"
        private const val KEY_AUTO_DELETE_ON_READ = "config_auto_delete_on_read"
        private const val KEY_BACKUP_INTERVAL_DAYS = "config_backup_interval_days"
        private const val KEY_ANONYMIZE_TITLES = "config_anonymize_titles"
        private const val KEY_MAX_CACHE_MB = "config_max_cache_mb"
        private const val KEY_SEMANTIC_WEIGHT = "config_semantic_weight"

        private const val KEY_MAX_DB_MB = "config_max_db_mb"
        private const val KEY_LOW_MEMORY_MODE = "config_low_memory_mode"
        private const val KEY_VECTOR_CACHE_MAX = "config_vector_cache_max"



        private const val KEY_AUTO_ACT_ON_NOTIFICATION = "config_auto_act_on_notification"
        private const val KEY_DEFAULT_REPLY_METHOD = "config_default_reply_method"
        private const val KEY_LONG_PRESS_ACTION = "config_long_press_action"


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
