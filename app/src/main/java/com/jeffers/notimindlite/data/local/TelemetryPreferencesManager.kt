package com.jeffers.notimindlite.data.local

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class TelemetryPreferencesManager(
    context: Context,
    private val profileManager: ProfilePreferencesManager
) {
    private val appContext = context.applicationContext
    private val preferences
        get() = appContext
            .createDeviceProtectedStorageContext()
            .getSharedPreferences(profileManager.activePreferencesName, Context.MODE_PRIVATE)

    private val _enabled = MutableStateFlow(preferences.getBoolean(KEY_ENABLED, true))
    private val _level = MutableStateFlow(preferences.getString(KEY_LEVEL, DEFAULT_LEVEL) ?: DEFAULT_LEVEL)

    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()
    val level: StateFlow<String> = _level.asStateFlow()

    fun reload() {
        _enabled.value = preferences.getBoolean(KEY_ENABLED, true)
        _level.value = preferences.getString(KEY_LEVEL, DEFAULT_LEVEL) ?: DEFAULT_LEVEL
    }

    fun setEnabled(value: Boolean) {
        preferences.edit { putBoolean(KEY_ENABLED, value) }
        _enabled.value = value
    }

    fun setLevel(value: String) {
        preferences.edit { putString(KEY_LEVEL, value) }
        _level.value = value
    }

    private companion object {
        const val KEY_ENABLED = "config_enable_telemetry"
        const val KEY_LEVEL = "config_telemetry_level"
        const val DEFAULT_LEVEL = "minimal"
    }
}
