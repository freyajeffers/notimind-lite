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
    private companion object {
        const val HOURS_PER_DAY = 24L
        const val MINUTES_PER_HOUR = 60L
        const val SECONDS_PER_MINUTE = 60L
        const val MILLIS_PER_SECOND = 1_000L
    }

    override suspend fun doWork(): Result = runCatching {
        val preferences = PreferencesRepository(applicationContext)
        val days = preferences.retentionDays.value
        if (days > 0 && days < PreferencesRepository.RETAIN_ALL_DAYS) {
            val cutoff = System.currentTimeMillis() - days.toLong() * HOURS_PER_DAY *
                MINUTES_PER_HOUR * SECONDS_PER_MINUTE * MILLIS_PER_SECOND
            AppDatabase.getDatabase(applicationContext).notificationDao().pruneOldLogs(cutoff)
        }
        Result.success()
    }.getOrElse { Result.retry() }
}
