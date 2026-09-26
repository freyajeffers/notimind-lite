package com.jeffers.notimindlite.data.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.firebase.auth.FirebaseAuth
import com.jeffers.notimindlite.data.local.AppDatabase
import com.jeffers.notimindlite.domain.backup.generateBackupKey
import java.util.concurrent.TimeUnit

class SyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val user = FirebaseAuth.getInstance().currentUser ?: return Result.success()
        val db = AppDatabase.getDatabase(applicationContext)
        val repository = FirestoreSyncRepository(db)
        
        // Use a stable device-bound key for background sync (Android KeyStore-backed).
        val secretKey = generateBackupKey(applicationContext)

        val result = repository.sync(user.uid, secretKey)
        return if (result.isSuccess) {
            Result.success()
        } else {
            Result.retry()
        }
    }

    companion object {
        private const val SYNC_WORK_NAME = "notimind_firestore_sync"

        fun schedulePeriodicSync(context: Context) {\n            val prefs = com.jeffers.notimindlite.data.local.PreferencesRepository(context)\n            val constraints = Constraints.Builder()\n                .setRequiredNetworkType(if (prefs.syncWifiOnly.value) NetworkType.UNMETERED else NetworkType.CONNECTED)\n                .setRequiresCharging(prefs.syncChargingOnly.value)\n                .setRequiresDeviceIdle(true)\n                .build()\n\n            val intervalHours = prefs.syncIntervalMin.value / 60\n            val interval = if (intervalHours < 1) 1 else intervalHours.toLong()\n\n            val request = PeriodicWorkRequestBuilder<SyncWorker>(interval, TimeUnit.HOURS)\n                .setConstraints(constraints)\n                .build()\n\n            WorkManager.getInstance(context).enqueueUniquePeriodicWork(\n                SYNC_WORK_NAME,\n                ExistingPeriodicWorkPolicy.UPDATE,\n                request\n            )\n        }

        fun cancelPeriodicSync(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(SYNC_WORK_NAME)
        }
    }
}
