package com.jeffers.notimindlite.data.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.jeffers.notimindlite.data.auth.SupabaseAuthManager
import com.jeffers.notimindlite.data.local.AppDatabase
import com.jeffers.notimindlite.data.local.PreferencesRepository
import com.jeffers.notimindlite.domain.backup.generateBackupKey
import java.util.concurrent.TimeUnit

class SyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val db = AppDatabase.getDatabase(applicationContext)
        val preferences = PreferencesRepository(applicationContext)
        return if (preferences.syncManager.useSupabaseSync.value) {
            syncSupabase(db, preferences)
        } else {
            syncFirebase(db, preferences)
        }
    }

    private suspend fun syncSupabase(db: AppDatabase, preferences: PreferencesRepository): Result {
        val profileId = preferences.profileManager.activeProfileId.value
        val supabaseAuth = SupabaseAuthManager(applicationContext)
        val userId = supabaseAuth.currentSession().uid ?: return Result.success()
        SupabaseSettingsSyncRepository(supabaseAuth).restoreOrUpload(preferences, userId)
        val result = SupabaseSyncRepository(db, supabaseAuth, profileId).sync()
        return finishSync(preferences, result)
    }

    private suspend fun syncFirebase(db: AppDatabase, preferences: PreferencesRepository): Result {
        val profileId = preferences.profileManager.activeProfileId.value
        val user = com.google.firebase.auth.FirebaseAuth
            .getInstance()
            .currentUser
            ?: return Result.success()
        FirebaseSettingsSyncRepository().restoreOrUpload(preferences, user.uid)
        val secretKey = generateBackupKey(applicationContext)
        val result = FirestoreSyncRepository(db, profileId = profileId).sync(user.uid, secretKey)
        return finishSync(preferences, result)
    }

    private suspend fun finishSync(
        preferences: PreferencesRepository,
        result: kotlin.Result<Int>
    ): Result = if (result.isSuccess) {
        preferences.syncManager.setLastSyncTs(System.currentTimeMillis())
        Result.success()
    } else {
        Result.retry()
    }

    companion object {
        private const val SYNC_WORK_NAME = "notimind_firestore_sync"
        private const val PERIODIC_SYNC_INTERVAL_HOURS = 6L

        fun schedulePeriodicSync(context: Context) {
            val constraints = Constraints
                .Builder()
                .setRequiredNetworkType(NetworkType.UNMETERED)
                .setRequiresCharging(true)
                .setRequiresDeviceIdle(true)
                .build()

            val request = PeriodicWorkRequestBuilder<SyncWorker>(
                PERIODIC_SYNC_INTERVAL_HOURS,
                TimeUnit.HOURS
            ).setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                SYNC_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        fun runImmediateSync(context: Context): java.util.UUID {
            val request = androidx.work.OneTimeWorkRequestBuilder<SyncWorker>().build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                "notimind_manual_sync",
                androidx.work.ExistingWorkPolicy.REPLACE,
                request
            )
            return request.id
        }

        fun cancelPeriodicSync(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(SYNC_WORK_NAME)
        }
    }
}
