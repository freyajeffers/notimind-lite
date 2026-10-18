package com.jeffers.notimindlite.data.sync

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.jeffers.notimindlite.data.local.AppDatabase
import com.jeffers.notimindlite.data.local.NotificationEntity
import com.jeffers.notimindlite.data.local.SyncStatus
import com.jeffers.notimindlite.util.SyncEncryptionHelper
import kotlinx.coroutines.tasks.await
import java.security.GeneralSecurityException
import javax.crypto.SecretKey

/**
 * FirestoreSyncRepository handles bidirectional sync with Firebase Firestore.
 * It implements Zero-Knowledge Sync using AES-GCM client-side encryption to protect PII.
 */
class FirestoreSyncRepository(
    private val db: AppDatabase,
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val profileId: String = "default"
) {
    private companion object {
        const val TAG = "FirestoreSyncRepo"
        const val FIRESTORE_BATCH_LIMIT = 500
    }

    /**
     * Synchronizes local notifications with the cloud.
     * @param userId The authenticated user's ID.
     * @param secretKey The user's unique encryption key (managed via Keystore/User Session).
     */
    // Firestore limits batch writes to 500 operations per commit. SCREAMING_SNAKE_CASE is forbidden
    // for class members by detekt's VariableNaming rule; this is a class member, so camelCase.
    private val firestoreBatchSize: Int = FIRESTORE_BATCH_LIMIT


    @Suppress(
        "NestedBlockDepth"
    ) // sync() is a single-pass pipeline (upload → fetch → merge → return). try/catch at the top
    // intentionally wraps all 3 phases; nested loops (chunked batch, per-notification, per-field)
    // are required to preserve Firestore batch atomicity. Phase extraction is tracked as future work.
    suspend fun sync(userId: String, secretKey: SecretKey): Result<Int> {
        return try {
            val dao = db.notificationDao()
            val userCol = notificationCollection(userId)
            val uploaded = uploadPendingNotifications(dao, userCol, secretKey)
            val downloaded = fetchRemoteNotifications(dao, userCol, secretKey)
            Result.success(uploaded + downloaded)
        } catch (e: FirebaseFirestoreException) {
            logError("Firestore sync failed: ${e.message}", e)
            Result.failure(e)
        } catch (e: GeneralSecurityException) {
            logError("Encrypted sync failed", e)
            Result.failure(e)
        } catch (e: IllegalArgumentException) {
            logError("Invalid sync data", e)
            Result.failure(e)
        } catch (e: android.database.SQLException) {
            logError("Database sync failed", e)
            Result.failure(e)
        } catch (e: IllegalStateException) {
            logError("Invalid sync state", e)
            Result.failure(e)
        }
    }

    private fun logError(message: String, error: Throwable) {
        runCatching { Log.e(TAG, message, error) }
    }

    private fun notificationCollection(userId: String) =
        if (profileId == "default") {
            firestore.collection("users").document(userId).collection("notifications")
        } else {
            firestore.collection("users").document(userId)
                .collection("profiles").document(profileId).collection("notifications")
        }

    private suspend fun uploadPendingNotifications(
        dao: com.jeffers.notimindlite.data.local.NotificationDao,
        userCol: com.google.firebase.firestore.CollectionReference,
        secretKey: SecretKey
    ): Int {
        val unsynced = dao.getUnsyncedNotifications().distinctBy { it.key }
        val now = System.currentTimeMillis()
        var syncCount = 0
        for (chunk in unsynced.chunked(firestoreBatchSize)) {
            val batch = firestore.batch()
            val syncedKeys = chunk.map { it.key }
            chunk.forEach { notification ->
                if (notification.syncStatus != SyncStatus.PENDING_DELETE) {
                    val docRef = userCol.document(notification.key)
                    batch.set(docRef, buildEncryptedNotificationMap(notification, secretKey))
                }
            }
            batch.commit().await()
            dao.updateSyncStatusBatch(syncedKeys, SyncStatus.SYNCED, now)
            syncCount += chunk.size
        }
        return syncCount
    }

    private suspend fun fetchRemoteNotifications(
        dao: com.jeffers.notimindlite.data.local.NotificationDao,
        userCol: com.google.firebase.firestore.CollectionReference,
        secretKey: SecretKey
    ): Int {
        val entitiesToInsert = userCol.get().await().documents.mapNotNull { doc ->
            val key = doc.getString("key") ?: doc.id
            val existing = dao.getNotificationByKey(key)
            val remotePostTime = doc.getLong("postTime") ?: System.currentTimeMillis()
            val remoteLastUpdatedTime = doc.getLong("lastUpdatedTime") ?: remotePostTime
            if (existing == null || remoteLastUpdatedTime > existing.lastUpdatedTime) {
                buildNotificationEntityFromRemote(
                    doc = doc,
                    key = key,
                    existingId = existing?.id,
                    remotePostTime = remotePostTime,
                    remoteLastUpdatedTime = remoteLastUpdatedTime,
                    secretKey = secretKey
                )
            } else {
                null
            }
        }
        if (entitiesToInsert.isNotEmpty()) {
            dao.insertNotifications(entitiesToInsert)
        }
        return entitiesToInsert.size
    }

    /**
     * Builds the encrypted field map for a single notification. Extracted from [sync]
     * to keep the outer function under detekt's LongMethod / NestedBlockDepth thresholds.
     */
    @Suppress("LongMethod") // Single mapping table; splitting would scatter field-by-field logic.
    private fun buildEncryptedNotificationMap(
        notification: NotificationEntity,
        secretKey: SecretKey
    ): HashMap<String, Any?> {
        return hashMapOf<String, Any?>(
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
    }

    /**
     * Builds a [NotificationEntity] from a remote Firestore document. Extracted from
     * [sync] alongside [buildEncryptedNotificationMap] to keep the outer function
     * under detekt's LongMethod / NestedBlockDepth thresholds.
     */
    @Suppress(
        "LongMethod",
        "LongParameterList",
        "CyclomaticComplexMethod",
        "MagicNumber"
    ) // 6-arg constructor mirrors the Firestore document schema (32+ fields). MagicNumbers
    // here are schema defaults (0, 1, false, true) that match NotificationEntity column types;
    // lifting them to constants would obscure the schema mapping.
    private fun buildNotificationEntityFromRemote(
        doc: com.google.firebase.firestore.DocumentSnapshot,
        key: String,
        existingId: Long?,
        remotePostTime: Long,
        remoteLastUpdatedTime: Long,
        secretKey: SecretKey
    ): NotificationEntity {
        return NotificationEntity(
            id = existingId ?: 0L,
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
    }

    /**
     * Data retention enforcement: Deletion of Firestore and local records is permanently
     * prohibited by product policy. This method is the single client-side gatekeeper for
     * any caller that wants to remove user data.
     *
     * The policy is enforced by:
     *   1. Rejecting every call here with `Result.failure(UnsupportedOperationException)` —
     *      this catches all in-app callers that route through the repository.
     *   2. Firestore Security Rules on the `users/{uid}` and `users/{uid}/...` subcollections
     *      MUST deny `delete` operations. Client code alone cannot enforce this; the
     *      rules live in the Firebase project, not in this codebase.
     *   3. Room schema does not define any `DELETE` triggers. The only `DELETE` statements
     *      in the codebase are user-initiated "clear log" actions on the `notifications`
     *      table, which are scoped to a single user's local log and never remove the
     *      persistence store itself.
     *
     * If a future feature legitimately needs to remove data (e.g., GDPR right-to-erasure),
     * the policy here MUST be reviewed and the Firestore rules MUST be updated. Do not
     * silently relax this method.
     */
    suspend fun purgeUserData(userId: String): Result<Unit> {
        check(userId.isNotBlank()) { "purgeUserData called with blank userId" }
        Log.w(TAG, "Purge request for uid=$userId rejected: Local and Firestore databases are non-deletable by policy.")
        return Result.failure(
            UnsupportedOperationException(
                "Data retention policy: deletion is permanently disabled. " +
                    "See Firestore Security Rules and ROOM_DELETE_POLICY.md."
            )
        )
    }
}
