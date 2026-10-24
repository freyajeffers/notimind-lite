package com.jeffers.notimindlite.data.local

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class StoragePreferencesManager(
    context: Context,
    private val profileManager: ProfilePreferencesManager
) {
    private val appContext = context.applicationContext
    private val preferences
        get() = appContext
            .createDeviceProtectedStorageContext()
            .getSharedPreferences(profileManager.activePreferencesName, Context.MODE_PRIVATE)

    private val _maxDbMb = MutableStateFlow(preferences.getInt(KEY_MAX_DB_MB, DEFAULT_MAX_DB_MB))
    private val _lowMemoryMode = MutableStateFlow(preferences.getBoolean(KEY_LOW_MEMORY_MODE, false))
    private val _vectorCacheMax = MutableStateFlow(preferences.getInt(KEY_VECTOR_CACHE_MAX, DEFAULT_VECTOR_CACHE_MAX))
    private val _maxCacheSizeMb = MutableStateFlow(preferences.getInt(KEY_MAX_CACHE_MB, DEFAULT_MAX_CACHE_MB))

    val maxDbMb: StateFlow<Int> = _maxDbMb.asStateFlow()
    val lowMemoryMode: StateFlow<Boolean> = _lowMemoryMode.asStateFlow()
    val vectorCacheMax: StateFlow<Int> = _vectorCacheMax.asStateFlow()
    val maxCacheSizeMb: StateFlow<Int> = _maxCacheSizeMb.asStateFlow()

    fun reload() {
        _maxDbMb.value = preferences.getInt(KEY_MAX_DB_MB, DEFAULT_MAX_DB_MB)
        _lowMemoryMode.value = preferences.getBoolean(KEY_LOW_MEMORY_MODE, false)
        _vectorCacheMax.value = preferences.getInt(KEY_VECTOR_CACHE_MAX, DEFAULT_VECTOR_CACHE_MAX)
        _maxCacheSizeMb.value = preferences.getInt(KEY_MAX_CACHE_MB, DEFAULT_MAX_CACHE_MB)
    }

    fun setMaxDbMb(value: Int) {
        val safe = value.coerceIn(MIN_DB_SIZE_MB, MAX_DB_SIZE_MB)
        preferences.edit {putInt(KEY_MAX_DB_MB, safe)}
        _maxDbMb.value = safe
    }

    fun setLowMemoryMode(value: Boolean) {
        preferences.edit {putBoolean(KEY_LOW_MEMORY_MODE, value)}
        _lowMemoryMode.value = value
        com.jeffers.notimindlite.util.VectorEmbeddingHelper
            .setLowMemoryMode(value)
    }

    fun setVectorCacheMax(value: Int) {
        val safe = value.coerceIn(MIN_VECTOR_CACHE_SIZE, MAX_VECTOR_CACHE_SIZE)
        preferences.edit {putInt(KEY_VECTOR_CACHE_MAX, safe)}
        _vectorCacheMax.value = safe
        com.jeffers.notimindlite.util.VectorEmbeddingHelper
            .updateCacheSize(safe)
    }

    fun setMaxCacheSizeMb(value: Int) {
        val safe = value.coerceIn(MIN_CACHE_SIZE_MB, MAX_CACHE_SIZE_MB)
        preferences.edit {putInt(KEY_MAX_CACHE_MB, safe)}
        _maxCacheSizeMb.value = safe
    }

    private companion object {
        const val KEY_MAX_DB_MB = "config_max_db_mb"
        const val KEY_LOW_MEMORY_MODE = "config_low_memory_mode"
        const val KEY_VECTOR_CACHE_MAX = "config_vector_cache_max"
        const val KEY_MAX_CACHE_MB = "config_max_cache_mb"

        private const val DEFAULT_MAX_DB_MB = 512
        private const val DEFAULT_VECTOR_CACHE_MAX = 1000
        private const val DEFAULT_MAX_CACHE_MB = 50

        private const val MIN_DB_SIZE_MB = 1
        private const val MAX_DB_SIZE_MB = 4096
        private const val MIN_VECTOR_CACHE_SIZE = 1
        private const val MAX_VECTOR_CACHE_SIZE = 10000
        private const val MIN_CACHE_SIZE_MB = 1
        private const val MAX_CACHE_SIZE_MB = 1024
    }
}
