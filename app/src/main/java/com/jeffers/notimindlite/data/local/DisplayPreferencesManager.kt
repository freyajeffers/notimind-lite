package com.jeffers.notimindlite.data.local

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class DisplayPreferencesManager(
    context: Context,
    private val profileManager: ProfilePreferencesManager
) {
    private val appContext = context.applicationContext
    private val preferences
        get() = appContext
            .createDeviceProtectedStorageContext()
            .getSharedPreferences(profileManager.activePreferencesName, Context.MODE_PRIVATE)

    private val _showAppIcons = MutableStateFlow(preferences.getBoolean(KEY_SHOW_APP_ICONS, true))
    private val _sortOrder = MutableStateFlow(preferences.getString(KEY_SORT_ORDER, "newest") ?: "newest")
    private val _groupByApp = MutableStateFlow(preferences.getBoolean(KEY_GROUP_BY_APP, true))
    private val _previewLength = MutableStateFlow(preferences.getInt(KEY_PREVIEW_LENGTH, DEFAULT_PREVIEW_LENGTH))
    private val _themeAccent = MutableStateFlow(preferences.getString(KEY_THEME_ACCENT, "system") ?: "system")
    private val _compactMode = MutableStateFlow(preferences.getBoolean(KEY_COMPACT_MODE, false))

    val showAppIcons: StateFlow<Boolean> = _showAppIcons.asStateFlow()
    val sortOrder: StateFlow<String> = _sortOrder.asStateFlow()
    val groupByApp: StateFlow<Boolean> = _groupByApp.asStateFlow()
    val previewLength: StateFlow<Int> = _previewLength.asStateFlow()
    val themeAccent: StateFlow<String> = _themeAccent.asStateFlow()
    val compactMode: StateFlow<Boolean> = _compactMode.asStateFlow()

    fun reload() {
        _showAppIcons.value = preferences.getBoolean(KEY_SHOW_APP_ICONS, true)
        _sortOrder.value = preferences.getString(KEY_SORT_ORDER, "newest") ?: "newest"
        _groupByApp.value = preferences.getBoolean(KEY_GROUP_BY_APP, true)
        _previewLength.value = preferences.getInt(KEY_PREVIEW_LENGTH, DEFAULT_PREVIEW_LENGTH)
        _themeAccent.value = preferences.getString(KEY_THEME_ACCENT, "system") ?: "system"
        _compactMode.value = preferences.getBoolean(KEY_COMPACT_MODE, false)
    }

    fun setShowAppIcons(value: Boolean) {
        preferences.edit { putBoolean(KEY_SHOW_APP_ICONS, value) }
        _showAppIcons.value = value
    }

    fun setSortOrder(value: String) {
        val safe = if (value in SORT_ORDERS) value else "newest"
        preferences.edit { putString(KEY_SORT_ORDER, safe) }
        _sortOrder.value = safe
    }

    fun setGroupByApp(value: Boolean) {
        preferences.edit { putBoolean(KEY_GROUP_BY_APP, value) }
        _groupByApp.value = value
    }

    fun setPreviewLength(value: Int) {
        val safe = value.coerceIn(MIN_PREVIEW_LENGTH, MAX_PREVIEW_LENGTH)
        preferences.edit { putInt(KEY_PREVIEW_LENGTH, safe) }
        _previewLength.value = safe
    }

    fun setThemeAccent(value: String) {
        val safe = if (value in THEME_ACCENTS) value else "system"
        preferences.edit { putString(KEY_THEME_ACCENT, safe) }
        _themeAccent.value = safe
    }

    fun setCompactMode(value: Boolean) {
        preferences.edit { putBoolean(KEY_COMPACT_MODE, value) }
        _compactMode.value = value
    }

    private companion object {
        const val KEY_SHOW_APP_ICONS = "config_show_app_icons"
        const val KEY_SORT_ORDER = "config_sort_order"
        const val KEY_GROUP_BY_APP = "config_group_by_app"
        const val KEY_PREVIEW_LENGTH = "config_preview_length"
        const val KEY_THEME_ACCENT = "config_theme_accent"
        const val KEY_COMPACT_MODE = "config_compact_mode"
        private const val DEFAULT_PREVIEW_LENGTH = 140
        private const val MIN_PREVIEW_LENGTH = 16
        private const val MAX_PREVIEW_LENGTH = 1024
        private val SORT_ORDERS = setOf("newest", "oldest", "app", "title", "dismissed", "received", "all")
        private val THEME_ACCENTS = setOf("system", "blue", "green", "purple", "orange", "amoled")
    }
}
