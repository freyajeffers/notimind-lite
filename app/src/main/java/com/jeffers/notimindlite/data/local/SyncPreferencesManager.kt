package com.jeffers.notimindlite.data.local

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SyncPreferencesManager(
    context: Context,
    private val profileManager: ProfilePreferencesManager
) {
    private val appContext = context.applicationContext
    private val preferences
        get() = appContext
            .createDeviceProtectedStorageContext()
            .getSharedPreferences(profileManager.activePreferencesName, Context.MODE_PRIVATE)

    private val _syncInterval = MutableStateFlow(preferences.getInt(KEY_SYNC_INTERVAL, DEFAULT_SYNC_INTERVAL))
    private val _syncWifiOnly = MutableStateFlow(preferences.getBoolean(KEY_SYNC_WIFI_ONLY, true))
    private val _syncChargingOnly = MutableStateFlow(preferences.getBoolean(KEY_SYNC_CHARGING_ONLY, true))
    private val _lastSyncTs = MutableStateFlow(preferences.getLong(KEY_LAST_SYNC_TS, 0L))
    private val _useSupabaseSync = MutableStateFlow(preferences.getBoolean(KEY_USE_SUPABASE_SYNC, true))
    private val _enableSync = MutableStateFlow(preferences.getBoolean(KEY_ENABLE_SYNC, true))
    private val _enableVector = MutableStateFlow(preferences.getBoolean(KEY_ENABLE_VECTOR, true))
    private val _enableFts4 = MutableStateFlow(preferences.getBoolean(KEY_ENABLE_FTS4, true))

    val syncInterval: StateFlow<Int> = _syncInterval.asStateFlow()
    val syncWifiOnly: StateFlow<Boolean> = _syncWifiOnly.asStateFlow()
    val syncChargingOnly: StateFlow<Boolean> = _syncChargingOnly.asStateFlow()
    val lastSyncTs: StateFlow<Long> = _lastSyncTs.asStateFlow()
    val useSupabaseSync: StateFlow<Boolean> = _useSupabaseSync.asStateFlow()
    val enableSync: StateFlow<Boolean> = _enableSync.asStateFlow()
    val enableVector: StateFlow<Boolean> = _enableVector.asStateFlow()
    val enableFts4: StateFlow<Boolean> = _enableFts4.asStateFlow()

    fun reload() {
        _syncInterval.value = preferences.getInt(KEY_SYNC_INTERVAL, DEFAULT_SYNC_INTERVAL)
        _syncWifiOnly.value = preferences.getBoolean(KEY_SYNC_WIFI_ONLY, true)
        _syncChargingOnly.value = preferences.getBoolean(KEY_SYNC_CHARGING_ONLY, true)
        _lastSyncTs.value = preferences.getLong(KEY_LAST_SYNC_TS, 0L)
        _useSupabaseSync.value = preferences.getBoolean(KEY_USE_SUPABASE_SYNC, true)
        _enableSync.value = preferences.getBoolean(KEY_ENABLE_SYNC, true)
        _enableVector.value = preferences.getBoolean(KEY_ENABLE_VECTOR, true)
        _enableFts4.value = preferences.getBoolean(KEY_ENABLE_FTS4, true)
    }

    fun setSyncInterval(value: Int) {
        val safe = value.coerceIn(MIN_SYNC_INTERVAL, MAX_SYNC_INTERVAL)
        preferences.edit {putInt(KEY_SYNC_INTERVAL, safe)}
        _syncInterval.value = safe
    }

    fun setSyncWifiOnly(value: Boolean) {
        preferences.edit {putBoolean(KEY_SYNC_WIFI_ONLY, value)}
        _syncWifiOnly.value = value
    }

    fun setSyncChargingOnly(value: Boolean) {
        preferences.edit {putBoolean(KEY_SYNC_CHARGING_ONLY, value)}
        _syncChargingOnly.value = value
    }

    fun setLastSyncTs(value: Long) {
        preferences.edit {putLong(KEY_LAST_SYNC_TS, value)}
        _lastSyncTs.value = value
    }

    fun refreshLastSyncTs() {
        _lastSyncTs.value = preferences.getLong(KEY_LAST_SYNC_TS, 0L)
    }

    fun setUseSupabaseSync(value: Boolean) {
        preferences.edit {putBoolean(KEY_USE_SUPABASE_SYNC, value)}
        _useSupabaseSync.value = value
    }

    fun setEnableSync(value: Boolean) {
        preferences.edit {putBoolean(KEY_ENABLE_SYNC, value)}
        _enableSync.value = value
    }

    fun setEnableVector(value: Boolean) {
        preferences.edit {putBoolean(KEY_ENABLE_VECTOR, value)}
        _enableVector.value = value
    }

    fun setEnableFts4(value: Boolean) {
        preferences.edit {putBoolean(KEY_ENABLE_FTS4, value)}
        _enableFts4.value = value
    }

    private companion object {
        const val DEFAULT_SYNC_INTERVAL = 360
        const val MIN_SYNC_INTERVAL = 15
        const val MAX_SYNC_INTERVAL = 1440
        const val KEY_SYNC_INTERVAL = "config_sync_interval_min"
        const val KEY_SYNC_WIFI_ONLY = "config_sync_wifi_only"
        const val KEY_SYNC_CHARGING_ONLY = "config_sync_charging_only"
        const val KEY_LAST_SYNC_TS = "config_last_sync_ts"
        const val KEY_USE_SUPABASE_SYNC = "config_use_supabase_sync"
        const val KEY_ENABLE_SYNC = "config_enable_sync"
        const val KEY_ENABLE_VECTOR = "config_enable_vector"
        const val KEY_ENABLE_FTS4 = "config_enable_fts4"
    }
}
