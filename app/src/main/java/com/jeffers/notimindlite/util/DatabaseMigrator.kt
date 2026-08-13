package com.jeffers.notimindlite.util

import android.content.Context
import android.util.Log
import androidx.sqlite.db.SupportSQLiteDatabase
import com.jeffers.notimindlite.data.local.AppDatabase

object DatabaseMigrator {
    private const val TAG = "DatabaseMigrator"

    /**
     * Executes zero-allocation cross-database merge from notimind_de.db into notimind_ce.db
     * using SQLite ATTACH DATABASE, copies staged apps and notifications, and rebuilds FTS4 index.
     */
    fun executeRawDbMergeAndRebuildFts(context: Context, ceDatabase: AppDatabase) {
        val deContext = context.createDeviceProtectedStorageContext()
        val deDbFile = deContext.getDatabasePath(AppDatabase.DE_DATABASE_NAME)
        if (!deDbFile.exists()) {
            Log.d(TAG, "No staging DE database found; skipping merge.")
            return
        }

        val deDbPath = deDbFile.absolutePath
        val db: SupportSQLiteDatabase = ceDatabase.openHelper.writableDatabase

        db.beginTransaction()
        try {
            db.execSQL("ATTACH DATABASE '$deDbPath' AS de_db;")

            // 1. Merge normalized apps table
            db.execSQL(
                """
                INSERT OR IGNORE INTO apps (packageName, appName, firstSeenTime, lastSeenTime, appIconUri, statusBarIconRes, statusBarIconPackage)
                SELECT packageName, appName, firstSeenTime, lastSeenTime, appIconUri, statusBarIconRes, statusBarIconPackage
                FROM de_db.apps;
                """.trimIndent()
            )

            // 2. Merge notifications table
            db.execSQL(
                """
                INSERT OR IGNORE INTO notifications (
                    `key`, `packageName`, `appName`, `appIconUri`, `title`, `content`,
                    `postTime`, `lastUpdatedTime`, `updateCount`, `isDismissed`, `isPersistent`,
                    `isRead`, `isGroupSummary`, `category`, `channelId`, `subText`, `bigText`,
                    `inboxLinesJson`, `priority`, `groupKey`, `isOngoing`, `isClearable`,
                    `actionsCount`, `dismissReason`, `dismissTime`, `intentUri`, `isPinned`,
                    `actionLabels`, `smallIconRes`
                )
                SELECT 
                    `key`, `packageName`, `appName`, `appIconUri`, `title`, `content`,
                    `postTime`, `lastUpdatedTime`, `updateCount`, `isDismissed`, `isPersistent`,
                    `isRead`, `isGroupSummary`, `category`, `channelId`, `subText`, `bigText`,
                    `inboxLinesJson`, `priority`, `groupKey`, `isOngoing`, `isClearable`,
                    `actionsCount`, `dismissReason`, `dismissTime`, `intentUri`, `isPinned`,
                    `actionLabels`, `smallIconRes`
                FROM de_db.notifications;
                """.trimIndent()
            )

            // 3. Rebuild full-text search index
            try {
                db.execSQL("INSERT INTO notifications_fts(notifications_fts) VALUES('rebuild');")
            } catch (e: Exception) {
                Log.e(TAG, "Failed rebuilding FTS index during merge: ${e.message}")
            }

            // 4. Clear merged records from DE database staging buffer
            try {
                db.execSQL("DELETE FROM de_db.notifications;")
                db.execSQL("DELETE FROM de_db.apps;")
            } catch (e: Exception) {
                Log.e(TAG, "Failed clearing DE staging tables: ${e.message}")
            }

            db.setTransactionSuccessful()
            Log.d(TAG, "Successfully migrated DE staging records to CE database and rebuilt FTS index.")
        } catch (e: Exception) {
            Log.e(TAG, "Error during cross-database merge: ${e.message}", e)
        } finally {
            db.endTransaction()
            try {
                db.execSQL("DETACH DATABASE de_db;")
            } catch (e: Exception) {
                Log.d(TAG, "Detach DE database cleanup: ${e.message}")
            }
        }
    }
}
