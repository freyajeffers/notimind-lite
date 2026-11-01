package com.jeffers.notimindlite.tier1feature

import android.app.Application
import android.app.NotificationManager
import android.content.Context
import com.jeffers.notimindlite.base.BaseRobolectricTest
import com.jeffers.notimindlite.data.local.NotificationEntity
import com.jeffers.notimindlite.service.BootConsolidationResult
import com.jeffers.notimindlite.service.NotificationLoggerService
import com.jeffers.notimindlite.service.NotificationRestoreCoordinator
import com.jeffers.notimindlite.service.RestoreResult
import com.jeffers.notimindlite.service.RestoredNotificationManager
import com.jeffers.notimindlite.service.RestoredNotificationState
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class NotificationRestoreCoordinatorTest : BaseRobolectricTest() {
    @Before
    fun resetRestoreState() {
        RestoredNotificationManager.clearRestoredState()
        NotificationLoggerService.clearActiveNotificationSnapshot()
        val application = context.applicationContext as Application
        Shadows.shadowOf(application).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
    }

    @After
    fun clearRestoreState() {
        RestoredNotificationManager.clearRestoredState()
        NotificationLoggerService.clearActiveNotificationSnapshot()
    }

    @Test
    fun restore_postsNotificationAndPersistsActiveRestoreState(): Unit = runBlocking {
        val key = "${context.packageName}|restore-success"
        dao.insertNotification(notification(key, isDismissed = true, dismissReason = 1))
        val item = requireNotNull(dao.getNotificationByKey(key))

        val result = NotificationRestoreCoordinator.restore(context, dao, item)

        val restored = requireNotNull(dao.getNotificationByKey(key))
        val notificationId = RestoredNotificationState.notificationId(restored.restoreMetadataJson)
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        assertEquals(RestoreResult.RESTORED, result)
        assertFalse(restored.isDismissed)
        assertEquals(RestoredNotificationState.notificationIdFor(item.id), notificationId)
        assertTrue(RestoredNotificationManager.isRestored(key))
        assertEquals(1, Shadows.shadowOf(manager).allNotifications.size)
    }

    @Test
    fun restore_queuesWhenActiveLimitIsReachedAndCancelQueuedClearsQueueState(): Unit = runBlocking {
        val activeItems = (0 until NotificationRestoreCoordinator.MAX_ACTIVE_RESTORED_NOTIFICATIONS).map { index ->
            notification(
                key = "${context.packageName}|active-$index",
                restoreMetadataJson = """{"${RestoredNotificationState.NOTIFICATION_ID_FIELD}":${1_000 + index}}"""
            )
        }
        dao.insertNotifications(activeItems)
        val key = "${context.packageName}|restore-overflow"
        dao.insertNotification(notification(key, isDismissed = true, dismissReason = 1))
        val item = requireNotNull(dao.getNotificationByKey(key))

        val result = NotificationRestoreCoordinator.restore(context, dao, item)
        val queued = requireNotNull(dao.getNotificationByKey(key))

        assertEquals(RestoreResult.QUEUED, result)
        assertTrue(RestoredNotificationState.isQueued(queued.restoreMetadataJson))
        assertEquals(1, dao.getQueuedRestoreNotifications().size)

        assertTrue(NotificationRestoreCoordinator.cancelQueued(dao, queued))
        assertNull(dao.getNotificationByKey(key)?.restoreMetadataJson)
        assertFalse(NotificationRestoreCoordinator.cancelQueued(dao, queued.copy(restoreMetadataJson = null)))
    }

    @Test
    fun dismiss_clearsEveryRowSharingRestoredNotificationId(): Unit = runBlocking {
        val notificationId = 12_345
        val metadata = """{"${RestoredNotificationState.NOTIFICATION_ID_FIELD}":$notificationId}"""
        val firstKey = "${context.packageName}|restore-group-first"
        val secondKey = "${context.packageName}|restore-group-second"
        dao.insertNotifications(
            listOf(
                notification(firstKey, restoreMetadataJson = metadata),
                notification(secondKey, restoreMetadataJson = metadata)
            )
        )
        RestoredNotificationManager.markAsRestored(firstKey, context.packageName, notificationId)
        RestoredNotificationManager.markAsRestored(secondKey, context.packageName, notificationId)

        val dismissed = NotificationRestoreCoordinator.dismiss(
            context,
            dao,
            requireNotNull(dao.getNotificationByKey(firstKey))
        )

        val rows = listOf(firstKey, secondKey).map { requireNotNull(dao.getNotificationByKey(it)) }
        assertTrue(dismissed)
        assertTrue(rows.all { it.isDismissed && it.dismissReason == 2 && it.restoreMetadataJson == null })
        assertFalse(RestoredNotificationManager.isRestored(firstKey))
        assertFalse(RestoredNotificationManager.isRestored(secondKey))
    }

    @Test
    fun restoreItems_deduplicatesCandidatesAndSkipsNonRestorableItems(): Unit = runBlocking {
        val restorableKey = "${context.packageName}|restore-batch-valid"
        val invalidKey = "${context.packageName}|restore-batch-invalid"
        dao.insertNotifications(
            listOf(
                notification(restorableKey, isDismissed = true, dismissReason = 1),
                notification(invalidKey, isDismissed = true, dismissReason = 99)
            )
        )
        val restorable = requireNotNull(dao.getNotificationByKey(restorableKey))
        val invalid = requireNotNull(dao.getNotificationByKey(invalidKey))

        val result = NotificationRestoreCoordinator.restoreItems(
            context,
            dao,
            listOf(restorable, restorable.copy(title = "Duplicate key"), invalid)
        )

        assertEquals(1, result.restoredCount)
        assertEquals(0, result.queuedCount)
        assertFalse(requireNotNull(dao.getNotificationByKey(restorableKey)).isDismissed)
        assertTrue(requireNotNull(dao.getNotificationByKey(invalidKey)).isDismissed)
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        assertEquals(1, Shadows.shadowOf(manager).allNotifications.size)
    }

    @Test
    fun restoreBootGroup_postsOneNotificationForAllOriginals(): Unit = runBlocking {
        val firstKey = "${context.packageName}|boot-group-first"
        val secondKey = "${context.packageName}|boot-group-second"
        dao.insertNotifications(listOf(notification(firstKey), notification(secondKey)))
        val originals = listOf(
            requireNotNull(dao.getNotificationByKey(firstKey)),
            requireNotNull(dao.getNotificationByKey(secondKey))
        )
        val group = BootConsolidationResult(
            summaryTitle = "Boot group",
            summaryContent = "Two restored notifications",
            appName = "NotiMind Lite",
            originalIds = originals.map { it.id }
        )

        NotificationRestoreCoordinator.restoreBootGroup(context, dao, group, originals, redactPii = false)

        val restored = originals.map { requireNotNull(dao.getNotificationByKey(it.key)) }
        val notificationIds = restored.map { RestoredNotificationState.notificationId(it.restoreMetadataJson) }.toSet()
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        assertTrue(restored.all { !it.isDismissed })
        assertEquals(1, notificationIds.size)
        assertTrue(notificationIds.single() != null)
        assertTrue(originals.all { RestoredNotificationManager.isRestored(it.key) })
        assertEquals(1, Shadows.shadowOf(manager).allNotifications.size)
    }

    @Test
    fun restoreBootGroup_queuesEveryOriginalWhenActiveLimitIsReached(): Unit = runBlocking {
        val activeItems = (0 until NotificationRestoreCoordinator.MAX_ACTIVE_RESTORED_NOTIFICATIONS).map { index ->
            notification(
                key = "${context.packageName}|boot-active-$index",
                restoreMetadataJson = """{"${RestoredNotificationState.NOTIFICATION_ID_FIELD}":${1_000 + index}}"""
            )
        }
        val firstKey = "${context.packageName}|boot-overflow-first"
        val secondKey = "${context.packageName}|boot-overflow-second"
        dao.insertNotifications(
            activeItems + listOf(
                notification(firstKey, isDismissed = true, dismissReason = 1),
                notification(secondKey, isDismissed = true, dismissReason = 1)
            )
        )
        val originals = listOf(
            requireNotNull(dao.getNotificationByKey(firstKey)),
            requireNotNull(dao.getNotificationByKey(secondKey))
        )
        val group = BootConsolidationResult(
            summaryTitle = "Overflow group",
            summaryContent = "Queued until a slot opens",
            appName = "NotiMind Lite",
            originalIds = originals.map { it.id }
        )

        NotificationRestoreCoordinator.restoreBootGroup(context, dao, group, originals, redactPii = false)

        val queued = dao.getQueuedRestoreNotifications()
        assertEquals(2, queued.size)
        assertTrue(queued.all { RestoredNotificationState.isQueued(it.restoreMetadataJson) })
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        assertTrue(Shadows.shadowOf(manager).allNotifications.isEmpty())
    }

    @Test
    fun restoreQueuedNotifications_postsNextQueuedItemWhenASlotOpens(): Unit = runBlocking {
        val activeItems = (0 until NotificationRestoreCoordinator.MAX_ACTIVE_RESTORED_NOTIFICATIONS).map { index ->
            notification(
                key = "${context.packageName}|queue-active-$index",
                restoreMetadataJson = """{"${RestoredNotificationState.NOTIFICATION_ID_FIELD}":${1_000 + index}}"""
            )
        }
        dao.insertNotifications(activeItems)
        val queuedKey = "${context.packageName}|queue-next"
        dao.insertNotification(
            notification(
                queuedKey,
                isDismissed = true,
                dismissReason = 1,
                restoreMetadataJson = RestoredNotificationState.withQueuedState(null)
            )
        )
        val activeKey = activeItems.first().key
        dao.updateRestoreMetadataJson(activeKey, null)

        NotificationRestoreCoordinator.restoreQueuedNotifications(context, dao)

        val restored = requireNotNull(dao.getNotificationByKey(queuedKey))
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        assertFalse(restored.isDismissed)
        assertFalse(RestoredNotificationState.isQueued(restored.restoreMetadataJson))
        assertTrue(RestoredNotificationState.notificationId(restored.restoreMetadataJson) != null)
        assertTrue(dao.getQueuedRestoreNotifications().isEmpty())
        assertEquals(1, Shadows.shadowOf(manager).allNotifications.size)
    }

    @Test
    fun restoreBootGroup_withNoOriginalsReturnsWithoutPosting(): Unit = runBlocking {
        val result = BootConsolidationResult(
            summaryTitle = "Unused group",
            summaryContent = "",
            appName = "Source app",
            originalIds = emptyList()
        )

        NotificationRestoreCoordinator.restoreBootGroup(context, dao, result, emptyList(), redactPii = false)

        assertTrue(dao.getAllRestoreMetadataRows().isEmpty())
    }

    private fun notification(
        key: String,
        isDismissed: Boolean = false,
        dismissReason: Int? = null,
        restoreMetadataJson: String? = null
    ) = NotificationEntity(
        key = key,
        packageName = context.packageName,
        appName = "NotiMind Lite",
        title = "Restore test",
        content = "Test notification",
        isDismissed = isDismissed,
        dismissReason = dismissReason,
        restoreMetadataJson = restoreMetadataJson
    )
}
