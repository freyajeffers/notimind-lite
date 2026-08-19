package com.jeffers.notimindlite.util

import android.content.Context
import android.util.Log
import androidx.sqlite.db.SupportSQLiteDatabase
import com.jeffers.notimindlite.data.local.AppDatabase
import com.jeffers.notimindlite.data.local.NotificationEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object DatabaseMigrator {
    private const val TAG = "DatabaseMigrator"

    /**
     * Vectorizes notifications that do not yet have an embedding.
     * This should be called after the database is initialized and migrated to version 17.
     */
    suspend fun vectorizeExistingNotifications(context: Context) = withContext(Dispatchers.IO) {
        val db = AppDatabase.getDatabase(context)
        val dao = db.notificationDao()
        
        try {
            val needingVectorization = dao.getNotificationsNeedingVectorization()
            if (needingVectorization.isEmpty()) return@withContext

            Log.i(TAG, "Vectorizing ${needingVectorization.size} notifications...")
            
            needingVectorization.forEach { entity ->
                val textToEmbed = buildString {
                    append(entity.appName).append(" ")
                    append(entity.title).append(" ")
                    append(entity.content).append(" ")
                    if (!entity.subText.isNullOrEmpty()) append(entity.subText).append(" ")
                    if (!entity.bigText.isNullOrEmpty()) append(entity.bigText).append(" ")
                    if (!entity.category.isNullOrEmpty()) append(entity.category).append(" ")
                    append(entity.packageName)
                }
                val embedding = VectorEmbeddingHelper.computeEmbedding(textToEmbed)
                dao.updateEmbedding(entity.id, embedding)
            }
            Log.i(TAG, "Vectorization complete.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to vectorize existing notifications: ${e.message}", e)
        }
    }

    /**
     * Executes zero-allocation cross-database merge from notimind_de.db into notimind_ce.db
     * using SQLite ATTACH DATABASE, copies staged apps and notifications, and rebuilds FTS4 index.
     */
    fun executeRawDbMergeAndRebuildFts(context: Context, ceDatabase: AppDatabase) {
        val deContext = context.createDeviceProtectedStorageContext()
        val deDbFile = deContext.getDatabasePath(AppDatabase.DE_DATABASE_NAME)
        if (!deDbFile.exists()) {
            
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
            
        } catch (e: Exception) {
            Log.e(TAG, "Error during cross-database merge: ${e.message}", e)
        } finally {
            db.endTransaction()
            try {
                db.execSQL("DETACH DATABASE de_db;")
            } catch (e: Exception) {
                
            }
        }
    }
}
