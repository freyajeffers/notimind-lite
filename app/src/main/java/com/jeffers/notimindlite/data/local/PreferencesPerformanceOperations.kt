package com.jeffers.notimindlite.data.local

import android.content.Context

interface PreferencesPerformanceOperations {
    fun getThreadPoolSize(): Int

    fun setThreadPoolSize(value: Int)

    fun getEmbeddingRateMs(): Long

    fun setEmbeddingRateMs(value: Long)

    fun getDbCompactionDays(): Int

    fun setDbCompactionDays(value: Int)

    fun getReindexDays(): Int

    fun setReindexDays(value: Int)

    fun isEmbeddingOffloadEnabled(): Boolean

    fun setEmbeddingOffloadEnabled(value: Boolean)
}

internal class PreferencesPerformanceOperationsImpl(
    context: Context
) : PreferencesPerformanceOperations {
    private val preferences = context.applicationContext
        .createDeviceProtectedStorageContext()
        .getSharedPreferences(
            "notimind_lite_prefs_${activeProfileId(context)}",
            Context.MODE_PRIVATE
        )

    override fun getThreadPoolSize() = preferences
        .getInt(KEY_THREAD_POOL_SIZE, DEFAULT_THREAD_POOL_SIZE)
        .coerceIn(MIN_THREAD_POOL_SIZE, MAX_THREAD_POOL_SIZE)

    override fun setThreadPoolSize(value: Int) {
        val safe = value.coerceIn(MIN_THREAD_POOL_SIZE, MAX_THREAD_POOL_SIZE)
        preferences.edit().putInt(KEY_THREAD_POOL_SIZE, safe).apply()
    }

    override fun getEmbeddingRateMs() = preferences
        .getLong(KEY_EMBEDDING_RATE_MS, DEFAULT_EMBEDDING_RATE_MS)
        .coerceAtLeast(0L)

    override fun setEmbeddingRateMs(value: Long) {
        preferences.edit().putLong(KEY_EMBEDDING_RATE_MS, value.coerceAtLeast(0L)).apply()
    }

    override fun getDbCompactionDays() = preferences
        .getInt(KEY_DB_COMPACTION_DAYS, DEFAULT_DB_COMPACTION_DAYS)
        .coerceAtLeast(1)

    override fun setDbCompactionDays(value: Int) {
        preferences.edit().putInt(KEY_DB_COMPACTION_DAYS, value.coerceAtLeast(1)).apply()
    }

    override fun getReindexDays() = preferences
        .getInt(KEY_REINDEX_DAYS, DEFAULT_REINDEX_DAYS)
        .coerceAtLeast(1)

    override fun setReindexDays(value: Int) {
        preferences.edit().putInt(KEY_REINDEX_DAYS, value.coerceAtLeast(1)).apply()
    }

    override fun isEmbeddingOffloadEnabled() = preferences
        .getBoolean(KEY_EMBEDDING_OFFLOAD, DEFAULT_EMBEDDING_OFFLOAD)

    override fun setEmbeddingOffloadEnabled(value: Boolean) {
        preferences.edit().putBoolean(KEY_EMBEDDING_OFFLOAD, value).apply()
    }

    private companion object {
        const val DEFAULT_THREAD_POOL_SIZE = 2
        const val DEFAULT_EMBEDDING_RATE_MS = 0L
        const val DEFAULT_DB_COMPACTION_DAYS = 7
        const val DEFAULT_REINDEX_DAYS = 30
        const val DEFAULT_EMBEDDING_OFFLOAD = true
        const val MIN_THREAD_POOL_SIZE = 1
        const val MAX_THREAD_POOL_SIZE = 32
        const val KEY_THREAD_POOL_SIZE = "performance_thread_pool_size"
        const val KEY_EMBEDDING_RATE_MS = "performance_embedding_rate_ms"
        const val KEY_DB_COMPACTION_DAYS = "performance_db_compaction_days"
        const val KEY_REINDEX_DAYS = "performance_reindex_days"
        const val KEY_EMBEDDING_OFFLOAD = "performance_embedding_offload"
    }
}

private fun activeProfileId(context: Context): String = context.applicationContext
    .createDeviceProtectedStorageContext()
    .getSharedPreferences("notimind_profiles", Context.MODE_PRIVATE)
    .getString("active_profile", "default") ?: "default"
