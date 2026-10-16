package com.jeffers.notimindlite.data.local

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SyncPreferencesManager(context: Context, private val profileManager: ProfilePreferencesManager) {
    private val appContext = context.applicationContext
    private val preferences
        get() = appContext.createDeviceProtectedStorageContext()
            .getSharedPreferences(profileManager.activePreferencesName, Context.MODE_PRIVATE)

    private val _syncInterval = MutableStateFlow(preferences.getInt(KEY_SYNC_INTERVAL, DEFAULT_SYNC_INTERVAL))
    private val _syncWifiOnly = MutableStateFlow(preferences.getBoolean(KEY_SYNC_WIFI_ONLY, true))
    private val _syncChargingOnly = MutableStateFlow(preferences.getBoolean(KEY_SYNC_CHARGING_ONLY, true))
    private val _lastSyncTs = MutableStateFlow(preferences.getLong(KEY_LAST_SYNC_TS, 0L))
    private val _useSupabaseSync = MutableStateFlow(preferences.getBoolean(KEY_USE_SUPABASE_SYNC, true))

    val syncInterval: StateFlow<Int> = _syncInterval.asStateFlow()
    val syncWifiOnly: StateFlow<Boolean> = _syncWifiOnly.asStateFlow()
    val syncChargingOnly: StateFlow<Boolean> = _syncChargingOnly.asStateFlow()
    val lastSyncTs: StateFlow<Long> = _lastSyncTs.asStateFlow()
    val useSupabaseSync: StateFlow<Boolean> = _useSupabaseSync.asStateFlow()

    fun reload() {
        _syncInterval.value = preferences.getInt(KEY_SYNC_INTERVAL, DEFAULT_SYNC_INTERVAL)
        _syncWifiOnly.value = preferences.getBoolean(KEY_SYNC_WIFI_ONLY, true)
        _syncChargingOnly.value = preferences.getBoolean(KEY_SYNC_CHARGING_ONLY, true)
        _lastSyncTs.value = preferences.getLong(KEY_LAST_SYNC_TS, 0L)
        _useSupabaseSync.value = preferences.getBoolean(KEY_USE_SUPABASE_SYNC, true)
    }

    fun setSyncInterval(value: Int) {
        val safe = value.coerceIn(MIN_SYNC_INTERVAL, MAX_SYNC_INTERVAL)
        preferences.edit().putInt(KEY_SYNC_INTERVAL, safe).apply()
        _syncInterval.value = safe
    }

    fun setSyncWifiOnly(value: Boolean) {
        preferences.edit().putBoolean(KEY_SYNC_WIFI_ONLY, value).apply()
        _syncWifiOnly.value = value
    }

    fun setSyncChargingOnly(value: Boolean) {
        preferences.edit().putBoolean(KEY_SYNC_CHARGING_ONLY, value).apply()
        _syncChargingOnly.value = value
    }

    fun setLastSyncTs(value: Long) {
        preferences.edit().putLong(KEY_LAST_SYNC_TS, value).apply()
        _lastSyncTs.value = value
    }

    fun refreshLastSyncTs() {
        _lastSyncTs.value = preferences.getLong(KEY_LAST_SYNC_TS, 0L)
    }

    fun setUseSupabaseSync(value: Boolean) {
        preferences.edit().putBoolean(KEY_USE_SUPABASE_SYNC, value).apply()
        _useSupabaseSync.value = value
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
    }
}
