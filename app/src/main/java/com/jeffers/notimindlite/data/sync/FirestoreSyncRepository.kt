package com.jeffers.notimindlite.data.sync

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.jeffers.notimindlite.data.local.AppDatabase
import com.jeffers.notimindlite.data.local.NotificationEntity
import com.jeffers.notimindlite.data.local.SyncStatus
import com.jeffers.notimindlite.util.SyncEncryptionHelper
import com.jeffers.notimindlite.util.generateBackupKey
import kotlinx.coroutines.tasks.await
import javax.crypto.SecretKey

/**
 * FirestoreSyncRepository handles bidirectional sync with Firebase Firestore.
 * It implements Zero-Knowledge Sync using AES-GCM client-side encryption to protect PII.
 */
class FirestoreSyncRepository(
    private val db: AppDatabase,
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    private val TAG = "FirestoreSyncRepo"

    /**
     * Synchronizes local notifications with the cloud.
     * @param userId The authenticated user's ID.
     * @param secretKey The user's unique encryption key (managed via Keystore/User Session).
     */
    suspend fun sync(userId: String, secretKey: SecretKey): Result<Int> {
        return try {
            val dao = db.notificationDao()
            val userCol = firestore.collection("users").document(userId).collection("notifications")

            // 1. Upload unsynced local changes in batches
            val unsynced = dao.getUnsyncedNotifications()
            var syncCount = 0

            if (unsynced.isNotEmpty()) {
                val now = System.currentTimeMillis()
                for (chunk in unsynced.chunked(500)) {
                    val batch = firestore.batch()
                    var hasWrites = false
                    val syncedKeys = mutableListOf<String>()

                    for (notification in chunk) {
                        syncedKeys.add(notification.key)
                        if (notification.syncStatus != SyncStatus.PENDING_DELETE) {
                            // Encrypt PII fields before upload
                            val map = hashMapOf<String, Any?>(
                                "key" to notification.key,
                                "packageName" to notification.packageName,
                                "appName" to notification.appName,
                                "title" to SyncEncryptionHelper.encrypt(notification.title, secretKey),
                                "content" to SyncEncryptionHelper.encrypt(notification.content, secretKey),
                                "postTime" to notification.postTime,
                                "lastUpdatedTime" to notification.lastUpdatedTime,
                                "updateCount" to notification.updateCount,
                                "isDismissed" to notification.isDismissed,
                                "isPersistent" to notification.isPersistent,
                                "isRead" to notification.isRead,
                                "isGroupSummary" to notification.isGroupSummary,
                                "category" to notification.category?.let { SyncEncryptionHelper.encrypt(it, secretKey) },
                                "channelId" to notification.channelId?.let { SyncEncryptionHelper.encrypt(it, secretKey) },
                                "subText" to notification.subText?.let { SyncEncryptionHelper.encrypt(it, secretKey) },
                                "bigText" to notification.bigText?.let { SyncEncryptionHelper.encrypt(it, secretKey) },
                                "inboxLinesJson" to notification.inboxLinesJson?.let { SyncEncryptionHelper.encrypt(it, secretKey) },
                                "priority" to notification.priority,
                                "groupKey" to notification.groupKey,
                                "isOngoing" to notification.isOngoing,
                                "isClearable" to notification.isClearable,
                                "actionsCount" to notification.actionsCount,
                                "dismissReason" to notification.dismissReason,
                                "dismissTime" to notification.dismissTime,
                                "intentUri" to notification.intentUri?.let { SyncEncryptionHelper.encrypt(it, secretKey) },
                                "isPinned" to notification.isPinned,
                                "actionLabels" to notification.actionLabels?.let { SyncEncryptionHelper.encrypt(it, secretKey) },
                                "smallIconRes" to notification.smallIconRes,
                                "appIconUri" to notification.appIconUri
                            )
                            val docRef = userCol.document(notification.key)
                            batch.set(docRef, map)
                            hasWrites = true
                        }
                    }

                    if (hasWrites) {
                        batch.commit().await()
                    }

                    dao.updateSyncStatusBatch(syncedKeys, SyncStatus.SYNCED, now)
                    syncCount += chunk.size
                }
            }

            // 2. Fetch remote documents and merge down
            val snapshot = userCol.get().await()
            val entitiesToInsert = mutableListOf<NotificationEntity>()
            for (doc in snapshot.documents) {
                val key = doc.getString("key") ?: doc.id
                val existing = dao.getNotificationByKey(key)
                val remotePostTime = doc.getLong("postTime") ?: System.currentTimeMillis()
                val remoteLastUpdatedTime = doc.getLong("lastUpdatedTime") ?: remotePostTime

                if (existing == null || remoteLastUpdatedTime > existing.lastUpdatedTime) {
                    // Decrypt PII fields before inserting into local DB
                    val entity = NotificationEntity(
                        id = existing?.id ?: 0L,
                        key = key,
                        packageName = doc.getString("packageName") ?: "",
                        appName = doc.getString("appName") ?: "",
                        title = doc.getString("title")?.let { SyncEncryptionHelper.decrypt(it, secretKey) } ?: "",
                        content = doc.getString("content")?.let { SyncEncryptionHelper.decrypt(it, secretKey) } ?: "",
                        postTime = remotePostTime,
                        lastUpdatedTime = remoteLastUpdatedTime,
                        updateCount = doc.getLong("updateCount")?.toInt() ?: 1,
                        isDismissed = doc.getBoolean("isDismissed") ?: false,
                        isPersistent = doc.getBoolean("isPersistent") ?: false,
                        isRead = doc.getBoolean("isRead") ?: false,
                        isGroupSummary = doc.getBoolean("isGroupSummary") ?: false,
                        category = doc.getString("category")?.let { SyncEncryptionHelper.decrypt(it, secretKey) },
                        channelId = doc.getString("channelId")?.let { SyncEncryptionHelper.decrypt(it, secretKey) },
                        subText = doc.getString("subText")?.let { SyncEncryptionHelper.decrypt(it, secretKey) },
                        bigText = doc.getString("bigText")?.let { SyncEncryptionHelper.decrypt(it, secretKey) },
                        inboxLinesJson = doc.getString("inboxLinesJson")?.let { SyncEncryptionHelper.decrypt(it, secretKey) },
                        priority = doc.getLong("priority")?.toInt() ?: 0,
                        groupKey = doc.getString("groupKey"),
                        isOngoing = doc.getBoolean("isOngoing") ?: false,
                        isClearable = doc.getBoolean("isClearable") ?: true,
                        actionsCount = doc.getLong("actionsCount")?.toInt() ?: 0,
                        dismissReason = doc.getLong("dismissReason")?.toInt(),
                        dismissTime = doc.getLong("dismissTime"),
                        intentUri = doc.getString("intentUri")?.let { SyncEncryptionHelper.decrypt(it, secretKey) },
                        isPinned = doc.getBoolean("isPinned") ?: false,
                        actionLabels = doc.getString("actionLabels")?.let { SyncEncryptionHelper.decrypt(it, secretKey) },
                        smallIconRes = doc.getLong("smallIconRes")?.toInt() ?: 0,
                        appIconUri = doc.getString("appIconUri"),
                        syncStatus = SyncStatus.SYNCED,
                        lastSyncedAt = System.currentTimeMillis()
                    )
                    entitiesToInsert.add(entity)
                }
            }

            if (entitiesToInsert.isNotEmpty()) {
                dao.insertNotifications(entitiesToInsert)
                syncCount += entitiesToInsert.size
            }

            Result.success(syncCount)
        } catch (e: Exception) {
            Log.e(TAG, "Sync failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Data retention enforcement: Deletion of Firestore and local records is permanently prohibited.
     */
    suspend fun purgeUserData(userId: String): Result<Unit> {
        return try {
            Log.w(TAG, "Purge request rejected: Local and Firestore databases are non-deletable.")
            Result.failure(UnsupportedOperationException("Database and Firestore cloud deletion is permanently disabled."))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
