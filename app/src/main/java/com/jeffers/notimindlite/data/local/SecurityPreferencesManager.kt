package com.jeffers.notimindlite.data.local

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SecurityPreferencesManager(
    context: Context,
    private val profileManager: ProfilePreferencesManager
) {
    private val appContext = context.applicationContext
    private val preferences
        get() = appContext
            .createDeviceProtectedStorageContext()
            .getSharedPreferences(profileManager.activePreferencesName, Context.MODE_PRIVATE)

    private val _redactPii = MutableStateFlow(readSecurityRedactPii(preferences))
    private val _encryptedExports = MutableStateFlow(preferences.getBoolean(KEY_ENCRYPTED_EXPORTS, true))
    private val _requirePassphrase = MutableStateFlow(preferences.getBoolean(KEY_REQUIRE_PASSPHRASE, false))
    private val _autoLockDb = MutableStateFlow(preferences.getBoolean(KEY_AUTO_LOCK_DB, false))
    private val _exportRequiresBiometric = MutableStateFlow(
        preferences.getBoolean(KEY_EXPORT_REQUIRES_BIOMETRIC, false)
    )
    private val _appLockEnabled = MutableStateFlow(preferences.getBoolean(KEY_APP_LOCK_ENABLED, false))
    private val _useKeystore = MutableStateFlow(preferences.getBoolean(KEY_USE_KEYSTORE, true))
    private val _dbEncrypted = MutableStateFlow(preferences.getBoolean(KEY_DB_ENCRYPTED, true))
    private val _dbEncryptionMode = MutableStateFlow(
        DbEncryptionMode.fromPersisted(
            preferences.getString(KEY_DB_ENCRYPTION_MODE, DbEncryptionMode.KEYSTORE.persisted)
        )
    )

    val redactPii: StateFlow<Boolean> = _redactPii.asStateFlow()
    val encryptedExports: StateFlow<Boolean> = _encryptedExports.asStateFlow()
    val requirePassphrase: StateFlow<Boolean> = _requirePassphrase.asStateFlow()
    val autoLockDb: StateFlow<Boolean> = _autoLockDb.asStateFlow()
    val exportRequiresBiometric: StateFlow<Boolean> = _exportRequiresBiometric.asStateFlow()
    val appLockEnabled: StateFlow<Boolean> = _appLockEnabled.asStateFlow()
    val useKeystore: StateFlow<Boolean> = _useKeystore.asStateFlow()
    val dbEncrypted: StateFlow<Boolean> = _dbEncrypted.asStateFlow()
    val dbEncryptionMode: StateFlow<DbEncryptionMode> = _dbEncryptionMode.asStateFlow()

    fun reload() {
        _redactPii.value = readSecurityRedactPii(preferences)
        _encryptedExports.value = preferences.getBoolean(KEY_ENCRYPTED_EXPORTS, true)
        _requirePassphrase.value = preferences.getBoolean(KEY_REQUIRE_PASSPHRASE, false)
        _autoLockDb.value = preferences.getBoolean(KEY_AUTO_LOCK_DB, false)
        _exportRequiresBiometric.value = preferences.getBoolean(KEY_EXPORT_REQUIRES_BIOMETRIC, false)
        _appLockEnabled.value = preferences.getBoolean(KEY_APP_LOCK_ENABLED, false)
        _useKeystore.value = preferences.getBoolean(KEY_USE_KEYSTORE, true)
        _dbEncrypted.value = preferences.getBoolean(KEY_DB_ENCRYPTED, true)
        _dbEncryptionMode.value = DbEncryptionMode.fromPersisted(
            preferences.getString(KEY_DB_ENCRYPTION_MODE, DbEncryptionMode.KEYSTORE.persisted)
        )
    }

    fun setRedactPii(value: Boolean) =
        updateSecurityBoolean(preferences, KEY_REDACT_PII, value) { _redactPii.value = value }

    fun setEncryptedExports(value: Boolean) =
        updateSecurityBoolean(preferences, KEY_ENCRYPTED_EXPORTS, value) { _encryptedExports.value = value }

    fun setRequirePassphrase(value: Boolean) =
        updateSecurityBoolean(preferences, KEY_REQUIRE_PASSPHRASE, value) { _requirePassphrase.value = value }

    fun setAutoLockDb(value: Boolean) =
        updateSecurityBoolean(preferences, KEY_AUTO_LOCK_DB, value) { _autoLockDb.value = value }

    fun setExportRequiresBiometric(value: Boolean) =
        updateSecurityBoolean(preferences, KEY_EXPORT_REQUIRES_BIOMETRIC, value) {
            _exportRequiresBiometric.value = value
        }

    fun setAppLockEnabled(value: Boolean) =
        updateSecurityBoolean(preferences, KEY_APP_LOCK_ENABLED, value) { _appLockEnabled.value = value }

    fun setUseKeystore(value: Boolean) =
        updateSecurityBoolean(preferences, KEY_USE_KEYSTORE, value) { _useKeystore.value = value }

    fun setDbEncrypted(value: Boolean) =
        updateSecurityBoolean(preferences, KEY_DB_ENCRYPTED, value) { _dbEncrypted.value = value }

    fun setDbEncryptionMode(value: DbEncryptionMode) {
        preferences.edit {putString(KEY_DB_ENCRYPTION_MODE, value.persisted)}
        _dbEncryptionMode.value = value
    }

    private companion object {
        const val KEY_REDACT_PII = "config_pii_redaction"
        const val KEY_ENCRYPTED_EXPORTS = "config_encrypted_exports"
        const val KEY_REQUIRE_PASSPHRASE = "config_require_passphrase"
        const val KEY_AUTO_LOCK_DB = "config_auto_lock_db"
        const val KEY_EXPORT_REQUIRES_BIOMETRIC = "config_export_requires_biometric"
        const val KEY_APP_LOCK_ENABLED = "config_app_lock_enabled"
        const val KEY_USE_KEYSTORE = "config_use_keystore"
        const val KEY_DB_ENCRYPTED = "config_db_encrypted"
        const val KEY_DB_ENCRYPTION_MODE = "config_db_key_mode"
    }
}

private fun updateSecurityBoolean(
    preferences: SharedPreferences,
    key: String,
    value: Boolean,
    state: () -> Unit,
) {
    preferences.edit {putBoolean(key, value)}
    state()
}

private fun readSecurityRedactPii(preferences: SharedPreferences): Boolean = preferences.getBoolean(
    "config_pii_redaction",
    preferences.getBoolean("config_redact_pii", false)
)
