package com.jeffers.notimindlite.data.local

import android.content.Context
import com.jeffers.notimindlite.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject


private const val DEFAULT_SEMANTIC_WEIGHT = 50

private const val MIN_RETENTION_DAYS = 1
private const val MAX_RETENTION_DAYS = 3650

private const val MIN_SEMANTIC_WEIGHT = 0
private const val MAX_SEMANTIC_WEIGHT = 100


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
    val storageManager = StoragePreferencesManager(context, profileManager)
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
            storageManager.reload()
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



    private val _semanticWeight = MutableStateFlow(backing.getInt(KEY_SEMANTIC_WEIGHT, DEFAULT_SEMANTIC_WEIGHT))


    val enableSemanticRanking: StateFlow<Boolean> = _enableSemanticRanking
    val autoDeleteOnRead: StateFlow<Boolean> = _autoDeleteOnRead
    val backupIntervalDays: StateFlow<Int> = _backupIntervalDays

    val anonymizeTitles: StateFlow<Boolean> = exportManager.anonymizeTitles

    val semanticWeight: StateFlow<Int> = _semanticWeight


    val maxDbMb: StateFlow<Int> = storageManager.maxDbMb
    val lowMemoryMode: StateFlow<Boolean> = storageManager.lowMemoryMode
    val vectorCacheMax: StateFlow<Int> = storageManager.vectorCacheMax


    val autoExecuteRules: StateFlow<List<AutoExecuteRule>> = automationManager.rules



    private fun reloadProfileFlows() {

        _retentionDays.value = backing.getInt(KEY_RETENTION_DAYS, DEFAULT_RETENTION_DAYS)

        _exportEncryption.value = backing.getBoolean(KEY_EXPORT_ENCRYPTION, true)


        _enableSemanticRanking.value = backing.getBoolean(KEY_ENABLE_SEMANTIC_RANKING, true)
        _autoDeleteOnRead.value = backing.getBoolean(KEY_AUTO_DELETE_ON_READ, false)

        _backupIntervalDays.value = backing.getInt(KEY_BACKUP_INTERVAL_DAYS, 0)




        _semanticWeight.value = backing.getInt(KEY_SEMANTIC_WEIGHT, DEFAULT_SEMANTIC_WEIGHT)


        // storageManager.reload() updates storage flows



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


    fun setSemanticWeight(value: Int) {
        val safe = value.coerceIn(MIN_SEMANTIC_WEIGHT, MAX_SEMANTIC_WEIGHT)
        backing.edit().putInt(KEY_SEMANTIC_WEIGHT, safe).apply()
        _semanticWeight.value = safe
    }







    companion object {

        private const val DEFAULT_RETENTION_DAYS = 30
        const val RETAIN_ALL_DAYS = 3650

        private const val KEY_RETENTION_DAYS = "config_retention_days"
        private const val KEY_EXPORT_ENCRYPTION = "config_export_encryption"

        private const val KEY_ENABLE_SEMANTIC_RANKING = "config_enable_semantic_ranking"
        private const val KEY_AUTO_DELETE_ON_READ = "config_auto_delete_on_read"
        private const val KEY_BACKUP_INTERVAL_DAYS = "config_backup_interval_days"


        private const val KEY_SEMANTIC_WEIGHT = "config_semantic_weight"








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
