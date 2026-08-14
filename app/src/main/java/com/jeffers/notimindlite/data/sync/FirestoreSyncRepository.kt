package com.jeffers.notimindlite.data.sync

import com.google.firebase.firestore.FirebaseFirestore
import com.jeffers.notimindlite.data.local.AppDatabase
import com.jeffers.notimindlite.data.local.NotificationEntity
import com.jeffers.notimindlite.data.local.SyncStatus
import kotlinx.coroutines.tasks.await

class FirestoreSyncRepository(
    private val db: AppDatabase,
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

    suspend fun sync(userId: String): Result<Int> {
        return try {
            val dao = db.notificationDao()
            val userCol = firestore.collection("users").document(userId).collection("notifications")

            // 1. Upload unsynced local changes
            val unsynced = dao.getUnsyncedNotifications()
            var syncCount = 0

            for (notification in unsynced) {
                if (notification.syncStatus == SyncStatus.PENDING_DELETE) {
                    userCol.document(notification.key).delete().await()
                    dao.markPendingDelete(notification.key)
                } else {
                    val map = hashMapOf(
                        "key" to notification.key,
                        "packageName" to notification.packageName,
                        "appName" to notification.appName,
                        "title" to notification.title,
                        "content" to notification.content,
                        "postTime" to notification.postTime,
                        "lastUpdatedTime" to notification.lastUpdatedTime,
                        "updateCount" to notification.updateCount,
                        "isDismissed" to notification.isDismissed,
                        "isPersistent" to notification.isPersistent,
                        "isRead" to notification.isRead,
                        "isGroupSummary" to notification.isGroupSummary,
                        "category" to notification.category,
                        "channelId" to notification.channelId,
                        "subText" to notification.subText,
                        "bigText" to notification.bigText,
                        "inboxLinesJson" to notification.inboxLinesJson,
                        "priority" to notification.priority,
                        "groupKey" to notification.groupKey,
                        "isOngoing" to notification.isOngoing,
                        "isClearable" to notification.isClearable,
                        "actionsCount" to notification.actionsCount,
                        "dismissReason" to notification.dismissReason,
                        "dismissTime" to notification.dismissTime,
                        "intentUri" to notification.intentUri,
                        "isPinned" to notification.isPinned,
                        "actionLabels" to notification.actionLabels,
                        "smallIconRes" to notification.smallIconRes,
                        "appIconUri" to notification.appIconUri
                    )
                    userCol.document(notification.key).set(map).await()
                    dao.updateSyncStatus(notification.key, SyncStatus.SYNCED)
                }
                syncCount++
            }
            dao.purgePendingDeletes()

            // 2. Fetch remote documents and merge down
            val snapshot = userCol.get().await()
            for (doc in snapshot.documents) {
                val key = doc.getString("key") ?: doc.id
                val existing = dao.getNotificationByKey(key)
                val remotePostTime = doc.getLong("postTime") ?: System.currentTimeMillis()
                val remoteLastUpdatedTime = doc.getLong("lastUpdatedTime") ?: remotePostTime

                if (existing == null || remoteLastUpdatedTime > existing.lastUpdatedTime) {
                    val entity = NotificationEntity(
                        key = key,
                        packageName = doc.getString("packageName") ?: "",
                        appName = doc.getString("appName") ?: "",
                        title = doc.getString("title") ?: "",
                        content = doc.getString("content") ?: "",
                        postTime = remotePostTime,
                        lastUpdatedTime = remoteLastUpdatedTime,
                        updateCount = doc.getLong("updateCount")?.toInt() ?: 1,
                        isDismissed = doc.getBoolean("isDismissed") ?: false,
                        isPersistent = doc.getBoolean("isPersistent") ?: false,
                        isRead = doc.getBoolean("isRead") ?: false,
                        isGroupSummary = doc.getBoolean("isGroupSummary") ?: false,
                        category = doc.getString("category"),
                        channelId = doc.getString("channelId"),
                        subText = doc.getString("subText"),
                        bigText = doc.getString("bigText"),
                        inboxLinesJson = doc.getString("inboxLinesJson"),
                        priority = doc.getLong("priority")?.toInt() ?: 0,
                        groupKey = doc.getString("groupKey"),
                        isOngoing = doc.getBoolean("isOngoing") ?: false,
                        isClearable = doc.getBoolean("isClearable") ?: true,
                        actionsCount = doc.getLong("actionsCount")?.toInt() ?: 0,
                        dismissReason = doc.getLong("dismissReason")?.toInt(),
                        dismissTime = doc.getLong("dismissTime"),
                        intentUri = doc.getString("intentUri"),
                        isPinned = doc.getBoolean("isPinned") ?: false,
                        actionLabels = doc.getString("actionLabels"),
                        smallIconRes = doc.getLong("smallIconRes")?.toInt() ?: 0,
                        appIconUri = doc.getString("appIconUri"),
                        syncStatus = SyncStatus.SYNCED,
                        lastSyncedAt = System.currentTimeMillis()
                    )
                    dao.insertNotification(entity)
                    syncCount++
                }
            }

            Result.success(syncCount)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
