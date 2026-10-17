package com.jeffers.notimindlite.data.local

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ExportPreferencesManager(
    context: Context,
    private val profileManager: ProfilePreferencesManager
) {
    private val appContext = context.applicationContext
    private val preferences
        get() = appContext.createDeviceProtectedStorageContext()
            .getSharedPreferences(profileManager.activePreferencesName, Context.MODE_PRIVATE)

    private val _formats = MutableStateFlow(preferences.getString(KEY_FORMATS, DEFAULT_FORMATS) ?: DEFAULT_FORMATS)
    private val _anonymize = MutableStateFlow(preferences.getBoolean(KEY_ANONYMIZE, false))

    val formats: StateFlow<String> = _formats.asStateFlow()
    val anonymize: StateFlow<Boolean> = _anonymize.asStateFlow()

    fun reload() {
        _formats.value = preferences.getString(KEY_FORMATS, DEFAULT_FORMATS) ?: DEFAULT_FORMATS
        _anonymize.value = preferences.getBoolean(KEY_ANONYMIZE, false)
    }

    fun setFormats(value: String) {
        val safe = value.split(',').map(String::trim).filter(String::isNotBlank).distinct()
            .joinToString(",").ifBlank { DEFAULT_FORMATS }
        preferences.edit().putString(KEY_FORMATS, safe).apply()
        _formats.value = safe
    }

    fun setAnonymize(value: Boolean) {
        preferences.edit().putBoolean(KEY_ANONYMIZE, value).apply()
        _anonymize.value = value
    }

    private companion object {
        const val KEY_FORMATS = "config_export_formats"
        const val KEY_ANONYMIZE = "config_export_anonymize"
        const val DEFAULT_FORMATS = "json,csv"
    }
}
