package com.jeffers.notimindlite.data.local

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.jeffers.notimindlite.data.local.AppDatabase

/** Applies the persisted retention policy without deleting pinned notifications. */
class RetentionCleanupWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        return runCatching {
            val preferences = PreferencesRepository(applicationContext)
            val days = preferences.retentionDays.value
            if (days > 0 && days < PreferencesRepository.RETAIN_ALL_DAYS) {
                val cutoff = System.currentTimeMillis() - days.toLong() * 24L * 60L * 60L * 1000L
                AppDatabase.getDatabase(applicationContext).notificationDao().pruneOldLogs(cutoff)
            }
            Result.success()
        }.getOrElse { Result.retry() }
    }
}
