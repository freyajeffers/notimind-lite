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
            if (executor == null || configuredSize != size) {
                executor?.shutdown()
                executor = Executors.newFixedThreadPool(size)
                configuredSize = size
            }
            return executor!!
        }
    }

    fun shutdown() = synchronized(this) {
        executor?.shutdown()
        executor = null
        configuredSize = -1
    }
}
