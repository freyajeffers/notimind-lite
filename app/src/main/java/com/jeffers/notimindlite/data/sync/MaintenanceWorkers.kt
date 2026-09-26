package com.jeffers.notimindlite.data.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.jeffers.notimindlite.data.local.AppDatabase
import com.jeffers.notimindlite.data.local.PreferencesRepository
import com.jeffers.notimindlite.util.DatabaseMigrator
import java.util.concurrent.TimeUnit

class DatabaseCompactionWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        AppDatabase.getDatabase(applicationContext).openHelper.writableDatabase.execSQL("VACUUM")
        Result.success()
    } catch (_: Exception) { Result.retry() }
}

class EmbeddingReindexWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        DatabaseMigrator.vectorizeExistingNotifications(applicationContext)
        Result.success()
    } catch (_: Exception) { Result.retry() }
}

object MaintenanceScheduler {
    private const val COMPACTION = "notimind_database_compaction"
    private const val REINDEX = "notimind_embedding_reindex"

    fun schedule(context: Context) {
        val preferences = PreferencesRepository(context)
        val work = WorkManager.getInstance(context)
        work.enqueueUniquePeriodicWork(COMPACTION, ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<DatabaseCompactionWorker>(preferences.getDbCompactionDays().toLong(), TimeUnit.DAYS).build())
        work.enqueueUniquePeriodicWork(REINDEX, ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<EmbeddingReindexWorker>(preferences.getReindexDays().toLong(), TimeUnit.DAYS).build())
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(COMPACTION)
        WorkManager.getInstance(context).cancelUniqueWork(REINDEX)
    }
}
