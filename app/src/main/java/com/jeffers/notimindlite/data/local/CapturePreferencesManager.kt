package com.jeffers.notimindlite.data.local

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class CapturePreferencesManager(
    context: Context,
    private val profileManager: ProfilePreferencesManager
) {
    private val appContext = context.applicationContext
    private val preferences get() = appContext
        .createDeviceProtectedStorageContext()
        .getSharedPreferences(profileManager.activePreferencesName, Context.MODE_PRIVATE)
    private val _enabled = MutableStateFlow(preferences.getBoolean(KEY_ENABLED, true))
    private val _foregroundOnly = MutableStateFlow(preferences.getBoolean(KEY_FOREGROUND_ONLY, false))
    private val _attachments = MutableStateFlow(preferences.getBoolean(KEY_ATTACHMENTS, true))
    private val _ongoing = MutableStateFlow(preferences.getBoolean(KEY_ONGOING, true))
    private val _allowlist = MutableStateFlow(readString(preferences, KEY_ALLOWLIST, "config_capture_allowlist"))
    private val _blocklist = MutableStateFlow(readString(preferences, KEY_BLOCKLIST, "config_capture_blocklist"))
    private val _minImportance = MutableStateFlow(preferences.getInt(KEY_MIN_IMPORTANCE, 0))
    private val _actionsOnly = MutableStateFlow(preferences.getBoolean(KEY_ACTIONS_ONLY, false))

    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()
    val foregroundOnly: StateFlow<Boolean> = _foregroundOnly.asStateFlow()
    val attachments: StateFlow<Boolean> = _attachments.asStateFlow()
    val ongoing: StateFlow<Boolean> = _ongoing.asStateFlow()
    val allowlist: StateFlow<String> = _allowlist.asStateFlow()
    val blocklist: StateFlow<String> = _blocklist.asStateFlow()
    val minImportance: StateFlow<Int> = _minImportance.asStateFlow()
    val actionsOnly: StateFlow<Boolean> = _actionsOnly.asStateFlow()

    fun reload() {
        _enabled.value = preferences.getBoolean(KEY_ENABLED, true)
        _foregroundOnly.value = preferences.getBoolean(KEY_FOREGROUND_ONLY, false)
        _attachments.value = preferences.getBoolean(KEY_ATTACHMENTS, true)
        _ongoing.value = preferences.getBoolean(KEY_ONGOING, true)
        _allowlist.value = readString(preferences, KEY_ALLOWLIST, "config_capture_allowlist")
        _blocklist.value = readString(preferences, KEY_BLOCKLIST, "config_capture_blocklist")
        _minImportance.value = preferences.getInt(KEY_MIN_IMPORTANCE, 0)
        _actionsOnly.value = preferences.getBoolean(KEY_ACTIONS_ONLY, false)
    }

    fun setEnabled(value: Boolean) =
        prefsUpdate(preferences, KEY_ENABLED, value) { _enabled.value = value }

    fun setForegroundOnly(value: Boolean) =
        prefsUpdate(preferences, KEY_FOREGROUND_ONLY, value) { _foregroundOnly.value = value }

    fun setAttachments(value: Boolean) =
        prefsUpdate(preferences, KEY_ATTACHMENTS, value) { _attachments.value = value }

    fun setOngoing(value: Boolean) =
        prefsUpdate(preferences, KEY_ONGOING, value) { _ongoing.value = value }

    fun setAllowlist(value: String) =
        prefsUpdate(preferences, KEY_ALLOWLIST, value) { _allowlist.value = value }

    fun setBlocklist(value: String) =
        prefsUpdate(preferences, KEY_BLOCKLIST, value) { _blocklist.value = value }

    fun setMinImportance(value: Int) =
        prefsUpdate(preferences, KEY_MIN_IMPORTANCE, value) { _minImportance.value = value }

    fun setActionsOnly(value: Boolean) =
        prefsUpdate(preferences, KEY_ACTIONS_ONLY, value) { _actionsOnly.value = value }

    private companion object {
        const val KEY_ENABLED = "config_capture_notifications"
        const val KEY_FOREGROUND_ONLY = "config_capture_foreground_only"
        const val KEY_ATTACHMENTS = "config_capture_attachments"
        const val KEY_ONGOING = "config_capture_ongoing"
        const val KEY_ALLOWLIST = "config_capture_package_allowlist"
        const val KEY_BLOCKLIST = "config_capture_package_blocklist"
        const val KEY_MIN_IMPORTANCE = "config_min_importance"
        const val KEY_ACTIONS_ONLY = "config_capture_actions_only"
    }
}

private fun readString(preferences: SharedPreferences, key: String, legacyKey: String): String =
    preferences.getString(key, preferences.getString(legacyKey, "")) ?: ""

private inline fun <reified T> prefsUpdate(preferences: SharedPreferences, key: String, value: T, state: () -> Unit) {
    preferences.edit {
        when (value) {
            is Boolean -> putBoolean(key, value)
            is Int -> putInt(key, value)
            is String -> putString(key, value)
        }
    }
    state()
}
