package com.jeffers.notimindlite.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationManagerCompat
import com.jeffers.notimindlite.data.local.NotificationDao
import com.jeffers.notimindlite.data.local.NotificationEntity
import com.jeffers.notimindlite.data.local.PreferencesRepository
import com.jeffers.notimindlite.util.AppLogger
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.jeffers.notimindlite.service.RestoredNotificationFactory.create as createRestoredNotification

internal data class RestoreBatchResult(
    val restoredCount: Int,
    val queuedCount: Int
)

internal enum class RestoreResult {
    RESTORED,
    QUEUED,
    SKIPPED
}

/** Restores dismissed records while keeping active restored alerts bounded and app-controlled. */
internal object NotificationRestoreCoordinator {
    const val MAX_ACTIVE_RESTORED_NOTIFICATIONS = 50
    private const val TAG = "NotificationRestore"
    private const val CHANNEL_ID = "restored_notifications_channel"
    private const val CHANNEL_NAME = "Restored Active Notifications"
    private const val CHANNEL_DESCRIPTION = "Dismiss restored notifications from NotiMind Lite only."
    private const val RESTORED_IN_APP_DISMISS_REASON = 2
    private val restoreMutex = Mutex()

    suspend fun restoreRecentlyDismissed(context: Context, dao: NotificationDao): RestoreBatchResult =
        restoreItems(context, dao, dao.getRecentlyDismissedList())

    suspend fun restoreItems(
        context: Context,
        dao: NotificationDao,
        items: List<NotificationEntity>
    ): RestoreBatchResult = restoreMutex.withLock {
        val candidates = items.distinctBy { it.key }.filter(RestoredNotificationState::canRestore)
        if (candidates.isEmpty()) return@withLock RestoreBatchResult(0, 0)
        requirePostingAllowed(context)
        drainQueuedInternal(context, dao)
        var activeCount = activeRestoreCount(dao)
        var restoredCount = 0
        var queuedCount = 0
        candidates.forEach { item ->
            if (NotificationLoggerService.isNotificationKnownActive(item.key)) {
                markSourceNotificationActiveLocked(context, dao, item)
                activeCount = activeRestoreCount(dao)
            } else if (activeCount >= MAX_ACTIVE_RESTORED_NOTIFICATIONS) {
                queue(item, dao)
                queuedCount++
            } else if (postOne(context, dao, item)) {
                restoredCount++
                activeCount++
            }
        }
        RestoreBatchResult(restoredCount, queuedCount)
    }

    suspend fun restore(context: Context, dao: NotificationDao, item: NotificationEntity): RestoreResult =
        restoreMutex.withLock {
            if (!RestoredNotificationState.canRestore(item)) return@withLock RestoreResult.SKIPPED
            if (NotificationLoggerService.isNotificationKnownActive(item.key)) {
                markSourceNotificationActiveLocked(context, dao, item)
                drainQueuedInternal(context, dao)
                return@withLock RestoreResult.SKIPPED
            }
            drainQueuedInternal(context, dao)
            if (NotificationLoggerService.isNotificationKnownActive(item.key)) {
                markSourceNotificationActiveLocked(context, dao, item)
                return@withLock RestoreResult.SKIPPED
            }
            if (activeRestoreCount(dao) >= MAX_ACTIVE_RESTORED_NOTIFICATIONS) {
                queue(item, dao)
                RestoreResult.QUEUED
            } else if (postOne(context, dao, item)) {
                RestoreResult.RESTORED
            } else {
                RestoreResult.SKIPPED
            }
        }

    suspend fun dismiss(context: Context, dao: NotificationDao, item: NotificationEntity): Boolean =
        restoreMutex.withLock {
            val notificationId = RestoredNotificationState.notificationId(item.restoreMetadataJson)
                ?: return@withLock false
            if (!cancelNotificationSafely(context, notificationId)) return@withLock false
            val associatedRows = dao
                .getAllRestoreMetadataRows()
                .filter { RestoredNotificationState.notificationId(it.restoreMetadataJson) == notificationId }
            associatedRows.forEach { row ->
                dao.markDismissedWithReason(row.key, RESTORED_IN_APP_DISMISS_REASON)
                dao.updateRestoreMetadataJson(
                    row.key,
                    RestoredNotificationState.withoutRestoreState(row.restoreMetadataJson)
                )
            }
            RestoredNotificationManager.forgetNotificationId(notificationId)
            try {
                drainQueuedInternal(context, dao)
            } catch (failure: SecurityException) {
                AppLogger.e(TAG, "Dismissed restored notification, but queued restore was denied", failure)
            } catch (failure: IllegalStateException) {
                AppLogger.e(TAG, "Dismissed restored notification, but queued restore could not continue", failure)
            } catch (failure: IllegalArgumentException) {
                AppLogger.e(TAG, "Dismissed restored notification, but queued restore was invalid", failure)
            } catch (failure: android.database.sqlite.SQLiteException) {
                AppLogger.e(TAG, "Dismissed restored notification, but queued restore hit a database error", failure)
            }
            true
        }

    suspend fun restoreQueuedNotifications(context: Context, dao: NotificationDao) = restoreMutex.withLock {
        drainQueuedInternal(context, dao)
    }

    suspend fun markSourceNotificationActive(context: Context, dao: NotificationDao, key: String) =
        restoreMutex.withLock {
            val item = dao.getNotificationByKey(key) ?: return@withLock
            markSourceNotificationActiveLocked(context, dao, item)
            drainQueuedInternal(context, dao)
        }

    private suspend fun markSourceNotificationActiveLocked(
        context: Context,
        dao: NotificationDao,
        item: NotificationEntity
    ) {
        val notificationId = RestoredNotificationState.notificationId(item.restoreMetadataJson)
        if (notificationId != null) {
            if (!cancelNotificationSafely(context, notificationId)) {
                dao.markNotificationActive(item.key)
                return
            }
            dao
                .getAllRestoreMetadataRows()
                .filter { RestoredNotificationState.notificationId(it.restoreMetadataJson) == notificationId }
                .forEach { row ->
                    dao.updateRestoreMetadataJson(
                        row.key,
                        RestoredNotificationState.withoutRestoreState(row.restoreMetadataJson)
                    )
                }
            RestoredNotificationManager.forgetNotificationId(notificationId)
        } else {
            dao.updateRestoreMetadataJson(
                item.key,
                RestoredNotificationState.withoutRestoreState(item.restoreMetadataJson)
            )
        }
        dao.markNotificationActive(item.key)
    }

    suspend fun cancelQueued(dao: NotificationDao, item: NotificationEntity): Boolean = restoreMutex.withLock {
        if (!RestoredNotificationState.isQueued(item.restoreMetadataJson)) return@withLock false
        dao.updateRestoreMetadataJson(
            item.key,
            RestoredNotificationState.withoutRestoreState(item.restoreMetadataJson)
        )
        true
    }

    /** Restores one boot-consolidated group, or persists its members in the FIFO queue. */
    suspend fun restoreBootGroup(
        context: Context,
        dao: NotificationDao,
        item: BootConsolidationResult,
        originals: List<NotificationEntity>,
        redactPii: Boolean
    ) = restoreMutex.withLock {
        if (originals.isEmpty()) return@withLock
        val safeOriginals = originals.filterNot { original ->
            if (NotificationLoggerService.isNotificationKnownActive(original.key)) {
                markSourceNotificationActiveLocked(context, dao, original)
                true
            } else {
                false
            }
        }
        if (safeOriginals.isEmpty()) return@withLock
        val safeItem = item.copy(originalIds = safeOriginals.map { it.id })
        drainQueuedInternal(context, dao)
        val activeCount = activeRestoreCount(dao)
        if (activeCount >= MAX_ACTIVE_RESTORED_NOTIFICATIONS) {
            safeOriginals.forEach { original -> queue(original, dao) }
            return@withLock
        }
        requirePostingAllowed(context)
        val notificationId = RestoredNotificationState.notificationIdFor(safeOriginals.first().id)
        val previousMetadata = safeOriginals.associate { it.key to it.restoreMetadataJson }
        try {
            safeOriginals.forEach { original ->
                dao.updateRestoreMetadataJson(
                    original.key,
                    RestoredNotificationState.withNotificationId(original.restoreMetadataJson, notificationId)
                )
            }
            val notification = createNotification(context, safeItem, safeOriginals, notificationId, redactPii)
            notificationManager(context).notify(notificationId, notification)
            safeOriginals.forEach { original ->
                dao.markNotificationActive(original.key)
                RestoredNotificationManager.markAsRestored(original.key, original.packageName, notificationId)
            }
        } catch (failure: SecurityException) {
            rollbackGroup(dao, safeOriginals, previousMetadata)
            throw failure
        } catch (failure: IllegalStateException) {
            rollbackGroup(dao, safeOriginals, previousMetadata)
            throw failure
        } catch (failure: IllegalArgumentException) {
            rollbackGroup(dao, safeOriginals, previousMetadata)
            throw failure
        } catch (failure: android.database.sqlite.SQLiteException) {
            rollbackGroup(dao, safeOriginals, previousMetadata)
            throw failure
        }
    }

    private suspend fun rollbackGroup(
        dao: NotificationDao,
        originals: List<NotificationEntity>,
        previousMetadata: Map<String, String?>
    ) {
        originals.forEach { original ->
            dao.updateRestoreMetadataJson(original.key, previousMetadata[original.key])
            dao.restoreNotificationDismissalState(
                original.key,
                original.isDismissed,
                original.dismissReason,
                original.dismissTime
            )
        }
    }

    private suspend fun drainQueuedInternal(context: Context, dao: NotificationDao) {
        var activeCount = activeRestoreCount(dao)
        if (activeCount >= MAX_ACTIVE_RESTORED_NOTIFICATIONS) return
        dao.getQueuedRestoreNotifications().forEach { item ->
            if (activeCount >= MAX_ACTIVE_RESTORED_NOTIFICATIONS) return
            if (!RestoredNotificationState.isQueued(item.restoreMetadataJson)) return@forEach
            if (NotificationLoggerService.isNotificationKnownActive(item.key)) {
                markSourceNotificationActiveLocked(context, dao, item)
                return@forEach
            }
            try {
                requirePostingAllowed(context)
                if (postOne(context, dao, item)) activeCount++
            } catch (failure: SecurityException) {
                AppLogger.e(TAG, "Could not restore queued notification ${item.key}: permission denied", failure)
            } catch (failure: IllegalStateException) {
                AppLogger.e(TAG, "Could not restore queued notification ${item.key}: illegal state", failure)
            } catch (failure: IllegalArgumentException) {
                AppLogger.e(TAG, "Could not restore queued notification ${item.key}: invalid notification", failure)
            } catch (failure: android.database.sqlite.SQLiteException) {
                AppLogger.e(TAG, "Could not restore queued notification ${item.key}: database error", failure)
            }
        }
    }

    private suspend fun postOne(context: Context, dao: NotificationDao, item: NotificationEntity): Boolean {
        if (NotificationLoggerService.isNotificationKnownActive(item.key)) {
            markSourceNotificationActiveLocked(context, dao, item)
            return false
        }
        requirePostingAllowed(context)
        val notificationId = RestoredNotificationState.notificationIdFor(item.id)
        val previousMetadata = item.restoreMetadataJson
        return try {
            dao.updateRestoreMetadataJson(
                item.key,
                RestoredNotificationState.withNotificationId(previousMetadata, notificationId)
            )
            val restoredItem = BootConsolidationResult(
                summaryTitle = item.title,
                summaryContent = item.content,
                appName = item.appName,
                originalIds = listOf(item.id)
            )
            val notification = createNotification(
                context,
                restoredItem,
                listOf(item),
                notificationId,
                currentRedaction(context)
            )
            notificationManager(context).notify(notificationId, notification)
            dao.markNotificationActive(item.key)
            RestoredNotificationManager.markAsRestored(item.key, item.packageName, notificationId)
            true
        } catch (failure: SecurityException) {
            cancelNotificationSafely(context, notificationId)
            dao.updateRestoreMetadataJson(item.key, previousMetadata)
            throw failure
        } catch (failure: IllegalStateException) {
            cancelNotificationSafely(context, notificationId)
            dao.updateRestoreMetadataJson(item.key, previousMetadata)
            throw failure
        } catch (failure: IllegalArgumentException) {
            cancelNotificationSafely(context, notificationId)
            dao.updateRestoreMetadataJson(item.key, previousMetadata)
            throw failure
        } catch (failure: android.database.sqlite.SQLiteException) {
            cancelNotificationSafely(context, notificationId)
            dao.updateRestoreMetadataJson(item.key, previousMetadata)
            throw failure
        }
    }

    private fun createNotification(
        context: Context,
        item: BootConsolidationResult,
        originals: List<NotificationEntity>,
        notificationId: Int,
        redactPii: Boolean
    ): android.app.Notification {
        ensureChannel(notificationManager(context))
        return createRestoredNotification(
            context = context,
            channelId = CHANNEL_ID,
            item = item,
            originals = originals,
            notificationId = notificationId,
            redactPii = redactPii
        )
    }

    private suspend fun activeRestoreCount(dao: NotificationDao): Int = dao
        .getAllRestoreMetadataRows()
        .mapNotNull { RestoredNotificationState.notificationId(it.restoreMetadataJson) }
        .distinct()
        .size

    private suspend fun queue(item: NotificationEntity, dao: NotificationDao) {
        dao.updateRestoreMetadataJson(item.key, RestoredNotificationState.withQueuedState(item.restoreMetadataJson))
    }

    private fun currentRedaction(context: Context): Boolean =
        PreferencesRepository(context).securityManager.redactPii.value

    private fun requirePostingAllowed(context: Context) {
        check(NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            "Notification permission is disabled for NotiMind Lite"
        }
    }

    private fun cancelNotificationSafely(context: Context, notificationId: Int): Boolean =
        try {
            notificationManager(context).cancel(notificationId)
            true
        } catch (failure: SecurityException) {
            AppLogger.e(TAG, "Could not cancel restored notification $notificationId: permission denied", failure)
            false
        } catch (failure: IllegalStateException) {
            AppLogger.e(TAG, "Could not cancel restored notification $notificationId: illegal state", failure)
            false
        } catch (failure: IllegalArgumentException) {
            AppLogger.e(TAG, "Could not cancel restored notification $notificationId: invalid notification", failure)
            false
        }

    private fun notificationManager(context: Context): NotificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: error("NotificationManager is unavailable")

    private fun ensureChannel(manager: NotificationManager) {
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = CHANNEL_DESCRIPTION
            }
        )
    }
}
