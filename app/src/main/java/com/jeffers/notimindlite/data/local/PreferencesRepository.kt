package com.jeffers.notimindlite.data.local

import android.content.Context
import android.os.UserManager
import androidx.core.content.edit

/** Persistent performance tuning preferences shared by workers and foreground code. */
class PreferencesRepository(context: Context) {
    companion object {
        const val DEFAULT_THREAD_POOL_SIZE = 2
        const val DEFAULT_EMBEDDING_RATE_MS = 0L
        const val DEFAULT_DB_COMPACTION_DAYS = 7
        const val DEFAULT_REINDEX_DAYS = 30
        const val DEFAULT_EMBEDDING_OFFLOAD = true

        private const val MIN_THREAD_POOL_SIZE = 1
        private const val MAX_THREAD_POOL_SIZE = 32

        private const val PREFS = "notimind_lite_prefs"
        private const val THREAD_POOL_SIZE = "performance_thread_pool_size"
        private const val EMBEDDING_RATE_MS = "performance_embedding_rate_ms"
        private const val DB_COMPACTION_DAYS = "performance_db_compaction_days"
        private const val REINDEX_DAYS = "performance_reindex_days"
        private const val EMBEDDING_OFFLOAD = "performance_embedding_offload"
    }

    private val prefs = run {
        val userManager = context.getSystemService(Context.USER_SERVICE) as? UserManager
        val effective = if (userManager != null && !userManager.isUserUnlocked) {
            context.createDeviceProtectedStorageContext()
        } else context
        effective.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    fun getThreadPoolSize() = prefs.getInt(THREAD_POOL_SIZE, DEFAULT_THREAD_POOL_SIZE)
        .coerceIn(MIN_THREAD_POOL_SIZE, MAX_THREAD_POOL_SIZE)

    fun setThreadPoolSize(value: Int) = prefs.edit {
        putInt(THREAD_POOL_SIZE, value.coerceIn(MIN_THREAD_POOL_SIZE, MAX_THREAD_POOL_SIZE))
    }
    fun getEmbeddingRateMs() = prefs.getLong(EMBEDDING_RATE_MS, DEFAULT_EMBEDDING_RATE_MS).coerceAtLeast(0L)
    fun setEmbeddingRateMs(value: Long) = prefs.edit { putLong(EMBEDDING_RATE_MS, value.coerceAtLeast(0L)) }
    fun getDbCompactionDays() = prefs.getInt(DB_COMPACTION_DAYS, DEFAULT_DB_COMPACTION_DAYS).coerceAtLeast(1)
    fun setDbCompactionDays(value: Int) = prefs.edit { putInt(DB_COMPACTION_DAYS, value.coerceAtLeast(1)) }
    fun getReindexDays() = prefs.getInt(REINDEX_DAYS, DEFAULT_REINDEX_DAYS).coerceAtLeast(1)
    fun setReindexDays(value: Int) = prefs.edit { putInt(REINDEX_DAYS, value.coerceAtLeast(1)) }
    fun isEmbeddingOffloadEnabled() = prefs.getBoolean(EMBEDDING_OFFLOAD, DEFAULT_EMBEDDING_OFFLOAD)
    fun setEmbeddingOffloadEnabled(value: Boolean) = prefs.edit { putBoolean(EMBEDDING_OFFLOAD, value) }
}
