package com.jeffers.notimindlite.data.local

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray

class SharedSearchStateManager(
    context: Context,
    private val profileManager: ProfilePreferencesManager
) {
    private val appContext = context.applicationContext
    private val preferences get() = appContext
        .createDeviceProtectedStorageContext()
        .getSharedPreferences(profileManager.activePreferencesName, Context.MODE_PRIVATE)
    private val _query = MutableStateFlow(preferences.getString(KEY_QUERY, "") ?: "")
    private val _packages = MutableStateFlow<List<String>?>(
        preferences
            .getString(KEY_PACKAGES, null)
            ?.split(',')
            ?.filter(String::isNotBlank)
            ?.ifEmpty { null }
    )
    private val _reason = MutableStateFlow(preferences.getInt(KEY_REASON, -1).let { if (it >= 0) it else null })
    private val _recentSearches = MutableStateFlow(readRecentSearches())

    val query: StateFlow<String> = _query.asStateFlow()
    val packages: StateFlow<List<String>?> = _packages.asStateFlow()
    val reason: StateFlow<Int?> = _reason.asStateFlow()
    val recentSearches: StateFlow<List<String>> = _recentSearches.asStateFlow()

    fun reload() {
        _query.value = preferences.getString(KEY_QUERY, "") ?: ""
        _packages.value = preferences
            .getString(KEY_PACKAGES, null)
            ?.split(',')
            ?.filter(String::isNotBlank)
            ?.ifEmpty { null }
        _reason.value = preferences.getInt(KEY_REASON, -1).let { if (it >= 0) it else null }
        _recentSearches.value = readRecentSearches()
    }

    fun setQuery(value: String) {
        preferences.edit().putString(KEY_QUERY, value).apply()
        _query.value = value
    }

    fun setPackages(value: List<String>?) {
        preferences.edit().putString(KEY_PACKAGES, value?.joinToString(",")).apply()
        _packages.value = value
    }

    fun setReason(value: Int?) {
        preferences.edit().putInt(KEY_REASON, value ?: -1).apply()
        _reason.value = value
    }

    fun addRecentSearch(value: String) {
        val updated = (listOf(value) + _recentSearches.value.filterNot { it == value }).take(MAX_RECENT_SEARCHES)
        _recentSearches.value = updated
        persistRecentSearches(updated)
    }

    fun resetRecentSearches() {
        _recentSearches.value = emptyList()
        persistRecentSearches(emptyList())
    }

    fun removeRecentSearch(value: String) {
        val updated = _recentSearches.value.filterNot { it == value }
        _recentSearches.value = updated
        persistRecentSearches(updated)
    }

    private fun readRecentSearches(): List<String> = runCatching {
        val array = JSONArray(preferences.getString(KEY_RECENT, ""))
        (0 until array.length()).map { array.getString(it) }
    }.getOrDefault(emptyList())

    private fun persistRecentSearches(values: List<String>) {
        preferences.edit().putString(KEY_RECENT, JSONArray(values).toString()).apply()
    }

    companion object {
        private const val KEY_QUERY = "ui_shared_search_query"
        private const val KEY_PACKAGES = "ui_shared_selected_packages"
        private const val KEY_REASON = "ui_shared_selected_reason"
        private const val KEY_RECENT = "ui_recent_searches"
        private const val MAX_RECENT_SEARCHES = 10
    }
}
