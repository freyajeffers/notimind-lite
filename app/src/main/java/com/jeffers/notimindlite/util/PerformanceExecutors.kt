package com.jeffers.notimindlite.util

import android.content.Context
import com.jeffers.notimindlite.data.local.PreferencesRepository
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/** Shared bounded pool for embedding and maintenance work. */
object PerformanceExecutors {
    @Volatile private var configuredSize = -1

    @Volatile private var executor: ExecutorService? = null

    fun embeddingExecutor(context: Context): ExecutorService {
        val size = PreferencesRepository(context).getThreadPoolSize()
        synchronized(this) {
            val currentExecutor = executor
            if (currentExecutor != null && configuredSize == size) {
                return currentExecutor
            }
            currentExecutor?.shutdown()
            return Executors.newFixedThreadPool(size).also {
                executor = it
                configuredSize = size
            }
        }
    }

    fun shutdown() = synchronized(this) {
        executor?.shutdown()
        executor = null
        configuredSize = -1
    }
}
