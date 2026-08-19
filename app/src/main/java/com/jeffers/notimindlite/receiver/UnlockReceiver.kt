package com.jeffers.notimindlite.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.jeffers.notimindlite.data.local.AppDatabase
import com.jeffers.notimindlite.util.DatabaseMigrator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Direct Boot Aware BroadcastReceiver that triggers when the user unlocks their device
 * for the first time after boot (ACTION_USER_UNLOCKED).
 * Migrates all pre-PIN staging records from notimind_de.db to notimind_ce.db and rebuilds FTS.
 */
class UnlockReceiver : BroadcastReceiver() {

    private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context?, intent: Intent?) {
        context ?: return
        val action = intent?.action ?: return

        if (action == Intent.ACTION_USER_UNLOCKED) {
            
            val pendingResult = goAsync()

            receiverScope.launch {
                try {
                    val ceDb = AppDatabase.getCeInstance(context)
                    DatabaseMigrator.executeRawDbMergeAndRebuildFts(context, ceDb)
                } catch (e: Exception) {
                    Log.e("UnlockReceiver", "Failed during unlock migration: ${e.message}", e)
                } finally {
                    pendingResult?.finish()
                }
            }
        }
    }
}
